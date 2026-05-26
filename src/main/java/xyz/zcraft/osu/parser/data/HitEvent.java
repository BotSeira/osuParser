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
    public enum HitResult {
        PERFECT, OK, MEH, MISS
    }

    public record AimBias(
            double theta,
            double distance,
            double angleFromLast
    ) {
        public AimBias standardize() {
            double rawTheta = theta - angleFromLast;

            double standardizedTheta = -rawTheta;

            while (standardizedTheta >= 2 * Math.PI) {
                standardizedTheta -= 2 * Math.PI;
            }
            while (standardizedTheta < 0) {
                standardizedTheta += 2 * Math.PI;
            }

            return new AimBias(standardizedTheta, distance, 0.0);
        }
    }
}
