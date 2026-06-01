package xyz.zcraft.osu.parser;

import xyz.zcraft.osu.parser.data.replay.*;
import xyz.zcraft.osu.parser.data.beatmap.*;
import xyz.zcraft.osu.parser.exception.ParseException;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class ReplayAnalyzer {
    public static ReplayAnalyze analyze(OsuBeatmap beatmap, OsuReplay replay) throws ParseException {
        final List<OsuReplay.TimedKeyFrame> keyFrames = replay.timedKeyFrames();
        List<HitEvent> events = new ArrayList<>();

        if (keyFrames == null || keyFrames.isEmpty() || beatmap == null || beatmap.getHitObjects() == null) {
            throw new ParseException("Replay or beatmap data is incomplete");
        }

        if (!Objects.equals(replay.beatmapHash(), beatmap.getHash())) {
            throw new ParseException("Beatmap hash mismatch");
        }

        final DifficultyAttribute diff = BeatmapParser.calculateDifficulty(beatmap, replay.mods());

        final double circleRadius = diff.getCircleRadiusInPixel();

        // Not sure if this is needed
//        applyStackLeniency(beatmap.getHitObjects(), diff.cs(), diff.ar(), beatmap.getStackLeniency());

        AtomicInteger keyFrameIndex = new AtomicInteger(0);
        List<HitObject> hitObjects = beatmap.getHitObjects();

        final int n = keyFrames.size();

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
            double searchFrom = objectStart - diff.getMissWindow();
            double searchTo = objectStart + diff.getMehWindow();

            int startIdx = Math.max(0, keyFrameIndex.get());
            int candidateIdx;

            while (startIdx < n && keyFrames.get(startIdx).time() < searchFrom) {
                startIdx++;
            }
            candidateIdx = (startIdx >= n) ? n - 1 : startIdx;

            int foundFrameIndex = -1;
            int foundKeyFlags = 0;
            HitEvent.HitResult hitResult = HitEvent.HitResult.MISS;
            HitEvent.AimBias aimBias = null;

            for (int fi = candidateIdx; fi < n; fi++) {
                long frameTime = keyFrames.get(fi).time();
                if (frameTime < searchFrom) continue;
                if (frameTime > searchTo) break;

                if (consumedFrames[fi]) continue;

                OsuReplay.TimedKeyFrame frame = keyFrames.get(fi);
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
                    } else {
                        if (aimBias == null || aimBias.distance() > distance) {
                            double theta = Math.atan2(dy, dx);
                            double angleFromLast = theta;

                            if (objIndex > 0) {
                                double lastX = hitObjects.get(objIndex - 1).getX();
                                double lastY = hitObjects.get(objIndex - 1).getY();
                                angleFromLast = Math.atan2(hitObject.getY() - lastY, hitObject.getX() - lastX);
                            }

                            aimBias = new HitEvent.AimBias(theta, distance, angleFromLast);
                        }
                    }
                }
            }

            boolean wasHit = foundFrameIndex != -1;
            long frameTime = wasHit ? keyFrames.get(foundFrameIndex).time() : -1L;
            long offset = wasHit ? (frameTime - objectStart) : Long.MIN_VALUE;
            float cursorX = wasHit ? keyFrames.get(foundFrameIndex).cursorX() : Float.NaN;
            float cursorY = wasHit ? keyFrames.get(foundFrameIndex).cursorY() : Float.NaN;

            if (wasHit) {
                keyFrameIndex.set(foundFrameIndex);

                if (Math.abs(offset) <= diff.getPerfectWindow()) {
                    hitResult = HitEvent.HitResult.PERFECT;
                } else if (Math.abs(offset) <= diff.getOkWindow()) {
                    hitResult = HitEvent.HitResult.OK;
                } else if (Math.abs(offset) <= diff.getMehWindow()) {
                    hitResult = HitEvent.HitResult.MEH;
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

        return new ReplayAnalyze(beatmap, diff, replay, events, ur);
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