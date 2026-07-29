package xyz.zcraft.osu.parser;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import xyz.zcraft.osu.parser.data.beatmap.OsuBeatmap;
import xyz.zcraft.osu.parser.data.replay.HitEvent;
import xyz.zcraft.osu.parser.data.replay.OsuReplay;
import xyz.zcraft.osu.parser.data.replay.ReplayAnalyze;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static xyz.zcraft.osu.parser.BeatmapParser.parseBeatmap;
import static xyz.zcraft.osu.parser.Util.getRes;

public class ReplayAnalyzeTest {
    private static <T> void optionalAssertEquals(T expected, T actual) {
        if (expected != null) {
            assertEquals(expected, actual);
        }
    }

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

            testHitResults(analyze, testCase);

            System.out.println("Ok");
        }
    }

    private void testMeta(OsuReplay replay, TestCase testCase) {
        if (testCase.lazer()) {
            assertEquals(testCase.scoreId(), replay.replayInfo().onlineId());
        } else {
            assertEquals(testCase.scoreId(), replay.legacyScoreId());
        }
    }

    private void testHitResults(ReplayAnalyze analyze, TestCase testCase) {
        final HashMap<HitEvent.HitResult, Long> hitResults = new HashMap<>();
        Long sliderTicks = 0L;
        Long sliderEnds = 0L;
        Long spinnerSpins = 0L;
        Long spinnerBonuses = 0L;
        for (HitEvent event : analyze.events()) {
            switch (event.eventType()) {
                case HIT_CIRCLE, SLIDER_HEAD, SPINNER ->
                        hitResults.put(event.hitResult(), hitResults.getOrDefault(event.hitResult(), 0L) + 1);
                case SLIDER_TICK -> {
                    if (event.hitResult() == HitEvent.HitResult.PERFECT) sliderTicks++;
                }
                case SLIDER_END -> {
                    if (event.hitResult() == HitEvent.HitResult.PERFECT) sliderEnds++;
                }
                case SPINNER_SPIN -> {
                    if (event.hitResult() == HitEvent.HitResult.PERFECT) spinnerSpins++;
                }
                case SPINNER_BONUS -> {
                    if (event.hitResult() == HitEvent.HitResult.PERFECT) spinnerBonuses++;
                }
            }
        }

        assertEquals(testCase.expected().hitResults().ok(), hitResults.getOrDefault(HitEvent.HitResult.OK, 0L));
        assertEquals(testCase.expected().hitResults().meh(), hitResults.getOrDefault(HitEvent.HitResult.MEH, 0L));

        assertEquals(testCase.expected().hitResults().perfect(), hitResults.getOrDefault(HitEvent.HitResult.PERFECT, 0L));
        assertEquals(testCase.expected().hitResults().miss(), hitResults.getOrDefault(HitEvent.HitResult.MISS, 0L));

        optionalAssertEquals(testCase.expected().hitResults().sliderTick(), sliderTicks);
        optionalAssertEquals(testCase.expected().hitResults().sliderEnd(), sliderEnds);

        optionalAssertEquals(testCase.expected().hitResults().spinnerSpin(), spinnerSpins);
        optionalAssertEquals(testCase.expected().hitResults().spinnerBonus(), spinnerBonuses);
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
