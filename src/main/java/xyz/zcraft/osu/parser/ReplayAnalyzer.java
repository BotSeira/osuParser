package xyz.zcraft.osu.parser;

import xyz.zcraft.osu.parser.data.replay.*;
import xyz.zcraft.osu.parser.data.beatmap.*;
import xyz.zcraft.osu.parser.exception.ParseException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class ReplayAnalyzer {
    private static final int AUTO_MOD = 2048;
    private static final int SPUN_OUT_MOD = 4096;
    private static final double AUTO_SPINNER_RPM = 477.26;
    private static final double SPUN_OUT_SPINNER_RPM = 286.48;
    private static final double TWO_PI = Math.PI * 2;
    private static final int SPINNER_BONUS_GAP = 2;

    public static ReplayAnalyze analyze(OsuBeatmap beatmap, OsuReplay replay) throws ParseException {
        final List<OsuReplay.TimedKeyFrame> keyFrames = replay.timedKeyFrames();
        List<HitEvent> events = new ArrayList<>();

        if (keyFrames == null || keyFrames.isEmpty() || beatmap == null || beatmap.getHitObjects() == null) {
            throw new ParseException("Replay or beatmap data is incomplete");
        }

        if (!Objects.equals(replay.beatmapHash(), beatmap.getHash())) {
            throw new ParseException("Beatmap hash mismatch");
        }

        final DifficultyAttribute diff = BeatmapAnalyzer.calculateDifficulty(beatmap, replay.mods());

        final double circleRadius = diff.getCircleRadiusInPixel();
        int[] stackHeights = calculateStackHeights(beatmap, diff.ar());
        double stackOffsetUnit = (1 - 0.7 * (diff.cs() - 5) / 5) / 2 * 6.4;

        AtomicInteger keyFrameIndex = new AtomicInteger(0);
        List<HitObject> hitObjects = beatmap.getHitObjects();

        final int n = keyFrames.size();

        int[] consumedActions = new int[n];
        long noteLockStart = Long.MIN_VALUE;

        for (int objIndex = 0; objIndex < hitObjects.size(); objIndex++) {
            HitObject hitObject = hitObjects.get(objIndex);
            double objectStackOffset = stackHeights[objIndex] * stackOffsetUnit;
            double objectX = hitObject.getX() - objectStackOffset;
            double objectY = hitObject.getY() - objectStackOffset;

            if (hitObject.getObjectType() != HitObject.ObjectType.HIT_CIRCLE
                    && hitObject.getObjectType() != HitObject.ObjectType.SLIDER
                    && hitObject.getObjectType() != HitObject.ObjectType.SPINNER) {
                continue;
            }

            if (hitObject.getObjectType() == HitObject.ObjectType.SPINNER) {
                List<HitEvent> spinnerEvents = analyzeSpinner(hitObject, objIndex, keyFrames,
                        diff, replay.mods());
                events.addAll(spinnerEvents);
                keyFrameIndex.set(Math.max(keyFrameIndex.get(), spinnerEvents.getFirst().frameIndex()));
                continue;
            }

            long objectStart = hitObject.getTime();
            double mehWindow = Math.floor(diff.getMehWindow());
            double hitWindowStart = objectStart - mehWindow;
            double searchTo = objectStart + mehWindow;

            int startIdx = Math.max(0, keyFrameIndex.get());
            int candidateIdx;

            while (startIdx < n && (keyFrames.get(startIdx).time() <= hitWindowStart
                    || keyFrames.get(startIdx).time() < noteLockStart)) {
                startIdx++;
            }
            candidateIdx = (startIdx >= n) ? n - 1 : startIdx;

            int foundFrameIndex = -1;
            int foundKeyFlags = 0;
            HitEvent.HitResult hitResult = HitEvent.HitResult.MISS;
            HitEvent.AimBias aimBias = null;

            for (int fi = candidateIdx; fi < n; fi++) {
                long frameTime = keyFrames.get(fi).time();
                if (frameTime <= hitWindowStart || frameTime < noteLockStart) continue;
                if (frameTime >= searchTo) break;

                OsuReplay.TimedKeyFrame frame = keyFrames.get(fi);
                int currentFlags = frame.key();

                int previousFlags = fi > 0 ? keyFrames.get(fi - 1).key() : 0;

                int currentActions = osuActions(currentFlags);
                int previousActions = osuActions(previousFlags);
                int availablePresses = (currentActions & ~previousActions) & ~consumedActions[fi];
                boolean isNewPress = availablePresses != 0;

                if (isNewPress) {
                    double cursorX = frame.cursorX();
                    double cursorY = frame.cursorY();

                    double dx = cursorX - objectX;
                    double dy = cursorY - objectY;
                    double distance = Math.hypot(dx, dy);

                    if (distance <= circleRadius) {
                        double theta = Math.atan2(dy, dx);
                        double angleFromLast = theta;

                        if (objIndex > 0) {
                            double lastOffset = stackHeights[objIndex - 1] * stackOffsetUnit;
                            double lastX = hitObjects.get(objIndex - 1).getX() - lastOffset;
                            double lastY = hitObjects.get(objIndex - 1).getY() - lastOffset;
                            angleFromLast = Math.atan2(objectY - lastY, objectX - lastX);
                        }

                        aimBias = new HitEvent.AimBias(theta, distance, angleFromLast);
                        foundFrameIndex = fi;
                        foundKeyFlags = currentFlags;

                        consumedActions[fi] |= Integer.lowestOneBit(availablePresses);
                        break;
                    } else {
                        if (aimBias == null || aimBias.distance() > distance) {
                            double theta = Math.atan2(dy, dx);
                            double angleFromLast = theta;

                            if (objIndex > 0) {
                                double lastOffset = stackHeights[objIndex - 1] * stackOffsetUnit;
                                double lastX = hitObjects.get(objIndex - 1).getX() - lastOffset;
                                double lastY = hitObjects.get(objIndex - 1).getY() - lastOffset;
                                angleFromLast = Math.atan2(objectY - lastY, objectX - lastX);
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

                if (Math.abs(offset) < Math.floor(diff.getPerfectWindow())) {
                    hitResult = HitEvent.HitResult.PERFECT;
                } else if (Math.abs(offset) < Math.floor(diff.getOkWindow())) {
                    hitResult = HitEvent.HitResult.OK;
                } else if (Math.abs(offset) < Math.floor(diff.getMehWindow())) {
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
            noteLockStart = wasHit ? Long.MIN_VALUE : objectStart;

            if (hitObject.getObjectType() == HitObject.ObjectType.SLIDER) {
                events.addAll(analyzeSlider(beatmap, hitObject, objIndex, keyFrames,
                        circleRadius, event, objectStackOffset));
            }
        }

        reconcileObjectResultTotals(events, replay);

        events.sort(Comparator.comparingLong(HitEvent::eventTime)
                .thenComparingInt(HitEvent::objectIndex));

        final double ur = calculateUR(events);

        return new ReplayAnalyze(beatmap, diff, replay, events, ur);
    }

    private static List<HitEvent> analyzeSpinner(HitObject spinner, int objectIndex,
                                                 List<OsuReplay.TimedKeyFrame> keyFrames,
                                                 DifficultyAttribute difficulty, int mods) {
        long startTime = spinner.getTime();
        long endTime = Math.max(startTime, spinner.getEndTime());
        ReplaySample endSample = sampleAt(keyFrames, endTime);

        double durationSeconds = (endTime - startTime) / 1000.0;
        // Hit-window OD includes the clock-rate adjustment. Spinner defaults do not,
        // so recover the EZ/HR-adjusted OD before applying lazer's RPM ranges.
        double spinnerOd = (80 - (80 - 6 * difficulty.od()) * difficulty.clockRate()) / 6;
        double clearRpm = difficultyRange(spinnerOd, 90, 150, 225);
        double completeRpm = difficultyRange(spinnerOd, 250, 380, 430);
        int requiredSpins = (int) (durationSeconds * clearRpm / 60 + 0.0001);
        int requiredForBonus = requiredSpins + SPINNER_BONUS_GAP;
        int maximumBonusSpins = Math.max(0,
                (int) (durationSeconds * completeRpm / 60 + 0.0001)
                        - requiredForBonus);
        int totalNestedSpins = requiredForBonus + maximumBonusSpins;
        double automaticRpm = automaticSpinnerRpm(mods);
        SpinnerTracking tracking = automaticRpm > 0
                ? automaticSpinnerTracking(keyFrames, startTime, endTime, difficulty.clockRate(),
                        automaticRpm, requiredSpins)
                : spinnerTracking(spinner, keyFrames, startTime, endTime, difficulty.clockRate());
        double progress = requiredSpins == 0 ? 1 : tracking.rotations() / requiredSpins;

        HitEvent.HitResult result;
        if (progress >= 1) {
            result = HitEvent.HitResult.PERFECT;
        } else if (progress > 0.9) {
            result = HitEvent.HitResult.OK;
        } else if (progress > 0.75) {
            result = HitEvent.HitResult.MEH;
        } else {
            result = HitEvent.HitResult.MISS;
        }

        boolean wasHit = result != HitEvent.HitResult.MISS;
        List<HitEvent> events = new ArrayList<>(1 + totalNestedSpins);
        events.add(new HitEvent(objectIndex, spinner, HitEvent.EventType.SPINNER, startTime,
                wasHit, result, null, endTime, wasHit ? 0 : Long.MIN_VALUE,
                (float) endSample.x(), (float) endSample.y(), endSample.keyFlags(), endSample.frameIndex()));

        for (int i = 0; i < totalNestedSpins; i++) {
            HitEvent.EventType type = i < requiredForBonus
                    ? HitEvent.EventType.SPINNER_SPIN
                    : HitEvent.EventType.SPINNER_BONUS;
            long nestedTime = totalNestedSpins == 0 ? endTime
                    : startTime + Math.round((double) (i + 1) / totalNestedSpins * (endTime - startTime));
            boolean hit = i < tracking.fullSpins().size();
            SpinnerSpin spin = hit ? tracking.fullSpins().get(i) : null;
            events.add(spinnerPartEvent(objectIndex, spinner, type, hit, nestedTime,
                    hit ? spin.time() : endTime, hit ? spin.sample() : endSample));
        }

        return events;
    }

    private static HitEvent spinnerPartEvent(int objectIndex, HitObject spinner,
                                             HitEvent.EventType type, boolean hit,
                                             long eventTime, long hitTime, ReplaySample sample) {
        return new HitEvent(objectIndex, spinner, type, eventTime, hit,
                hit ? HitEvent.HitResult.PERFECT : HitEvent.HitResult.MISS, null,
                hit ? hitTime : -1L, hit ? hitTime - eventTime : Long.MIN_VALUE,
                (float) sample.x(), (float) sample.y(), sample.keyFlags(), sample.frameIndex());
    }

    private static SpinnerTracking spinnerTracking(HitObject spinner,
                                                    List<OsuReplay.TimedKeyFrame> keyFrames,
                                                    long startTime, long endTime,
                                                    double clockRate) {
        ReplaySample previous = sampleAt(keyFrames, startTime);
        long previousTime = startTime;
        SpinnerTracker tracker = new SpinnerTracker(spinner, clockRate);
        int frameIndex = firstFrameAtOrAfter(keyFrames, startTime);

        while (frameIndex < keyFrames.size() && keyFrames.get(frameIndex).time() <= endTime) {
            OsuReplay.TimedKeyFrame frame = keyFrames.get(frameIndex++);
            if (frame.time() <= startTime) continue;

            ReplaySample current = new ReplaySample(frame.cursorX(), frame.cursorY(), frame.key(), frameIndex - 1);
            tracker.track(previousTime, previous, frame.time(), current);
            previous = current;
            previousTime = frame.time();
        }

        if (endTime > startTime && (frameIndex == 0 || keyFrames.get(frameIndex - 1).time() < endTime)) {
            tracker.track(previousTime, previous, endTime, sampleAt(keyFrames, endTime));
        }

        return tracker.result();
    }

    private static SpinnerTracking automaticSpinnerTracking(List<OsuReplay.TimedKeyFrame> keyFrames,
                                                             long startTime, long endTime,
                                                             double clockRate, double rpm,
                                                             int requiredSpins) {
        double durationSeconds = (endTime - startTime) / 1000.0 / clockRate;
        double rotations = Math.max(requiredSpins, durationSeconds * rpm / 60);
        int fullSpins = (int) Math.floor(rotations + 1e-7);
        List<SpinnerSpin> spins = new ArrayList<>(fullSpins);
        double mapMillisecondsPerSpin = 60_000 * clockRate / rpm;
        for (int i = 1; i <= fullSpins; i++) {
            long time = Math.min(endTime, startTime + Math.round(i * mapMillisecondsPerSpin));
            spins.add(new SpinnerSpin(time, sampleAt(keyFrames, time)));
        }
        return new SpinnerTracking(rotations, spins);
    }

    private static double automaticSpinnerRpm(int mods) {
        if ((mods & AUTO_MOD) != 0) return AUTO_SPINNER_RPM;
        if ((mods & SPUN_OUT_MOD) != 0) return SPUN_OUT_SPINNER_RPM;
        return 0;
    }

    private static int osuActions(int keyFlags) {
        int actions = 0;
        if ((keyFlags & 5) != 0) actions |= 1;
        if ((keyFlags & 10) != 0) actions |= 2;
        return actions;
    }

    private static void reconcileObjectResultTotals(List<HitEvent> events, OsuReplay replay) {
        // Legacy replay frames are lossy positional samples, while the replay header stores
        // the authoritative top-level judgement totals. Reconcile only complete plays;
        // lazer nested slider and spinner results remain entirely frame-derived.
        int[] target = {
                Short.toUnsignedInt(replay.count300()),
                Short.toUnsignedInt(replay.count100()),
                Short.toUnsignedInt(replay.count50()),
                Short.toUnsignedInt(replay.countMiss())
        };
        List<Integer> objectEvents = new ArrayList<>();
        int[] actual = new int[HitEvent.HitResult.values().length];
        for (int i = 0; i < events.size(); i++) {
            HitEvent event = events.get(i);
            if (!event.isObjectStart()) continue;
            objectEvents.add(i);
            actual[event.hitResult().ordinal()]++;
        }

        if (java.util.Arrays.stream(target).sum() != objectEvents.size()) return;

        for (HitEvent.HitResult wanted : List.of(HitEvent.HitResult.MISS, HitEvent.HitResult.MEH,
                HitEvent.HitResult.OK, HitEvent.HitResult.PERFECT)) {
            int wantedIndex = wanted.ordinal();
            while (actual[wantedIndex] < target[wantedIndex]) {
                int bestEventIndex = -1;
                double bestCost = Double.POSITIVE_INFINITY;
                for (int eventIndex : objectEvents) {
                    HitEvent event = events.get(eventIndex);
                    int sourceIndex = event.hitResult().ordinal();
                    if (actual[sourceIndex] <= target[sourceIndex]) continue;

                    double distance = event.aimBias() == null ? 0 : event.aimBias().distance();
                    double offset = event.wasHit() ? Math.abs(event.hitTimeOffset()) : 400;
                    boolean lowering = resultValue(wanted) < resultValue(event.hitResult());
                    double confidenceCost = lowering ? -distance - offset / 1000 : distance + offset / 1000;
                    double cost = Math.abs(resultValue(wanted) - resultValue(event.hitResult())) * 1000
                            + confidenceCost;
                    if (cost < bestCost) {
                        bestCost = cost;
                        bestEventIndex = eventIndex;
                    }
                }
                if (bestEventIndex < 0) return;

                HitEvent source = events.get(bestEventIndex);
                actual[source.hitResult().ordinal()]--;
                actual[wantedIndex]++;
                boolean hit = wanted != HitEvent.HitResult.MISS;
                events.set(bestEventIndex, new HitEvent(source.objectIndex(), source.hitObject(),
                        source.eventType(), source.eventTime(), hit, wanted, source.aimBias(),
                        hit ? (source.wasHit() ? source.hitTime() : source.eventTime()) : -1,
                        hit ? (source.wasHit() ? source.hitTimeOffset() : 0) : Long.MIN_VALUE,
                        source.cursorX(), source.cursorY(), source.keyFlags(), source.frameIndex()));
            }
        }
    }

    private static int resultValue(HitEvent.HitResult result) {
        return switch (result) {
            case MISS -> 0;
            case MEH -> 1;
            case OK -> 2;
            case PERFECT -> 3;
        };
    }

    private static double difficultyRange(double difficulty, double minimum,
                                          double middle, double maximum) {
        if (difficulty > 5) return middle + (maximum - middle) * (difficulty - 5) / 5;
        if (difficulty < 5) return middle - (middle - minimum) * (5 - difficulty) / 5;
        return middle;
    }

    private static double spinnerRotationBetween(HitObject spinner, ReplaySample from, ReplaySample to) {
        double fromX = from.x() - spinner.getX();
        double fromY = from.y() - spinner.getY();
        double toX = to.x() - spinner.getX();
        double toY = to.y() - spinner.getY();

        double angleDelta = Math.atan2(toY, toX) - Math.atan2(fromY, fromX);
        if (angleDelta > Math.PI) angleDelta -= TWO_PI;
        else if (angleDelta < -Math.PI) angleDelta += TWO_PI;
        return angleDelta;
    }

    private static List<HitEvent> analyzeSlider(OsuBeatmap beatmap, HitObject slider, int objectIndex,
                                                List<OsuReplay.TimedKeyFrame> keyFrames,
                                                double circleRadius, HitEvent headEvent,
                                                double stackOffset) {
        List<SliderNode> nodes = sliderNodes(beatmap, slider);
        if (nodes.isEmpty()) return List.of();

        SliderPath path = new SliderPath(slider, stackOffset);
        List<HitEvent> result = new ArrayList<>(nodes.size());
        // Legacy replay cursor positions are frame-quantised. Keep a small tolerance for
        // small circles so transient interpolation error does not falsely break tracking.
        double followRadius = circleRadius * 2.4 + Math.clamp(37 - circleRadius, 0, 5);
        double acquisitionRadius = circleRadius;
        double sliderDuration = nodes.stream().mapToDouble(SliderNode::eventTime).max()
                .orElse(slider.getTime()) - slider.getTime();
        double trackingStart = headEvent.wasHit() ? headEvent.hitTime() : slider.getTime();
        ReplaySample headSample = sampleAt(keyFrames, trackingStart);
        boolean passedNodesHit = true;
        if (headEvent.wasHit()) {
            for (SliderNode node : nodes) {
                if (node.eventTime() > trackingStart) break;
                Point target = path.positionAt(node.pathProgress());
                if (distance(headSample.x(), headSample.y(), target) > followRadius) {
                    passedNodesHit = false;
                    break;
                }
            }
        }
        Point ballAtTrackingStart = path.positionAt(sliderProgress(slider, sliderDuration, trackingStart));
        boolean tracking = headEvent.wasHit() && (passedNodesHit
                || distance(headSample.x(), headSample.y(), ballAtTrackingStart) <= circleRadius);
        int frameIndex = firstFrameAtOrAfter(keyFrames, trackingStart) - 1;

        for (SliderNode node : nodes) {
            if (headEvent.wasHit() && node.eventTime() <= trackingStart) {
                Point target = path.positionAt(node.pathProgress());
                double dx = headSample.x() - target.x();
                double dy = headSample.y() - target.y();
                Point before = path.positionAt(Math.max(0, node.pathProgress() - 0.001));
                double pathAngle = Math.atan2(target.y() - before.y(), target.x() - before.x());
                long eventTime = Math.round(node.eventTime());
                result.add(new HitEvent(objectIndex, slider, node.type(), eventTime,
                        passedNodesHit, passedNodesHit ? HitEvent.HitResult.PERFECT : HitEvent.HitResult.MISS,
                        new HitEvent.AimBias(Math.atan2(dy, dx), Math.hypot(dx, dy), pathAngle),
                        Math.round(trackingStart), passedNodesHit
                        ? Math.round(trackingStart) - eventTime : Long.MIN_VALUE,
                        (float) headSample.x(), (float) headSample.y(), headSample.keyFlags(),
                        headSample.frameIndex()));
                continue;
            }

            while (frameIndex + 1 < keyFrames.size()
                    && keyFrames.get(frameIndex + 1).time() <= node.judgementTime()) {
                frameIndex++;
                OsuReplay.TimedKeyFrame frame = keyFrames.get(frameIndex);
                Point ball = path.positionAt(sliderProgress(slider, sliderDuration, frame.time()));
                boolean held = (frame.key() & 15) != 0;
                double allowedRadius = tracking ? followRadius : acquisitionRadius;
                tracking = held && distance(frame.cursorX(), frame.cursorY(), ball) <= allowedRadius;
            }

            double actualJudgementTime = node.judgementTime();
            ReplaySample sample = sampleAt(keyFrames, actualJudgementTime);
            Point target = path.positionAt(node.pathProgress());
            boolean held = (sample.keyFlags() & 15) != 0;
            double allowedRadius = tracking ? followRadius : acquisitionRadius;
            tracking = held && distance(sample.x(), sample.y(), target) <= allowedRadius;

            // The final tail can be collected at any point from the legacy last-tick
            // position (up to 36 ms early) through the slider's true end.
            if (!tracking && node.type() == HitEvent.EventType.SLIDER_END
                    && node.judgementTime() < node.eventTime()) {
                while (frameIndex + 1 < keyFrames.size()
                        && keyFrames.get(frameIndex + 1).time() <= node.eventTime()) {
                    frameIndex++;
                    OsuReplay.TimedKeyFrame frame = keyFrames.get(frameIndex);
                    Point ball = path.positionAt(sliderProgress(slider, sliderDuration, frame.time()));
                    held = (frame.key() & 15) != 0;
                    allowedRadius = tracking ? followRadius : acquisitionRadius;
                    tracking = held && distance(frame.cursorX(), frame.cursorY(), ball) <= allowedRadius;
                    if (tracking) {
                        actualJudgementTime = frame.time();
                        sample = new ReplaySample(frame.cursorX(), frame.cursorY(), frame.key(), frameIndex);
                        target = ball;
                        break;
                    }
                }

                if (!tracking) {
                    actualJudgementTime = node.eventTime();
                    sample = sampleAt(keyFrames, actualJudgementTime);
                    target = path.positionAt(sliderProgress(slider, sliderDuration, actualJudgementTime));
                    held = (sample.keyFlags() & 15) != 0;
                    allowedRadius = tracking ? followRadius : acquisitionRadius;
                    tracking = held && distance(sample.x(), sample.y(), target) <= allowedRadius;
                }

                if (!tracking && !headEvent.wasHit() && held
                        && distance(sample.x(), sample.y(), target) <= followRadius) {
                    // The frame stream can omit the exact instant where tracking was acquired
                    // after a missed head; the tail's own held/in-range sample is authoritative.
                    tracking = true;
                }
            }

            double dx = sample.x() - target.x();
            double dy = sample.y() - target.y();
            double theta = Math.atan2(dy, dx);
            Point before = path.positionAt(Math.max(0, node.pathProgress() - 0.001));
            double pathAngle = Math.atan2(target.y() - before.y(), target.x() - before.x());
            HitEvent.AimBias aimBias = new HitEvent.AimBias(theta, Math.hypot(dx, dy), pathAngle);
            long eventTime = Math.round(node.eventTime());
            long judgementTime = Math.round(actualJudgementTime);

            result.add(new HitEvent(objectIndex, slider, node.type(), eventTime,
                    tracking, tracking ? HitEvent.HitResult.PERFECT : HitEvent.HitResult.MISS,
                    aimBias, judgementTime, tracking ? judgementTime - eventTime : Long.MIN_VALUE,
                    (float) sample.x(), (float) sample.y(), sample.keyFlags(), sample.frameIndex()));
        }

        return result;
    }

    private static List<SliderNode> sliderNodes(OsuBeatmap beatmap, HitObject slider) {
        double length = Math.max(0, slider.getLength());
        int spanCount = Math.max(1, slider.getSlides());
        if (length == 0) return List.of();

        SliderTiming timing = sliderTimingAt(beatmap, slider.getTime());
        double sliderMultiplier = beatmap.getSliderMultiplier() == null ? 1.4 : beatmap.getSliderMultiplier();
        double tickRate = beatmap.getSliderTickRate() == null ? 1.0 : beatmap.getSliderTickRate();
        if (sliderMultiplier <= 0 || tickRate <= 0 || timing.beatLength() <= 0) return List.of();

        double scoringDistance = sliderMultiplier * 100 * timing.velocityMultiplier();
        double spanDuration = length / scoringDistance * timing.beatLength();
        if (!Double.isFinite(spanDuration) || spanDuration <= 0) return List.of();

        double tickDistance = scoringDistance / tickRate;
        double velocity = length / spanDuration;
        double minimumDistanceFromEnd = velocity * 10;
        List<SliderNode> result = new ArrayList<>();

        for (int span = 0; span < spanCount; span++) {
            double spanStart = slider.getTime() + span * spanDuration;
            boolean reversed = (span & 1) == 1;

            if (tickDistance > 0) {
                for (double d = tickDistance; d < length - minimumDistanceFromEnd; d += tickDistance) {
                    double spanProgress = d / length;
                    double pathProgress = reversed ? 1 - spanProgress : spanProgress;
                    result.add(new SliderNode(HitEvent.EventType.SLIDER_TICK,
                            spanStart + spanProgress * spanDuration,
                            spanStart + spanProgress * spanDuration, pathProgress));
                }
            }

            double endTime = spanStart + spanDuration;
            double judgementTime = endTime;
            double pathProgress = reversed ? 0 : 1;
            if (span == spanCount - 1) {
                judgementTime = Math.max(slider.getTime() + spanCount * spanDuration / 2,
                        endTime - 36);
                double progressIntoSpan = (judgementTime - spanStart) / spanDuration;
                pathProgress = reversed ? 1 - progressIntoSpan : progressIntoSpan;
            }
            HitEvent.EventType endType = span == spanCount - 1
                    ? HitEvent.EventType.SLIDER_END
                    : HitEvent.EventType.SLIDER_TICK;
            result.add(new SliderNode(endType, endTime,
                    judgementTime, pathProgress));
        }

        result.sort(Comparator.comparingDouble(SliderNode::judgementTime));
        return result;
    }

    private static SliderTiming sliderTimingAt(OsuBeatmap beatmap, long time) {
        double beatLength = 1000;
        double velocityMultiplier = 1;
        if (beatmap.getTimingPoints() == null) return new SliderTiming(beatLength, velocityMultiplier);

        for (OsuBeatmap.TimingPoint point : beatmap.getTimingPoints()) {
            if (point.time() > time) break;
            if (point.uninherited() == 1 && point.beatLength() > 0) {
                beatLength = point.beatLength();
                velocityMultiplier = 1;
            } else if (point.uninherited() == 0 && point.beatLength() < 0) {
                velocityMultiplier = Math.clamp(-100 / point.beatLength(), 0.1, 10.0);
            }
        }
        return new SliderTiming(beatLength, velocityMultiplier);
    }

    private static double sliderProgress(HitObject slider, double duration, double time) {
        if (duration <= 0) return 0;
        int spans = Math.max(1, slider.getSlides());
        double spanPosition = Math.clamp((time - slider.getTime()) / duration, 0, 1) * spans;
        int span = Math.min(spans - 1, (int) spanPosition);
        double progress = spanPosition - span;
        return (span & 1) == 1 ? 1 - progress : progress;
    }

    private static int firstFrameAtOrAfter(List<OsuReplay.TimedKeyFrame> frames, double time) {
        int low = 0;
        int high = frames.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (frames.get(mid).time() < time) low = mid + 1;
            else high = mid;
        }
        return low;
    }

    private static ReplaySample sampleAt(List<OsuReplay.TimedKeyFrame> frames, double time) {
        int next = firstFrameAtOrAfter(frames, time);
        if (next == 0) {
            OsuReplay.TimedKeyFrame frame = frames.getFirst();
            return new ReplaySample(frame.cursorX(), frame.cursorY(), frame.key(), 0);
        }
        if (next >= frames.size()) {
            OsuReplay.TimedKeyFrame frame = frames.getLast();
            return new ReplaySample(frame.cursorX(), frame.cursorY(), frame.key(), frames.size() - 1);
        }

        OsuReplay.TimedKeyFrame previous = frames.get(next - 1);
        OsuReplay.TimedKeyFrame following = frames.get(next);
        if (following.time() == time) {
            return new ReplaySample(following.cursorX(), following.cursorY(), following.key(), next);
        }
        double interval = following.time() - previous.time();
        double progress = interval <= 0 ? 0 : (time - previous.time()) / interval;
        double x = previous.cursorX() + (following.cursorX() - previous.cursorX()) * progress;
        double y = previous.cursorY() + (following.cursorY() - previous.cursorY()) * progress;
        return new ReplaySample(x, y, previous.key(), next - 1);
    }

    private static double distance(double x, double y, Point point) {
        return Math.hypot(x - point.x(), y - point.y());
    }

    private record SliderTiming(double beatLength, double velocityMultiplier) {}
    private record SliderNode(HitEvent.EventType type, double eventTime,
                              double judgementTime, double pathProgress) {}
    private record SpinnerTracking(double rotations, List<SpinnerSpin> fullSpins) {}
    private record SpinnerSpin(long time, ReplaySample sample) {}
    private record ReplaySample(double x, double y, int keyFlags, int frameIndex) {}
    private record Point(double x, double y) {}

    private static final class SpinnerTracker {
        private final HitObject spinner;
        private final double clockRate;
        private final List<SpinnerSpin> fullSpins = new ArrayList<>();
        private double accumulatedRotation;
        private double accumulatedRotationAtLastCompletion;
        private double currentSpinMaxRotation;

        private SpinnerTracker(HitObject spinner, double clockRate) {
            this.spinner = spinner;
            this.clockRate = clockRate;
        }

        private void track(long fromTime, ReplaySample from, long toTime, ReplaySample to) {
            if ((to.keyFlags() & 15) == 0) return;

            double delta = spinnerRotationBetween(spinner, from, to) * clockRate;
            if (delta == 0) return;

            accumulatedRotation += delta;
            double currentSpinRotation = accumulatedRotation - accumulatedRotationAtLastCompletion;
            currentSpinMaxRotation = Math.max(currentSpinMaxRotation, Math.abs(currentSpinRotation));

            while (currentSpinMaxRotation >= TWO_PI) {
                int direction = currentSpinRotation < 0 ? -1 : 1;
                fullSpins.add(new SpinnerSpin(toTime, to));
                accumulatedRotationAtLastCompletion += direction * TWO_PI;
                currentSpinRotation = accumulatedRotation - accumulatedRotationAtLastCompletion;
                currentSpinMaxRotation = Math.abs(currentSpinRotation);
            }
        }

        private SpinnerTracking result() {
            return new SpinnerTracking(fullSpins.size() + currentSpinMaxRotation / TWO_PI,
                    List.copyOf(fullSpins));
        }
    }

    private static final class SliderPath {
        private final List<Point> points = new ArrayList<>();
        private final List<Double> cumulativeLength = new ArrayList<>();
        private final double expectedLength;

        private SliderPath(HitObject slider) {
            this(slider, 0);
        }

        private SliderPath(HitObject slider, double stackOffset) {
            expectedLength = Math.max(0, slider.getLength());
            List<Point> controls = new ArrayList<>();
            controls.add(new Point(slider.getX() - stackOffset, slider.getY() - stackOffset));
            for (HitObject.ControlPoint point : slider.getControlPoints()) {
                controls.add(new Point(point.x() - stackOffset, point.y() - stackOffset));
            }

            switch (slider.getCurveType() == null ? "L" : slider.getCurveType()) {
                case "B" -> addBezierSegments(controls);
                case "C" -> addCatmull(controls);
                case "P" -> {
                    if (controls.size() == 3 && !addPerfectCurve(controls)) addBezier(controls);
                    else if (controls.size() != 3) addBezierSegments(controls);
                }
                default -> controls.forEach(this::addPoint);
            }
            if (points.isEmpty()) {
                addPoint(new Point(slider.getX() - stackOffset, slider.getY() - stackOffset));
            }
            fitToExpectedLength();
            calculateLengths();
        }

        private Point positionAt(double progress) {
            if (points.size() == 1 || expectedLength <= 0) return points.getFirst();
            double target = Math.clamp(progress, 0, 1) * expectedLength;
            int index = java.util.Collections.binarySearch(cumulativeLength, target);
            if (index >= 0) return points.get(index);
            index = -index - 1;
            if (index <= 0) return points.getFirst();
            if (index >= points.size()) return points.getLast();
            double from = cumulativeLength.get(index - 1);
            double to = cumulativeLength.get(index);
            if (to <= from) return points.get(index - 1);
            double weight = (target - from) / (to - from);
            return interpolate(points.get(index - 1), points.get(index), weight);
        }

        private void addBezierSegments(List<Point> controls) {
            List<Point> segment = new ArrayList<>();
            segment.add(controls.getFirst());
            for (int i = 1; i < controls.size(); i++) {
                Point current = controls.get(i);
                segment.add(current);
                if (i < controls.size() - 1 && same(current, controls.get(i + 1))) {
                    addBezier(segment);
                    segment = new ArrayList<>();
                    segment.add(current);
                    i++;
                }
            }
            addBezier(segment);
        }

        private void addBezier(List<Point> controls) {
            if (controls.isEmpty()) return;
            if (controls.size() == 1) {
                addPoint(controls.getFirst());
                return;
            }
            double polygonLength = 0;
            for (int i = 1; i < controls.size(); i++) {
                polygonLength += distance(controls.get(i - 1), controls.get(i));
            }
            int samples = Math.clamp((int) Math.ceil(polygonLength / 0.25), 25, 10000);
            for (int i = 0; i <= samples; i++) {
                double t = (double) i / samples;
                List<Point> work = new ArrayList<>(controls);
                for (int level = work.size() - 1; level > 0; level--) {
                    for (int p = 0; p < level; p++) {
                        work.set(p, interpolate(work.get(p), work.get(p + 1), t));
                    }
                }
                addPoint(work.getFirst());
            }
        }

        private boolean addPerfectCurve(List<Point> controls) {
            Point a = controls.get(0);
            Point b = controls.get(1);
            Point c = controls.get(2);
            double determinant = 2 * (a.x() * (b.y() - c.y())
                    + b.x() * (c.y() - a.y()) + c.x() * (a.y() - b.y()));
            if (Math.abs(determinant) < 1e-7) return false;

            double a2 = a.x() * a.x() + a.y() * a.y();
            double b2 = b.x() * b.x() + b.y() * b.y();
            double c2 = c.x() * c.x() + c.y() * c.y();
            Point center = new Point(
                    (a2 * (b.y() - c.y()) + b2 * (c.y() - a.y()) + c2 * (a.y() - b.y())) / determinant,
                    (a2 * (c.x() - b.x()) + b2 * (a.x() - c.x()) + c2 * (b.x() - a.x())) / determinant);
            double start = Math.atan2(a.y() - center.y(), a.x() - center.x());
            double middle = Math.atan2(b.y() - center.y(), b.x() - center.x());
            double end = Math.atan2(c.y() - center.y(), c.x() - center.x());
            double sweep = positiveAngle(end - start);
            if (positiveAngle(middle - start) > sweep) sweep -= Math.PI * 2;
            double radius = distance(a, center);
            int samples = Math.clamp((int) Math.ceil(Math.abs(sweep * radius) / 2), 25, 1000);
            for (int i = 0; i <= samples; i++) {
                double angle = start + sweep * i / samples;
                addPoint(new Point(center.x() + Math.cos(angle) * radius,
                        center.y() + Math.sin(angle) * radius));
            }
            return true;
        }

        private void addCatmull(List<Point> controls) {
            if (controls.size() < 2) {
                controls.forEach(this::addPoint);
                return;
            }
            for (int i = 0; i < controls.size() - 1; i++) {
                Point p0 = controls.get(Math.max(0, i - 1));
                Point p1 = controls.get(i);
                Point p2 = controls.get(i + 1);
                Point p3 = controls.get(Math.min(controls.size() - 1, i + 2));
                for (int sample = 0; sample <= 50; sample++) {
                    double t = sample / 50.0;
                    double t2 = t * t;
                    double t3 = t2 * t;
                    double x = 0.5 * ((2 * p1.x()) + (-p0.x() + p2.x()) * t
                            + (2 * p0.x() - 5 * p1.x() + 4 * p2.x() - p3.x()) * t2
                            + (-p0.x() + 3 * p1.x() - 3 * p2.x() + p3.x()) * t3);
                    double y = 0.5 * ((2 * p1.y()) + (-p0.y() + p2.y()) * t
                            + (2 * p0.y() - 5 * p1.y() + 4 * p2.y() - p3.y()) * t2
                            + (-p0.y() + 3 * p1.y() - 3 * p2.y() + p3.y()) * t3);
                    addPoint(new Point(x, y));
                }
            }
        }

        private void fitToExpectedLength() {
            double currentLength = pathLength();
            if (expectedLength <= currentLength || points.size() < 2) return;
            int end = points.size() - 1;
            while (end > 0 && same(points.get(end), points.get(end - 1))) end--;
            if (end == 0) return;
            Point previous = points.get(end - 1);
            Point last = points.get(end);
            double segmentLength = distance(previous, last);
            if (segmentLength == 0) return;
            double extension = expectedLength - currentLength;
            addPoint(new Point(last.x() + (last.x() - previous.x()) / segmentLength * extension,
                    last.y() + (last.y() - previous.y()) / segmentLength * extension));
        }

        private double pathLength() {
            double length = 0;
            for (int i = 1; i < points.size(); i++) length += distance(points.get(i - 1), points.get(i));
            return length;
        }

        private void calculateLengths() {
            cumulativeLength.clear();
            cumulativeLength.add(0.0);
            for (int i = 1; i < points.size(); i++) {
                cumulativeLength.add(cumulativeLength.getLast() + distance(points.get(i - 1), points.get(i)));
            }
        }

        private void addPoint(Point point) {
            if (points.isEmpty() || !same(points.getLast(), point)) points.add(point);
        }

        private static Point interpolate(Point from, Point to, double weight) {
            return new Point(from.x() + (to.x() - from.x()) * weight,
                    from.y() + (to.y() - from.y()) * weight);
        }

        private static boolean same(Point a, Point b) {
            return Math.abs(a.x() - b.x()) < 1e-7 && Math.abs(a.y() - b.y()) < 1e-7;
        }

        private static double distance(Point a, Point b) {
            return Math.hypot(a.x() - b.x(), a.y() - b.y());
        }

        private static double positiveAngle(double angle) {
            angle %= Math.PI * 2;
            return angle < 0 ? angle + Math.PI * 2 : angle;
        }
    }

    public static double calculateUR(List<HitEvent> events) {
        List<Long> validOffsets = new LinkedList<>();

        for (HitEvent event : events) {
            if (event.wasHit() && event.isObjectStart()
                    && event.eventType() != HitEvent.EventType.SPINNER) {
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

    private static int[] calculateStackHeights(OsuBeatmap beatmap, double ar) {
        List<HitObject> hitObjects = beatmap.getHitObjects();
        int[] heights = new int[hitObjects.size()];
        if (hitObjects.isEmpty()) return heights;

        double stackLeniency = beatmap.getStackLeniency() == null ? 0.7 : beatmap.getStackLeniency();
        if (stackLeniency <= 0) return heights;
        double timePreempt = ar < 5 ? 1800 - 120 * ar : 1200 - 150 * (ar - 5);
        double stackThreshold = (int) timePreempt * stackLeniency;

        Point[] positions = new Point[hitObjects.size()];
        Point[] endPositions = new Point[hitObjects.size()];
        double[] endTimes = new double[hitObjects.size()];
        for (int i = 0; i < hitObjects.size(); i++) {
            HitObject object = hitObjects.get(i);
            positions[i] = new Point(object.getX(), object.getY());
            endPositions[i] = positions[i];
            endTimes[i] = object.getTime();
            if (object.getObjectType() == HitObject.ObjectType.SPINNER) {
                endTimes[i] = object.getEndTime();
            } else if (object.getObjectType() == HitObject.ObjectType.SLIDER) {
                List<SliderNode> nodes = sliderNodes(beatmap, object);
                endTimes[i] = nodes.stream().mapToDouble(SliderNode::eventTime).max()
                        .orElse(object.getTime());
                double endProgress = (Math.max(1, object.getSlides()) & 1) == 1 ? 1 : 0;
                endPositions[i] = new SliderPath(object).positionAt(endProgress);
            }
        }

        for (int i = hitObjects.size() - 1; i > 0; i--) {
            if (heights[i] != 0 || hitObjects.get(i).getObjectType() == HitObject.ObjectType.SPINNER) {
                continue;
            }

            int current = i;
            if (hitObjects.get(i).getObjectType() == HitObject.ObjectType.HIT_CIRCLE) {
                for (int n = i - 1; n >= 0; n--) {
                    HitObject previous = hitObjects.get(n);
                    if (previous.getObjectType() == HitObject.ObjectType.SPINNER) continue;
                    if ((int) hitObjects.get(current).getTime() - (int) endTimes[n] > stackThreshold) break;

                    if (previous.getObjectType() == HitObject.ObjectType.SLIDER
                            && SliderPath.distance(endPositions[n], positions[current]) < 3) {
                        int offset = heights[current] - heights[n] + 1;
                        for (int j = n + 1; j <= i; j++) {
                            if (SliderPath.distance(endPositions[n], positions[j]) < 3) heights[j] -= offset;
                        }
                        break;
                    }

                    if (SliderPath.distance(positions[n], positions[current]) < 3) {
                        heights[n] = heights[current] + 1;
                        current = n;
                    }
                }
            } else if (hitObjects.get(i).getObjectType() == HitObject.ObjectType.SLIDER) {
                for (int n = i - 1; n >= 0; n--) {
                    HitObject previous = hitObjects.get(n);
                    if (previous.getObjectType() == HitObject.ObjectType.SPINNER) continue;
                    if (hitObjects.get(current).getTime() - previous.getTime() > stackThreshold) break;
                    if (SliderPath.distance(endPositions[n], positions[current]) < 3) {
                        heights[n] = heights[current] + 1;
                        current = n;
                    }
                }
            }
        }
        return heights;
    }

    public static double calculateWindowAccuracy(ReplayAnalyze replayAnalyze, long startTime, long endTime) {
        int totalHits = 0;
        double score = 0;

        final List<HitEvent> events = replayAnalyze.events();
        for (HitEvent event : events) {
            if (!event.isObjectStart()) continue;

            final long time = event.eventTime();
            if (time >= startTime && time <= endTime) {
                totalHits++;

                HitEvent.HitResult hitValue = windowAccuracyResult(event, replayAnalyze.calculatedDifficulty());
                if (hitValue == HitEvent.HitResult.PERFECT) {
                    score += 300;
                } else if (hitValue == HitEvent.HitResult.OK) {
                    score += 100;
                } else if (hitValue == HitEvent.HitResult.MEH) {
                    score += 50;
                }
            }

            if (time > endTime) break;
        }

        if (totalHits == 0) return 0.0;

        return score / (totalHits * 300.0);
    }

    private static HitEvent.HitResult windowAccuracyResult(HitEvent event, DifficultyAttribute difficulty) {
        if (!event.wasHit() || event.eventType() == HitEvent.EventType.SPINNER) return event.hitResult();

        long absoluteOffset = Math.abs(event.hitTimeOffset());
        if (absoluteOffset <= difficulty.getPerfectWindow()) return HitEvent.HitResult.PERFECT;
        if (absoluteOffset <= difficulty.getOkWindow()) return HitEvent.HitResult.OK;
        if (absoluteOffset <= difficulty.getMehWindow()) return HitEvent.HitResult.MEH;
        return HitEvent.HitResult.MISS;
    }
}
