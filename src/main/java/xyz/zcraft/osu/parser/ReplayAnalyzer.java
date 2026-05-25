package xyz.zcraft.osu.parser;

import xyz.zcraft.osu.parser.data.*;
import xyz.zcraft.osu.parser.exception.ParseException;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class ReplayAnalyzer {
    public static ReplayAnalyze analyze(OsuBeatmap beatmap, OsuReplay replay) throws ParseException {
        final List<OsuReplay.KeyFrame> keyFrames = replay.keyFrames();
        List<HitEvent> events = new ArrayList<>();

        if (keyFrames == null || keyFrames.isEmpty() || beatmap == null || beatmap.getHitObjects() == null) {
            throw new ParseException("Replay or beatmap data is incomplete");
        }

        if (!Objects.equals(replay.beatmapHash(), beatmap.getHash())) {
            throw new ParseException("Beatmap hash mismatch");
        }

        boolean hasEZ = (replay.mods() & 2) > 0;
        boolean hasHR = (replay.mods() & 16) > 0;
        boolean hasDT = (replay.mods() & 64) > 0;
        boolean hasHT = (replay.mods() & 256) > 0;
        boolean hasNC = (replay.mods() & 512) > 0;

        double cs = beatmap.getCs();
        double od = beatmap.getOd();
        double ar = beatmap.getAr();

        if (hasHR) {
            cs = Math.min(10.0, cs * 1.3);
            od = Math.min(10.0, od * 1.4);
            ar = Math.min(10.0, ar * 1.4);
        } else if (hasEZ) {
            cs = cs * 0.5;
            od = od * 0.5;
            ar = ar * 0.5;
        }

        final double circleRadius = 54.4 - 4.48 * cs;

        double hitWindow = 200 - 10 * od; // 50 (MEH)
        double hitWindowOk = 140 - 8 * od; // 100 (OK)
        double hitWindowPf = 80 - 6 * od; // 300 (PERFECT)

        double clockRate = 1.0;
        if (hasDT || hasNC) {
            clockRate = 1.5;
        } else if (hasHT) {
            clockRate = 0.75;
        }

        hitWindow /= clockRate;
        hitWindowOk /= clockRate;
        hitWindowPf /= clockRate;

        applyStackLeniency(beatmap.getHitObjects(), cs, ar, beatmap.getStackLeniency());

        final int n = keyFrames.size();
        long[] cumulative = new long[n];
        long t = 0L;
        for (int i = 0; i < n; i++) {
            final long offset = keyFrames.get(i).offset();
            t += offset;
            cumulative[i] = t;
        }

        AtomicInteger keyFrameIndex = new AtomicInteger(0);
        List<HitObject> hitObjects = beatmap.getHitObjects();

        boolean[] consumedFrames = new boolean[n];

        for (int objIndex = 0; objIndex < hitObjects.size(); objIndex++) {
            HitObject hitObject = hitObjects.get(objIndex);

            if (hitObject.getObjectType() != HitObject.ObjectType.HIT_CIRCLE
                    && hitObject.getObjectType() != HitObject.ObjectType.SLIDER
                    && hitObject.getObjectType() != HitObject.ObjectType.SPINNER) {
                continue;
            }

            if (hitObject.getObjectType() == HitObject.ObjectType.SPINNER) {
                HitEvent event = new HitEvent(objIndex, hitObject, true, HitEvent.HitResult.PERFECT, null,
                        hitObject.getTime(), 0L, Float.NaN, Float.NaN, 0, Math.max(0, keyFrameIndex.get()));
                events.add(event);
                continue;
            }

            long objectStart = hitObject.getTime();
            double searchFrom = objectStart - hitWindow;
            double searchTo = objectStart + hitWindow;

            int startIdx = Math.max(0, keyFrameIndex.get());
            int candidateIdx;

            while (startIdx < n && cumulative[startIdx] < searchFrom) {
                startIdx++;
            }
            candidateIdx = (startIdx >= n) ? n - 1 : startIdx;

            int foundFrameIndex = -1;
            int foundKeyFlags = 0;
            HitEvent.HitResult hitResult = HitEvent.HitResult.MISS;
            HitEvent.AimBias aimBias = null;

            for (int fi = candidateIdx; fi < n; fi++) {
                long frameTime = cumulative[fi];
                if (frameTime < searchFrom) continue;
                if (frameTime > searchTo) break;

                if (consumedFrames[fi]) continue;

                OsuReplay.KeyFrame frame = keyFrames.get(fi);
                int currentFlags = frame.key();

                int previousFlags = fi > 0 ? keyFrames.get(fi - 1).key() : 0;

                int newlyPressed = currentFlags & ~previousFlags;
                boolean isNewPress = (newlyPressed & 15) > 0;

                if (isNewPress) {
                    double cursorX = frame.cursorX();
                    double cursorY = frame.cursorY();
                    double dx = cursorX - hitObject.getX();
                    double dy = cursorY - hitObject.getY();
                    double distance = Math.hypot(dx, dy);

                    if (distance <= circleRadius) {
                        double theta = Math.atan2(dy, dx);
                        double angleFromLast = theta;

                        if (objIndex > 0) {
                            double lastX = hitObjects.get(objIndex - 1).getX();
                            double lastY = hitObjects.get(objIndex - 1).getY();
                            angleFromLast = Math.atan2(hitObject.getY() - lastY, hitObject.getX() - lastX);
                        }

                        aimBias = new HitEvent.AimBias(theta, distance, angleFromLast);
                        foundFrameIndex = fi;
                        foundKeyFlags = currentFlags;

                        consumedFrames[fi] = true;
                        break;
                    }
                }
            }

            boolean wasHit = foundFrameIndex != -1;
            long frameTime = wasHit ? cumulative[foundFrameIndex] : -1L;
            long offset = wasHit ? (frameTime - objectStart) : Long.MIN_VALUE;
            float cursorX = wasHit ? keyFrames.get(foundFrameIndex).cursorX() : Float.NaN;
            float cursorY = wasHit ? keyFrames.get(foundFrameIndex).cursorY() : Float.NaN;

            if (wasHit) {
                keyFrameIndex.set(foundFrameIndex);

                if (Math.abs(offset) <= hitWindowPf) {
                    hitResult = HitEvent.HitResult.PERFECT;
                } else if (Math.abs(offset) <= hitWindowOk) {
                    hitResult = HitEvent.HitResult.OK;
                } else if (Math.abs(offset) <= hitWindow) {
                    hitResult = HitEvent.HitResult.MEH;
                } else {
                    throw new IllegalStateException("Hit detected outside of hit windows");
                }
            } else {
                keyFrameIndex.set(candidateIdx);
            }

            HitEvent event = new HitEvent(objIndex, hitObject, wasHit, hitResult, aimBias,
                    frameTime, wasHit ? offset : Long.MIN_VALUE,
                    cursorX, cursorY, foundKeyFlags,
                    wasHit ? foundFrameIndex : candidateIdx);
            events.add(event);
        }

        final double ur = calculateUR(events);

        return new ReplayAnalyze(events, ur);
    }

    public static double calculateUR(List<HitEvent> events) {
        List<Long> validOffsets = new LinkedList<>();

        for (HitEvent event : events) {
            if (event.wasHit() && !event.hitObject().getObjectType().equals(HitObject.ObjectType.SPINNER)) {
                validOffsets.add(event.hitTimeOffset());
            }
        }

        if (validOffsets.isEmpty()) {
            return 0.0;
        }

        double sum = 0;
        for (long offset : validOffsets) {
            sum += offset;
        }
        double mean = sum / validOffsets.size();

        double squaredDiffSum = 0;
        for (long offset : validOffsets) {
            double diff = offset - mean;
            squaredDiffSum += (diff * diff);
        }
        double variance = squaredDiffSum / validOffsets.size();

        double standardDeviation = Math.sqrt(variance);

        return standardDeviation * 10.0;
    }

    private static void applyStackLeniency(List<HitObject> hitObjects, double cs, double ar, double stackLeniencyMultiplier) {
        if (hitObjects == null || hitObjects.isEmpty() || stackLeniencyMultiplier <= 0) return;

        double timePreempt;
        if (ar < 5.0) {
            timePreempt = 1200.0 + 600.0 * (5.0 - ar) / 5.0;
        } else if (ar > 5.0) {
            timePreempt = 1200.0 - 750.0 * (ar - 5.0) / 5.0;
        } else {
            timePreempt = 1200.0;
        }

        double stackLeniencyTime = timePreempt * stackLeniencyMultiplier;

        double scale = (1.0 - 0.7 * (cs - 5.0) / 5.0) / 2.0;
        double stackOffset = scale * 6.4;

        int[] stackHeights = new int[hitObjects.size()];

        for (int i = hitObjects.size() - 1; i >= 0; i--) {
            HitObject objI = hitObjects.get(i);

            for (int j = i + 1; j < hitObjects.size(); j++) {
                HitObject objJ = hitObjects.get(j);

                if (objJ.getTime() - objI.getTime() > stackLeniencyTime) break;

                if (Math.abs(objI.getX() - objJ.getX()) < 2 && Math.abs(objI.getY() - objJ.getY()) < 2) {
                    stackHeights[i] = stackHeights[j] + 1;
                    break;
                }
            }
        }

        for (int i = 0; i < hitObjects.size(); i++) {
            if (stackHeights[i] > 0) {
                HitObject obj = hitObjects.get(i);
                int shift = (int) (stackHeights[i] * stackOffset);

                obj.setX(obj.getX() - shift);
                obj.setY(obj.getY() - shift);
            }
        }
    }
}