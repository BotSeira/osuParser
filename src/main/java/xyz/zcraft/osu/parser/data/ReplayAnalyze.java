package xyz.zcraft.osu.parser.data;

import java.util.List;

public record ReplayAnalyze(
        List<HitEvent> events,
        double unstableRate
) {
}