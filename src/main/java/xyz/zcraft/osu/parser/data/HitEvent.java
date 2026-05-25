package xyz.zcraft.osu.parser.data;

public record HitEvent(
        int objectIndex,
        HitObject hitObject,
        boolean wasHit,
        HitResult hitResult,
        AimBias aimBias,
        long hitTime,
        long hitTimeOffset,
        float cursorX,
        float cursorY,
        int keyFlags,
        int frameIndex) {
    public record AimBias(
            double theta,
            double distance,
            double angleFromLast
    ) {
    }

    public enum HitResult {
        PERFECT, OK, MEH, MISS
    }
}
