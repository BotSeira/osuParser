package xyz.zcraft.osu.parser.data;

import java.util.List;

public record OsuReplay(
        byte gameMode,
        int gameVersion,
        String beatmapHash,
        String playerName,
        String replayHash,
        short count300,
        short count100,
        short count50,
        short countGeki,
        short countKatu,
        short countMiss,
        int totalScore,
        short maxCombo,
        boolean perfectCombo,
        int mods,
        String lifeBarGraph,
        long timestamp,
        @Deprecated
        List<KeyFrame> keyFrames,
        List<TimedKeyFrame> timedKeyFrames
) {
    public record KeyFrame(
            long offset,
            float cursorX,
            float cursorY,
            int key
    ) {
    }

    public record TimedKeyFrame(
            long time,
            long offset,
            float cursorX,
            float cursorY,
            int key
    ){}
}
