package xyz.zcraft.osu.parser.data.replay;

import xyz.zcraft.osu.parser.data.beatmap.DifficultyAttribute;
import xyz.zcraft.osu.parser.data.beatmap.OsuBeatmap;

import java.util.List;

public record ReplayAnalyze(
        OsuBeatmap beatmap,
        DifficultyAttribute calculatedDifficulty,
        OsuReplay replay,
        List<HitEvent> events,
        double unstableRate
) {
}