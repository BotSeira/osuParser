package xyz.zcraft.osu.parser;

import org.jetbrains.annotations.Nullable;
import xyz.zcraft.osu.parser.data.TimeRange;
import xyz.zcraft.osu.parser.data.beatmap.HitObject;
import xyz.zcraft.osu.parser.data.beatmap.OsuBeatmap;

import java.util.List;

public class BeatmapAnalyzer {
    @Nullable
    public static TimeRange calculateDifficultyPeak(OsuBeatmap osuBeatmap) {
        List<Long> timestamps = extractTimestamps(osuBeatmap);
        if (timestamps.isEmpty()) return null;

        final double bufferedStart = getBufferedStart(osuBeatmap, timestamps);
        double finalEnd = bufferedStart + 11.5;

        return new TimeRange(bufferedStart, finalEnd);
    }

    private static double getBufferedStart(OsuBeatmap osuBeatmap, List<Long> timestamps) {
        double maxStarRating = 0;
        int bestStartIndex = 0;
        long windowDurationMs = 10000;

        for (int i = 0; i < timestamps.size(); i++) {
            long windowStartTime = timestamps.get(i);
            long windowEndTime = windowStartTime + windowDurationMs;

            if (windowEndTime > timestamps.getLast()) {
                break;
            }

            try {
                double windowSR = calculateWindowDifficulty(osuBeatmap, windowStartTime, windowEndTime);

                if (windowSR > maxStarRating) {
                    maxStarRating = windowSR;
                    bestStartIndex = i;
                }
            } catch (Exception e) {
                System.err.println("Failed to calculate window SR at " + windowStartTime + "ms: " + e.getMessage());
            }
        }

        double highlightStart = timestamps.get(bestStartIndex) / 1000.0;
        return Math.max(0, highlightStart - 1.5);
    }

    public static double calculateWindowDifficulty(OsuBeatmap osuBeatmap, long startTimeMs, long endTimeMs) {
        String slicedOsuString = osuBeatmap.toWindowedBeatmapString(startTimeMs, endTimeMs);

        try (
                desu.life.RosuFFI.Beatmap beatmap = new desu.life.RosuFFI.Beatmap(slicedOsuString.getBytes());
                desu.life.RosuFFI.Difficulty diff = new desu.life.RosuFFI.Difficulty()
        ) {
            return diff.calculate(beatmap).osu.t.stars;
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate window difficulty", e);
        }
    }

    public static List<Long> extractTimestamps(OsuBeatmap beatmap) {
        return beatmap.getHitObjects().stream().map(HitObject::getTime).toList();
    }
}