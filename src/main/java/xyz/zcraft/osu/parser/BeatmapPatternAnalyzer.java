package xyz.zcraft.osu.parser;

import xyz.zcraft.osu.parser.data.beatmap.BeatmapPatternAnalysis;
import xyz.zcraft.osu.parser.data.beatmap.DifficultyAttribute;
import xyz.zcraft.osu.parser.data.beatmap.HitObject;
import xyz.zcraft.osu.parser.data.beatmap.OsuBeatmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static xyz.zcraft.osu.parser.data.beatmap.BeatmapPatternAnalysis.AimPatternType;
import static xyz.zcraft.osu.parser.data.beatmap.BeatmapPatternAnalysis.PatternType;

/**
 * Classifies osu!standard maps from hit-object timing, placement and slider geometry.
 * The scores are a pattern profile rather than difficulty or performance values.
 */
public final class BeatmapPatternAnalyzer {
    private static final double DEFAULT_BEAT_LENGTH = 500.0;
    private static final double CROSS_SCREEN_DISTANCE = 300.0;

    private BeatmapPatternAnalyzer() {
    }

    public static BeatmapPatternAnalysis analyze(OsuBeatmap beatmap, DifficultyAttribute difficulty) {
        if (beatmap == null) throw new IllegalArgumentException("beatmap must not be null");

        List<HitObject> objects = beatmap.getHitObjects() == null
                ? List.of()
                : beatmap.getHitObjects().stream()
                .filter(object -> object.getObjectType() != HitObject.ObjectType.SPINNER)
                .sorted(Comparator.comparingLong(HitObject::getTime))
                .toList();
        if (objects.isEmpty()) {
            return result(new double[PatternType.values().length],
                    new double[AimPatternType.values().length],
                    new BeatmapPatternAnalysis.PatternMetrics(0, 0, 0, 0, 0, 0));
        }

        double clockRate = difficulty == null ? 1.0 : Math.max(0.1, difficulty.clockRate());
        double circleRadius = difficulty == null
                ? 54.4 - 4.48 * valueOr(beatmap.getCs(), 4.0)
                : difficulty.getCircleRadiusInPixel();
        double diameter = Math.max(12.0, circleRadius * 2.0);
        double approachRate = difficulty == null ? valueOr(beatmap.getAr(), 9.0) : difficulty.ar();

        int circles = 0;
        int sliders = 0;
        int complexSliders = 0;
        double sliderGeometryTotal = 0;
        for (HitObject object : objects) {
            if (object.getObjectType() == HitObject.ObjectType.HIT_CIRCLE) circles++;
            if (object.getObjectType() == HitObject.ObjectType.SLIDER) {
                sliders++;
                if (isComplexSlider(object)) complexSliders++;
                sliderGeometryTotal += sliderGeometry(object);
            }
        }

        List<Transition> transitions = new ArrayList<>();
        Vector previousVector = null;
        double previousInterval = -1;
        double previousDistance = -1;
        double totalSpacing = 0;
        double rhythmVariationTotal = 0;
        double spacingVariationTotal = 0;
        int overlapCount = 0;
        int readingOverlapCount = 0;
        int jumpCount = 0;
        int bigJumpCount = 0;
        int sharpCount = 0;
        int smoothCount = 0;
        int angleCount = 0;
        double normalizedDistanceTotal = 0;
        double[] aimEvidence = new double[AimPatternType.values().length];
        boolean breakBeforeTransition = false;

        for (int index = 1; index < objects.size(); index++) {
            HitObject previous = objects.get(index - 1);
            HitObject current = objects.get(index);
            double interval = Math.max(1.0, (current.getTime() - previous.getTime()) / clockRate);
            double beatLength = beatLengthAt(beatmap, current.getTime()) / clockRate;
            if (interval > Math.max(2_000.0 / clockRate, beatLength * 3.0)) {
                previousVector = null;
                previousInterval = -1;
                previousDistance = -1;
                breakBeforeTransition = true;
                continue;
            }

            Point cursorFrom = endPoint(previous);
            Point cursorTo = startPoint(current);
            Vector vector = cursorFrom.vectorTo(cursorTo);
            double distance = vector.length();
            double normalizedDistance = distance / diameter;
            double visualDistance = startPoint(previous).distanceTo(cursorTo);
            double beatSnap = interval / Math.max(1.0, beatLength);
            Double turn = previousVector == null || previousVector.length() == 0 || vector.length() == 0
                    ? null : angleBetween(previousVector, vector);

            boolean circlePair = previous.getObjectType() == HitObject.ObjectType.HIT_CIRCLE
                    && current.getObjectType() == HitObject.ObjectType.HIT_CIRCLE;
            transitions.add(new Transition(interval, beatSnap, distance, normalizedDistance,
                    visualDistance, turn, circlePair, breakBeforeTransition));
            breakBeforeTransition = false;
            totalSpacing += distance;
            normalizedDistanceTotal += normalizedDistance;

            if (distance < diameter * 0.70) overlapCount++;
            if (visualDistance < diameter && beatSnap > 0.38) readingOverlapCount++;
            if (normalizedDistance > 1.50) jumpCount++;
            if (normalizedDistance > 3.0) bigJumpCount++;

            double rhythmChange = logarithmicChange(previousInterval, interval);
            double spacingChange = logarithmicChange(previousDistance, distance);
            rhythmVariationTotal += rhythmChange;
            spacingVariationTotal += spacingChange;

            if (turn != null) {
                angleCount++;
                if (turn > 105.0) sharpCount++;
                if (turn < 65.0) smoothCount++;
            }
            addAimEvidence(aimEvidence, interval, beatSnap, distance, normalizedDistance,
                    turn, rhythmChange, spacingChange);

            previousVector = vector;
            previousInterval = interval;
            previousDistance = distance;
        }

        int transitionCount = transitions.size();
        double divisor = Math.max(1, transitionCount);
        RunFeature burst = runFeature(transitions, 0.08, 0.40, 3, objects.size());
        RunFeature stream = runFeature(transitions, 0.08, 0.40, 5, objects.size());
        RunFeature alt = runFeature(transitions, 0.38, 0.80, 4, objects.size());

        double sliderRatio = sliders / (double) objects.size();
        double complexSliderRatio = complexSliders / (double) objects.size();
        double sliderGeometry = sliders == 0 ? 1.0 : sliderGeometryTotal / sliders;
        double curvedSliderFactor = Math.max(0, sliderGeometry - 1.0);
        double jumpRatio = jumpCount / divisor;
        double bigJumpRatio = bigJumpCount / divisor;
        double averageNormalizedDistance = normalizedDistanceTotal / divisor;
        double sharpRatio = sharpCount / (double) Math.max(1, angleCount);
        double smoothRatio = smoothCount / (double) Math.max(1, angleCount);
        double rhythmVariation = rhythmVariationTotal / divisor;
        double spacingVariation = spacingVariationTotal / divisor;
        double readingOverlapRatio = readingOverlapCount / divisor;
        double collisionRatio = visualCollisionRatio(objects, diameter, approachRate, clockRate);

        double[] typeEvidence = categoryEvidence(objects.size(), approachRate,
                sliderRatio, complexSliderRatio, sliderGeometry, curvedSliderFactor,
                burst.coverage(), stream.coverage(), alt.coverage(),
                jumpRatio, bigJumpRatio, averageNormalizedDistance,
                sharpRatio, smoothRatio, rhythmVariation, spacingVariation,
                readingOverlapRatio, collisionRatio);

        return result(typeEvidence, aimEvidence, new BeatmapPatternAnalysis.PatternMetrics(
                objects.size(), circles, sliders, totalSpacing / divisor,
                overlapCount * 100.0 / divisor, rhythmVariation));
    }

