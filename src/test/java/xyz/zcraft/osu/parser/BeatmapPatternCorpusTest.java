package xyz.zcraft.osu.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import xyz.zcraft.osu.parser.data.beatmap.BeatmapPatternAnalysis;
import xyz.zcraft.osu.parser.data.beatmap.OsuBeatmap;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static xyz.zcraft.osu.parser.data.beatmap.BeatmapPatternAnalysis.AimPatternType.AWKWARD_AIM;
import static xyz.zcraft.osu.parser.data.beatmap.BeatmapPatternAnalysis.AimPatternType.CROSS_SCREEN_JUMP_AIM;
import static xyz.zcraft.osu.parser.data.beatmap.BeatmapPatternAnalysis.AimPatternType.WIDE_ANGLE_JUMP_AIM;
import static xyz.zcraft.osu.parser.data.beatmap.BeatmapPatternAnalysis.PatternType;

class BeatmapPatternCorpusTest {
    private static final Path CORPUS = Path.of("src/test/resources/beatmaps/classification");

    @Test
    void classifiesCuratedRealBeatmapCorpus() {
        List<Executable> checks = new ArrayList<>();
        for (Map.Entry<String, PatternType> entry : expectedTypes().entrySet()) {
            checks.add(() -> {
                BeatmapPatternAnalysis analysis = analyze(entry.getKey());
                assertEquals(entry.getValue(), analysis.primaryType().type(), entry.getKey());
                assertEquals(100.0, analysis.types().stream()
                        .mapToDouble(BeatmapPatternAnalysis.PatternScore::percentage).sum(), 0.000_001);
                assertTrue(analysis.types().stream()
                        .allMatch(score -> Double.isFinite(score.percentage()) && score.percentage() >= 0));
            });
        }
        assertAll(checks);
    }

    @Test
    void exposesAnnotatedAimCharacteristics() throws Exception {
        assertEquals(CROSS_SCREEN_JUMP_AIM, analyze("129847").primaryAimType().type(),
                "square and cross-screen jump map");
        assertEquals(AWKWARD_AIM, analyze("2697301").primaryAimType().type(),
                "awkward aim map");
        assertTrue(aimPercentage("4130854", WIDE_ANGLE_JUMP_AIM) > 3,
                "wide-angle jump map");
    }

    private static BeatmapPatternAnalysis analyze(String id) throws Exception {
        OsuBeatmap map = BeatmapParser.parseBeatmap(CORPUS.resolve(id + ".osu"));
        return BeatmapPatternAnalyzer.analyze(map, BeatmapAnalyzer.calculateDifficulty(map, 0));
    }

    private static double aimPercentage(String id, BeatmapPatternAnalysis.AimPatternType type)
            throws Exception {
        return analyze(id).aimTypes().stream()
                .filter(score -> score.type() == type)
                .findFirst().orElseThrow().percentage();
    }

    private static Map<String, PatternType> expectedTypes() {
        Map<String, PatternType> result = new LinkedHashMap<>();
        add(result, PatternType.TECH,
                "1044831", "3924950", "970190", "4649264", "4654773", "3802658",
                "5523978", "3507057", "2898393", "4894589", "2895460");
        add(result, PatternType.STREAM,
                "2469254", "3954418", "4352547", "2156842", "4442129",
                "5349820", "3732960", "4914172", "3018109");
        add(result, PatternType.AIM,
                "5350214", "5169058", "947145", "5542524", "2697301",
                "5290576", "129847", "4130854", "735757", "5607874");
        add(result, PatternType.READING,
                "1511428", "2012215", "2817456", "5198852");
        return result;
    }

    private static void add(Map<String, PatternType> target, PatternType type, String... ids) {
        for (String id : ids) target.put(id, type);
    }
}
