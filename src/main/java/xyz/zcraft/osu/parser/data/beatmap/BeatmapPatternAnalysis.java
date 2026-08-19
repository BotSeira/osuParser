package xyz.zcraft.osu.parser.data.beatmap;

import java.util.List;

/**
 * Placement-based osu!standard pattern profile. Percentages in each list are normalized
 * independently and describe relative prevalence, not difficulty or performance values.
 */
public record BeatmapPatternAnalysis(
        List<PatternScore> types,
        List<AimPatternScore> aimTypes,
        PatternMetrics metrics
) {
    public BeatmapPatternAnalysis {
        types = List.copyOf(types);
        aimTypes = List.copyOf(aimTypes);
    }

    public PatternScore primaryType() {
        return types.getFirst();
    }

    public AimPatternScore primaryAimType() {
        return aimTypes.getFirst();
    }

    public enum PatternType {
        STREAM,
        ALT,
        AIM,
        FLOW,
        TECH,
        READING
    }

    public enum AimPatternType {
        SNAP_AIM,
        JUMP_AIM,
        CROSS_SCREEN_JUMP_AIM,
        AWKWARD_AIM,
        FLOW_AIM,
        LINEAR_JUMP_AIM,
        WIDE_ANGLE_JUMP_AIM,
        SHARP_ANGLE_JUMP_AIM,
        BACK_AND_FORTH_AIM
    }

    public record PatternScore(PatternType type, double percentage, double evidence) {
    }

    public record AimPatternScore(AimPatternType type, double percentage, double evidence) {
    }

    public record PatternMetrics(
            int objectCount,
            int circleCount,
            int sliderCount,
            double averageSpacing,
            double overlapRatio,
            double rhythmComplexity
    ) {
    }
}