    private static double[] categoryEvidence(
            int objectCount,
            double approachRate,
            double sliderRatio,
            double complexSliderRatio,
            double sliderGeometry,
            double curvedSliderFactor,
            double burstCoverage,
            double streamCoverage,
            double altCoverage,
            double jumpRatio,
            double bigJumpRatio,
            double averageNormalizedDistance,
            double sharpRatio,
            double smoothRatio,
            double rhythmVariation,
            double spacingVariation,
            double readingOverlapRatio,
            double collisionRatio
    ) {
        double[] evidence = new double[PatternType.values().length];

        double tech = 0.20
                + 0.60 * sliderRatio
                + 1.50 * complexSliderRatio
                + 2.00 * curvedSliderFactor * sliderRatio
                + 0.30 * rhythmVariation
                + 0.30 * spacingVariation
                + 0.20 * sharpRatio;
        double stream = 0.15
                + 1.55 * burstCoverage
                + 1.65 * streamCoverage
                + 0.35 * smoothRatio
                - 0.80 * curvedSliderFactor;
        double aim = 0.25
                + 1.45 * jumpRatio
                + 0.55 * bigJumpRatio
                + 0.25 * averageNormalizedDistance
                + 0.30 * altCoverage
                - 0.35 * burstCoverage;
        double regularAltDiscount = 1.0 - 0.75 * Math.min(1.0, altCoverage);
        double reading = 0.12
                + 1.20 * Math.max(0, 9.10 - approachRate)
                + (1.50 * readingOverlapRatio + 0.80 * collisionRatio) * regularAltDiscount
                + 0.35 * spacingVariation
                - 0.50 * curvedSliderFactor
                - 0.40 * complexSliderRatio;
        double flow = 0.10
                + 0.90 * smoothRatio * (1.0 - 0.75 * burstCoverage)
                + 0.15 * sliderRatio
                + 0.10 * Math.min(2.0, averageNormalizedDistance);
        double alt = 0.08
                + 2.10 * altCoverage * (1.0 - 0.75 * Math.min(1.0, jumpRatio));

        // Whole-map labels are dominated by sustained local patterns. These gates promote a
        // strong, interpretable signature while leaving the other category shares visible.
        boolean readingSignature = (approachRate <= 8.85
                && readingOverlapRatio >= 0.10
                && altCoverage < 0.60
                && curvedSliderFactor < 0.18)
                || (approachRate <= 9.05
                && readingOverlapRatio >= 0.09
                && collisionRatio >= 0.15
                && altCoverage < 0.60
                && curvedSliderFactor < 0.12);
        boolean streamSignature = burstCoverage >= 0.35 && sliderGeometry < 1.15;
        boolean techSignature = sliderRatio > 0.30
                && (sliderGeometry > 1.12 || complexSliderRatio > 0.16 || sliderRatio > 0.56);

        if (readingSignature) reading += 2.0;
        else if (streamSignature) stream += 2.0;
        else if (techSignature) tech += 2.0;

        double scale = Math.max(1, objectCount);
        evidence[PatternType.TECH.ordinal()] = Math.max(0, tech) * scale;
        evidence[PatternType.STREAM.ordinal()] = Math.max(0, stream) * scale;
        evidence[PatternType.AIM.ordinal()] = Math.max(0, aim) * scale;
        evidence[PatternType.READING.ordinal()] = Math.max(0, reading) * scale;
        evidence[PatternType.FLOW.ordinal()] = Math.max(0, flow) * scale;
        evidence[PatternType.ALT.ordinal()] = Math.max(0, alt) * scale;
        return evidence;
    }

