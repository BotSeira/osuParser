package xyz.zcraft.osu.parser.data.replay;

import xyz.zcraft.osu.parser.data.beatmap.HitObject;

public record HitEvent(
        int objectIndex,
        HitObject hitObject,
        EventType eventType,
        long eventTime,
        boolean wasHit,
        HitResult hitResult,
        AimBias aimBias,
        long hitTime,
        long hitTimeOffset,
        float cursorX,
        float cursorY,
        int keyFlags,
        int frameIndex) {
    /**
     * Retains source compatibility for callers which create events for whole hit objects.
     */
    public HitEvent(int objectIndex, HitObject hitObject, boolean wasHit, HitResult hitResult,
                    AimBias aimBias, long hitTime, long hitTimeOffset, float cursorX,
                    float cursorY, int keyFlags, int frameIndex) {
        this(objectIndex, hitObject, eventTypeFor(hitObject), hitObject.getTime(), wasHit,
                hitResult, aimBias, hitTime, hitTimeOffset, cursorX, cursorY, keyFlags, frameIndex);
    }

    private static EventType eventTypeFor(HitObject hitObject) {
        return switch (hitObject.getObjectType()) {
            case HIT_CIRCLE -> EventType.HIT_CIRCLE;
            case SLIDER -> EventType.SLIDER_HEAD;
            case SPINNER -> EventType.SPINNER;
        };
    }

    public boolean isObjectStart() {
        return eventType == EventType.HIT_CIRCLE
                || eventType == EventType.SLIDER_HEAD
                || eventType == EventType.SPINNER;
    }

    public enum EventType {
        HIT_CIRCLE, SLIDER_HEAD, SLIDER_TICK, SLIDER_END,
        SPINNER, SPINNER_SPIN, SPINNER_BONUS
    }

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
