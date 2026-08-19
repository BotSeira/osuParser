package xyz.zcraft.osu.parser;

import org.junit.jupiter.api.Test;
import xyz.zcraft.osu.parser.data.beatmap.BeatmapPatternAnalysis;
import xyz.zcraft.osu.parser.data.beatmap.DifficultyAttribute;
import xyz.zcraft.osu.parser.data.beatmap.HitObject;
import xyz.zcraft.osu.parser.data.beatmap.OsuBeatmap;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static xyz.zcraft.osu.parser.data.beatmap.BeatmapPatternAnalysis.AimPatternType.CROSS_SCREEN_JUMP_AIM;
import static xyz.zcraft.osu.parser.data.beatmap.BeatmapPatternAnalysis.PatternType.AIM;
import static xyz.zcraft.osu.parser.data.beatmap.BeatmapPatternAnalysis.PatternType.ALT;
import static xyz.zcraft.osu.parser.data.beatmap.BeatmapPatternAnalysis.PatternType.READING;
import static xyz.zcraft.osu.parser.data.beatmap.BeatmapPatternAnalysis.PatternType.STREAM;

class BeatmapPatternAnalyzerTest {
    private static final DifficultyAttribute NORMAL = new DifficultyAttribute(4, 8, 9, 6, 8, 1);

    @Test
    void identifiesLongQuarterBeatCircleRunsAsStream() {
        OsuBeatmap map = map(9);
        for (int index = 0; index < 14; index++) {
            map.getHitObjects().add(circle(1000 + index * 125L, 120 + index * 18, 190 + (index % 3) * 14));
        }

        BeatmapPatternAnalysis analysis = BeatmapPatternAnalyzer.analyze(map, NORMAL);

        assertEquals(STREAM, analysis.primaryType().type());
        assertEquals(6, analysis.types().size());
        assertNormalized(analysis.types().stream().mapToDouble(BeatmapPatternAnalysis.PatternScore::percentage).sum());
    }

    @Test
    void identifiesSustainedHalfBeatPatternsAsAlt() {
        OsuBeatmap map = map(9);
        for (int index = 0; index < 12; index++) {
            map.getHitObjects().add(circle(1000 + index * 250L, 220 + (index % 2) * 60, 180));
        }

        BeatmapPatternAnalysis analysis = BeatmapPatternAnalyzer.analyze(map, NORMAL);

        assertEquals(ALT, analysis.primaryType().type());
    }

    @Test
    void identifiesCrossScreenJumpAimFromPlacementGeometry() {
        OsuBeatmap map = map(9.5);
        for (int index = 0; index < 9; index++) {
            boolean left = index % 2 == 0;
            map.getHitObjects().add(circle(1000 + index * 500L, left ? 32 : 480, left ? 48 : 336));
        }

        BeatmapPatternAnalysis analysis = BeatmapPatternAnalyzer.analyze(map, NORMAL);

        assertEquals(AIM, analysis.primaryType().type());
        assertEquals(CROSS_SCREEN_JUMP_AIM, analysis.primaryAimType().type());
        assertNormalized(analysis.aimTypes().stream()
                .mapToDouble(BeatmapPatternAnalysis.AimPatternScore::percentage).sum());
    }

    @Test
    void lowArOverlapsProduceReadingEvidence() {
        OsuBeatmap map = map(5);
        long[] times = {1000, 1125, 1500, 1625, 2125, 2250, 2625, 2750};
        for (int index = 0; index < times.length; index++) {
            map.getHitObjects().add(circle(times[index], 256 + index % 2 * 8, 192 + index % 3 * 5));
        }
        DifficultyAttribute lowAr = new DifficultyAttribute(4, 7, 5, 6, 7, 1);

        BeatmapPatternAnalysis analysis = BeatmapPatternAnalyzer.analyze(map, lowAr);

        assertTrue(analysis.types().stream()
                .filter(type -> type.type() == READING)
                .findFirst().orElseThrow().percentage() > 20);
        assertTrue(analysis.metrics().overlapRatio() > 80);
    }

    @Test
    void analyzesAParsedRealBeatmapWithoutInvalidWeights() throws Exception {
        OsuBeatmap map = BeatmapParser.parseBeatmap(
                Path.of("src/test/resources/beatmaps/5198852.osu"));

        BeatmapPatternAnalysis analysis = BeatmapPatternAnalyzer.analyze(
                map, BeatmapAnalyzer.calculateDifficulty(map, 0));

        assertTrue(analysis.metrics().objectCount() > 100);
        assertEquals(6, analysis.types().size());
        assertTrue(analysis.types().stream().allMatch(type -> Double.isFinite(type.percentage())));
        assertTrue(analysis.aimTypes().stream().allMatch(type -> Double.isFinite(type.percentage())));
        assertNormalized(analysis.types().stream().mapToDouble(BeatmapPatternAnalysis.PatternScore::percentage).sum());
        assertNormalized(analysis.aimTypes().stream()
                .mapToDouble(BeatmapPatternAnalysis.AimPatternScore::percentage).sum());
    }

    private static OsuBeatmap map(double ar) {
        OsuBeatmap map = new OsuBeatmap();
        map.setCs(4.0);
        map.setAr(ar);
        map.setOd(8.0);
        map.setHp(6.0);
        map.setTimingPoints(List.of(new OsuBeatmap.TimingPoint(0, 500, 4, 1, 0, 100, 1, 0)));
        return map;
    }

    private static HitObject circle(long time, int x, int y) {
        HitObject object = new HitObject();
        object.setTime(time);
        object.setX(x);
        object.setY(y);
        object.setObjectType(HitObject.ObjectType.HIT_CIRCLE);
        return object;
    }

    private static void assertNormalized(double total) {
        assertEquals(100.0, total, 0.000_001);
    }
}