    private static void addAimEvidence(
            double[] evidence,
            double interval,
            double beatSnap,
            double distance,
            double normalizedDistance,
            Double turn,
            double rhythmChange,
            double spacingChange
    ) {
        double jump = Math.max(0, normalizedDistance - 0.70)
                * clamp(250.0 / interval, 0.35, 2.25);
        if (jump <= 0) return;

        evidence[AimPatternType.JUMP_AIM.ordinal()] += jump * 0.45;
        if (beatSnap >= 0.38) {
            evidence[AimPatternType.SNAP_AIM.ordinal()] += jump
                    * (0.55 + 0.35 * clamp(beatSnap, 0, 1.5));
        }
        if (distance >= CROSS_SCREEN_DISTANCE || normalizedDistance >= 3.0) {
            evidence[AimPatternType.CROSS_SCREEN_JUMP_AIM.ordinal()] += jump
                    * (1.25 + Math.max(0, distance - 230.0) / 180.0);
        }
        if (turn == null) return;

        double smoothness = clamp(1.0 - Math.max(0, turn - 65.0) / 100.0, 0, 1);
        evidence[AimPatternType.FLOW_AIM.ordinal()] += jump * smoothness
                * clamp(0.50 / Math.max(0.15, beatSnap), 0.35, 1.35);

        double awkwardness = clamp((turn - 85.0) / 95.0, 0, 1)
                + 0.35 * Math.min(1.5, rhythmChange + spacingChange);
        evidence[AimPatternType.AWKWARD_AIM.ordinal()] += jump * awkwardness * 1.40;

        if (turn <= 24.0) {
            evidence[AimPatternType.LINEAR_JUMP_AIM.ordinal()] += jump * 1.10;
        } else if (turn <= 92.0) {
            evidence[AimPatternType.WIDE_ANGLE_JUMP_AIM.ordinal()] += jump * 1.05;
        } else {
            evidence[AimPatternType.SHARP_ANGLE_JUMP_AIM.ordinal()] += jump
                    * (0.50 + turn / 360.0);
        }
        if (turn >= 150.0) {
            evidence[AimPatternType.BACK_AND_FORTH_AIM.ordinal()] += jump
                    * (0.80 + (turn - 150.0) / 60.0);
        }
    }

