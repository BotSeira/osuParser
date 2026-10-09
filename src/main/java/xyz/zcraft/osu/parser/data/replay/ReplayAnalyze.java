package xyz.zcraft.osu.parser.data.replay;

import xyz.zcraft.osu.parser.data.beatmap.DifficultyAttribute;
import xyz.zcraft.osu.parser.data.beatmap.OsuBeatmap;

import java.util.List;

public record ReplayAnalyze(
        OsuBeatmap beatmap,
        DifficultyAttribute calculatedDifficulty,
        OsuReplay replay,
        List<HitEvent> events,
        double unstableRate,
        double aimUnstableRate
) {
    public List<HitEvent> misses() {
        return events.stream().filter(HitEvent::isAnalysisMiss)
                .sorted(java.util.Comparator.comparingLong(HitEvent::analysisTime)).toList();
    }
}
