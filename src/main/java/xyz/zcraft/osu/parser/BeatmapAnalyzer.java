package xyz.zcraft.osu.parser;

import desu.life.RosuFFI;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import xyz.zcraft.osu.parser.data.beatmap.DifficultyAttribute;
import xyz.zcraft.osu.parser.data.beatmap.HitObject;
import xyz.zcraft.osu.parser.data.beatmap.OsuBeatmap;
import xyz.zcraft.osu.parser.data.beatmap.WindowDifficulty;
import xyz.zcraft.osu.parser.exception.AnalyzeException;

import java.time.Duration;
import java.util.*;

public class BeatmapAnalyzer {
    public static List<WindowDifficulty> getWindowDifficulties(OsuBeatmap osuBeatmap, Duration window) throws AnalyzeException {
        List<Long> timestamps = extractTimestamps(osuBeatmap);
        if (timestamps.isEmpty()) {
            throw new AnalyzeException("No hit objects found in the beatmap.");
        }

        double maxPp = 0;
        long windowDurationMs = window.toMillis();
        List<WindowDifficulty> difficulties = new ArrayList<>((int) (timestamps.getLast() / windowDurationMs + 1));

        for (int i = 0; i < timestamps.size(); i += 5) {
            long start = timestamps.get(i);
            long end = start + windowDurationMs;

            if (end > timestamps.getLast()) {
                break;
            }

            try {
                var windowDiff = calculateWindowDifficulty(osuBeatmap, start, end);
                final Double windowStar = windowDiff.getKey();
                final Double windowPp = windowDiff.getValue();

                maxPp = Math.max(maxPp, windowPp);

                difficulties.add(new WindowDifficulty(start, end, windowStar, windowPp));
            } catch (Exception e) {
                throw new AnalyzeException("Failed to calculate window difficulty around " + start, e);
            }
        }

        return difficulties;
    }

    /**
     * @return Pair of window's star rating and PP
     */
    public static Pair<Double, Double> calculateWindowDifficulty(OsuBeatmap osuBeatmap, long startTimeMs, long endTimeMs) {
        try {
            final String str = osuBeatmap.toWindowedBeatmapString(startTimeMs, endTimeMs);
            try (
                    desu.life.RosuFFI.Beatmap beatmap = new desu.life.RosuFFI.Beatmap(str.getBytes());
                    desu.life.RosuFFI.Difficulty diff = new desu.life.RosuFFI.Difficulty();
                    desu.life.RosuFFI.Performance performance = new RosuFFI.Performance()
            ) {
                final double stars = diff.calculate(beatmap).asOsu().stars;
                final double pp = performance.calculate(beatmap).asOsu().pp;

                return new ImmutablePair<>(stars, pp);
            } catch (RosuFFI.FFIException e) {
                throw new RuntimeException(e);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate window difficulty", e);
        }
    }

    private static List<Long> extractTimestamps(OsuBeatmap beatmap) {
        return beatmap.getHitObjects().stream().map(HitObject::getTime).toList();
    }

    public static @NotNull DifficultyAttribute calculateDifficulty(OsuBeatmap beatmap, long mods) {
        boolean hasEZ = (mods & 2) > 0;
        boolean hasHR = (mods & 16) > 0;
        boolean hasDT = (mods & 64) > 0;
        boolean hasHT = (mods & 256) > 0;
        boolean hasNC = (mods & 512) > 0;

        double cs = beatmap.getCs();
        double od = beatmap.getOd();
        double ar = beatmap.getAr();
        double hp = beatmap.getHp();

        double approachTime = ar >= 5 ? (1200 - 150 * (ar - 5)) : (1800 - 120 * ar);

        if (hasHR) {
            cs = Math.min(10.0, cs * 1.3);
            od = Math.min(10.0, od * 1.4);
            hp = Math.min(10.0, hp * 1.4);
        } else if (hasEZ) {
            cs = cs * 0.5;
            od = od * 0.5;
            hp = hp * 0.5;
        }

        double clockRate = 1.0;
        if (hasDT || hasNC) {
            clockRate = 1.5;
        } else if (hasHT) {
            clockRate = 0.75;
        }

        approachTime = approachTime / clockRate;

        if (approachTime > 1200) {
            ar = (1800 - approachTime) / 120;
        } else {
            ar = 5 + (1200 - approachTime) / 150;
        }

        double window = (80.0 - (6.0 * od)) / clockRate;
        od = (80.0 - window) / 6;

        return new DifficultyAttribute(cs, od, ar, hp, beatmap.getOd(), clockRate);
    }

    public static double calculateBpm(OsuBeatmap beatmap) {
        final List<OsuBeatmap.TimingPoint> timingPoints = beatmap.getTimingPoints()
                .stream()
                .filter(tp -> tp.uninherited() == 1)
                .toList();

        if (timingPoints.isEmpty()) return 0.0;

        Map<Double, Long> bpmDurations = new HashMap<>();

        double previousBpm = 0;
        long previousBpmStartTime = 0;

        for (OsuBeatmap.TimingPoint tp : timingPoints) {
            double currentBpm = Math.round((60000.0 / tp.beatLength()) * 100.0) / 100.0;

            if (previousBpm > 0) {
                long duration = tp.time() - previousBpmStartTime;
                bpmDurations.put(previousBpm, bpmDurations.getOrDefault(previousBpm, 0L) + duration);
            }

            previousBpm = currentBpm;
            previousBpmStartTime = tp.time();
        }

        if (previousBpm > 0 && !beatmap.getHitObjects().isEmpty()) {
            long finalObjectTime = beatmap.getHitObjects().getLast().getTime();
            long finalDuration = Math.max(0, finalObjectTime - previousBpmStartTime);
            bpmDurations.put(previousBpm, bpmDurations.getOrDefault(previousBpm, 0L) + finalDuration);
        }

        return bpmDurations.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(0.0);
    }

    /**
     * @return Pair of Total Length and Hit Length
     */
    public static Pair<Integer, Integer> calculateLengths(OsuBeatmap beatmap) {
        List<HitObject> hitObjects = beatmap.getHitObjects();
        if (hitObjects == null || hitObjects.isEmpty()) {
            return new ImmutablePair<>(0, 0);
        }

        long firstObjectTime = hitObjects.getFirst().getTime();
        long baseTotalLengthMs = hitObjects.getLast().getTime();

        long totalBreakTimeMs = 0;
        var events = beatmap.getBreakEvents();

        for (var line : events) {
            long breakStart = line.getStartTime();
            long breakEnd = line.getEndTime();

            if (breakEnd > breakStart) {
                totalBreakTimeMs += (breakEnd - breakStart);
            }
        }

        long baseHitLengthMs = (baseTotalLengthMs - firstObjectTime) - totalBreakTimeMs;

        baseHitLengthMs = Math.max(0, baseHitLengthMs);

        int totalLengthSeconds = (int) Math.round((baseTotalLengthMs / 1000.0));
        int hitLengthSeconds = (int) Math.round((baseHitLengthMs / 1000.0));

        return new ImmutablePair<>(totalLengthSeconds, hitLengthSeconds);
    }
}