    private static RunFeature runFeature(
            List<Transition> transitions,
            double minimumSnap,
            double maximumSnap,
            int minimumObjects,
            int totalObjects
    ) {
        int coveredObjects = 0;
        int longestRun = 0;
        int currentObjects = 1;
        for (Transition transition : transitions) {
            if (transition.breakBefore()) {
                if (currentObjects >= minimumObjects) {
                    coveredObjects += currentObjects;
                    longestRun = Math.max(longestRun, currentObjects);
                }
                currentObjects = 1;
            }
            if (transition.circlePair()
                    && transition.beatSnap() >= minimumSnap
                    && transition.beatSnap() <= maximumSnap) {
                currentObjects++;
            } else {
                if (currentObjects >= minimumObjects) {
                    coveredObjects += currentObjects;
                    longestRun = Math.max(longestRun, currentObjects);
                }
                currentObjects = 1;
            }
        }
        if (currentObjects >= minimumObjects) {
            coveredObjects += currentObjects;
            longestRun = Math.max(longestRun, currentObjects);
        }
        return new RunFeature(coveredObjects / (double) Math.max(1, totalObjects), longestRun);
    }

    private static double visualCollisionRatio(
            List<HitObject> objects,
            double diameter,
            double approachRate,
            double clockRate
    ) {
        double preempt = approachRate >= 5
                ? 1200 - 150 * (approachRate - 5)
                : 1800 - 120 * approachRate;
        int collisions = 0;
        for (int index = 0; index < objects.size(); index++) {
            Point current = startPoint(objects.get(index));
            for (int previousIndex = index - 2; previousIndex >= 0; previousIndex--) {
                HitObject previous = objects.get(previousIndex);
                if ((objects.get(index).getTime() - previous.getTime()) / clockRate > preempt) break;
                if (current.distanceTo(startPoint(previous)) < diameter * 0.90) {
                    collisions++;
                    break;
                }
            }
        }
        return collisions / (double) Math.max(1, objects.size());
    }

    private static boolean isComplexSlider(HitObject slider) {
        int points = slider.getControlPoints() == null ? 0 : slider.getControlPoints().size();
        return points >= 3 || slider.getSlides() > 1;
    }

