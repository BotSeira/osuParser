package xyz.zcraft.osu.parser;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import xyz.zcraft.osu.parser.data.beatmap.OsuBeatmap;
import xyz.zcraft.osu.parser.data.replay.OsuReplay;
import xyz.zcraft.osu.parser.data.replay.ReplayAnalyze;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static xyz.zcraft.osu.parser.BeatmapParser.parseBeatmap;
import static xyz.zcraft.osu.parser.Util.getRes;

public class ReplayAnalyzeURTest {

    @Test
    void replayAnalyzeTest() throws Exception {
        final JsonObject testRoot = JsonParser.parseString(Files.readString(getRes("beatmap-replay-tests.json"))).getAsJsonObject();
        final JsonArray casesRoot = testRoot.get("testCases").getAsJsonArray();

        for (JsonElement elem : casesRoot) {
            final TestCase testCase = new Gson().fromJson(elem, TestCase.class);

            System.out.print("Running case: " + testCase.name() + "...");

            Path beatmapPath = getRes("beatmaps" + "/" + testCase.beatmap());
            Path replayPath = getRes("replays" + "/" + testCase.replay());

            final OsuReplay replay = ReplayParser.parseReplay(replayPath);
            final OsuBeatmap beatmap = parseBeatmap(beatmapPath);

            testMeta(replay, testCase);

            final ReplayAnalyze analyze = ReplayAnalyzer.analyze(beatmap, replay);

            System.out.println("UR=" + analyze.unstableRate() + " AimUR=" + analyze.aimUnstableRate());
        }
    }

    private void testMeta(OsuReplay replay, TestCase testCase) {
        if (testCase.lazer()) {
            assertEquals(testCase.scoreId(), replay.replayInfo().onlineId());
        } else {
            assertEquals(testCase.scoreId(), replay.legacyScoreId());
        }
    }

    public record TestCase(String name,
                           String beatmap,
                           String replay,
                           boolean lazer,
                           long scoreId,
                           ExpectedResult expected) {
    }

    public record ExpectedResult(Integer score,
                                 Integer maxCombo,
                                 Double accuracy,
                                 Double pp,
                                 List<String> mods,
                                 ExpectedHitResults hitResults) {
    }

    public record ExpectedHitResults(Long perfect,
                                     Long ok,
                                     Long meh,
                                     Long miss,
                                     Long sliderTick,
                                     Long sliderEnd,
                                     Long spinnerBonus,
                                     Long spinnerSpin) {
    }
}
