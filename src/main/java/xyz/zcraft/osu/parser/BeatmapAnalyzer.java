package xyz.zcraft.osu.parser;

import desu.life.RosuFFI;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import xyz.zcraft.osu.parser.data.beatmap.WindowDifficulty;
import xyz.zcraft.osu.parser.data.beatmap.HitObject;
import xyz.zcraft.osu.parser.data.beatmap.OsuBeatmap;
import xyz.zcraft.osu.parser.exception.AnalyzeException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BeatmapAnalyzer {
    public static WindowDifficulty getDifficultyPeak(OsuBeatmap osuBeatmap) {
        final var difficulty = getWindowDifficulties(osuBeatmap);

        return difficulty.stream().sorted(Comparator.comparing(WindowDifficulty::starRating)).toList().reversed().getFirst();
    }

    public static List<WindowDifficulty> getWindowDifficulties(OsuBeatmap osuBeatmap) {
        List<Long> timestamps = extractTimestamps(osuBeatmap);
        if (timestamps.isEmpty()) {
            throw new AnalyzeException("No hit objects found in the beatmap.");
        }

        double maxStarRating = 0;
        long windowDurationMs = 20 * 1000L;
        List<WindowDifficulty> difficulties = new ArrayList<>((int) (timestamps.getLast() / windowDurationMs + 1));

        for (int i = 0; i < timestamps.size(); i++) {
            long windowStartTime = timestamps.get(i);
            long windowEndTime = windowStartTime + windowDurationMs;

            if (windowEndTime > timestamps.getLast()) {
                break;
            }

            try {
                var windowSR = calculateWindowDifficulty(osuBeatmap, windowStartTime, windowEndTime);

                if (windowSR.getKey() > maxStarRating) {
                    maxStarRating = windowSR.getKey();
                }

                difficulties.add(new WindowDifficulty(windowStartTime, windowEndTime, windowSR.getKey(), windowSR.getValue()));
            } catch (Exception e) {
                throw new AnalyzeException("Failed to calculate window difficulty around " + windowStartTime, e);
            }
        }

        return difficulties;
    }

    public static Pair<Double, Double> calculateWindowDifficulty(OsuBeatmap osuBeatmap, long startTimeMs, long endTimeMs) {
        String slicedOsuString = osuBeatmap.toWindowedBeatmapString(startTimeMs, endTimeMs);

        try (
                desu.life.RosuFFI.Beatmap beatmap = new desu.life.RosuFFI.Beatmap(slicedOsuString.getBytes());
                desu.life.RosuFFI.Difficulty diff = new desu.life.RosuFFI.Difficulty();
                desu.life.RosuFFI.Performance performance = new RosuFFI.Performance()
        ) {
            final double stars = diff.calculate(beatmap).osu.t.stars;
            final double pp = performance.calculate(beatmap).osu.t.pp;
            return new ImmutablePair<>(stars, pp);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate window difficulty", e);
        }
    }

    public static List<Long> extractTimestamps(OsuBeatmap beatmap) {
        return beatmap.getHitObjects().stream().map(HitObject::getTime).toList();
    }
}