    private static double sliderGeometry(HitObject slider) {
        if (slider.getControlPoints() == null || slider.getControlPoints().isEmpty()) return 1.0;
        Point start = startPoint(slider);
        Point previous = start;
        double path = 0;
        for (HitObject.ControlPoint control : slider.getControlPoints()) {
            Point next = new Point(control.x(), control.y());
            path += previous.distanceTo(next);
            previous = next;
        }
        double chord = start.distanceTo(previous);
        return clamp(path / Math.max(20.0, chord), 1.0, 4.0);
    }

    private static BeatmapPatternAnalysis result(
            double[] typeEvidence,
            double[] aimEvidence,
            BeatmapPatternAnalysis.PatternMetrics metrics
    ) {
        double typeTotal = Arrays.stream(typeEvidence).map(value -> Math.max(0, value)).sum();
        List<BeatmapPatternAnalysis.PatternScore> types = new ArrayList<>();
        for (PatternType type : PatternType.values()) {
            double evidence = Math.max(0, typeEvidence[type.ordinal()]);
            types.add(new BeatmapPatternAnalysis.PatternScore(
                    type, typeTotal > 0 ? evidence / typeTotal * 100 : 0, evidence));
        }
        types.sort(Comparator.comparingDouble(BeatmapPatternAnalysis.PatternScore::percentage).reversed());

        double aimTotal = Arrays.stream(aimEvidence).map(value -> Math.max(0, value)).sum();
        List<BeatmapPatternAnalysis.AimPatternScore> aimTypes = new ArrayList<>();
        for (AimPatternType type : AimPatternType.values()) {
            double evidence = Math.max(0, aimEvidence[type.ordinal()]);
            aimTypes.add(new BeatmapPatternAnalysis.AimPatternScore(
                    type, aimTotal > 0 ? evidence / aimTotal * 100 : 0, evidence));
        }
        aimTypes.sort(Comparator.comparingDouble(BeatmapPatternAnalysis.AimPatternScore::percentage).reversed());
        return new BeatmapPatternAnalysis(types, aimTypes, metrics);
    }

    private static Point startPoint(HitObject object) {
        return new Point(object.getX(), object.getY());
    }

    private static Point endPoint(HitObject object) {
        if (object.getObjectType() != HitObject.ObjectType.SLIDER
                || object.getControlPoints() == null
                || object.getControlPoints().isEmpty()
                || object.getSlides() % 2 == 0) {
            return startPoint(object);
        }
        HitObject.ControlPoint end = object.getControlPoints().getLast();
        return new Point(end.x(), end.y());
    }

    private static double beatLengthAt(OsuBeatmap beatmap, long time) {
        double beatLength = DEFAULT_BEAT_LENGTH;
        if (beatmap.getTimingPoints() == null) return beatLength;
        for (OsuBeatmap.TimingPoint point : beatmap.getTimingPoints()) {
            if (point.time() > time) break;
            if (point.uninherited() == 1 && point.beatLength() > 0) beatLength = point.beatLength();
        }
        return beatLength;
    }

    private static double angleBetween(Vector first, Vector second) {
        double cosine = (first.x() * second.x() + first.y() * second.y())
                / (first.length() * second.length());
        return Math.toDegrees(Math.acos(clamp(cosine, -1, 1)));
    }

    private static double logarithmicChange(double first, double second) {
        if (first <= 0 || second <= 0) return 0;
        return Math.min(2.0, Math.abs(Math.log(second / first)));
    }

    private static double valueOr(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.clamp(value, minimum, maximum);
    }

    private record RunFeature(double coverage, int longestRun) {
    }

    private record Transition(
            double interval,
            double beatSnap,
            double distance,
            double normalizedDistance,
            double visualDistance,
            Double turn,
            boolean circlePair,
            boolean breakBefore
    ) {
    }

    private record Point(double x, double y) {
        Vector vectorTo(Point other) {
            return new Vector(other.x - x, other.y - y);
        }

        double distanceTo(Point other) {
            return Math.hypot(other.x - x, other.y - y);
        }
    }

    private record Vector(double x, double y) {
        double length() {
            return Math.hypot(x, y);
        }
    }
}
