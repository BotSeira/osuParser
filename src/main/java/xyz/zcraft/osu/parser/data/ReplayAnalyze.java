package xyz.zcraft.osu.parser.data;

import java.util.List;

public record ReplayAnalyze(
        OsuBeatmap beatmap,
        OsuReplay replay,
        List<HitEvent> events,
        double unstableRate
) {
}