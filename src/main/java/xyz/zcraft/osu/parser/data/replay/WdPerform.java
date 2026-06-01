package xyz.zcraft.osu.parser.data.replay;

public record WdPerform(
        long startTime,
        long endTime,
        double beatmapPp,
        double accuracy,
        double wdScore
) {
}
