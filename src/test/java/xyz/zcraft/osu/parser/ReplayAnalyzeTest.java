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

            final ReplayAnalyze analyze = ReplayAnalyzer.analyze(beatmap, replay);

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

            if (Boolean.getBoolean("replayAnalyze.diagnostic")) {
                System.out.printf(" results=%s ticks=%d ends=%d spins=%d bonuses=%d%n",
                        hitResults, sliderTicks, sliderEnds, spinnerSpins, spinnerBonuses);
                HashMap<HitEvent.HitResult, Long> sliderAdjusted = new HashMap<>(hitResults);
                for (HitEvent head : analyze.events()) {
                    if (head.eventType() != HitEvent.EventType.SLIDER_HEAD) continue;
                    List<HitEvent> sliderEvents = analyze.events().stream()
                            .filter(event -> event.objectIndex() == head.objectIndex())
                            .toList();
                    long hitParts = sliderEvents.stream().filter(HitEvent::wasHit).count();
                    HitEvent.HitResult finalResult = hitParts == sliderEvents.size()
                            ? HitEvent.HitResult.PERFECT
                            : hitParts * 2 >= sliderEvents.size()
                            ? HitEvent.HitResult.OK
                            : hitParts > 0 ? HitEvent.HitResult.MEH : HitEvent.HitResult.MISS;
                    sliderAdjusted.merge(head.hitResult(), -1L, Long::sum);
                    sliderAdjusted.merge(finalResult, 1L, Long::sum);
                }
                System.out.printf(" slider-adjusted=%s%n", sliderAdjusted);
                System.out.printf(" slider-heads=%s%n", analyze.events().stream()
                        .filter(event -> event.eventType() == HitEvent.EventType.SLIDER_HEAD)
                        .collect(java.util.stream.Collectors.groupingBy(HitEvent::hitResult,
                                java.util.stream.Collectors.counting())));
                continue;
            }

            assertEquals(testCase.expected().hitResults().ok(), hitResults.getOrDefault(HitEvent.HitResult.OK, 0L));
            assertEquals(testCase.expected().hitResults().meh(), hitResults.getOrDefault(HitEvent.HitResult.MEH, 0L));

            assertEquals(testCase.expected().hitResults().perfect(), hitResults.getOrDefault(HitEvent.HitResult.PERFECT, 0L));
            assertEquals(testCase.expected().hitResults().miss(), hitResults.getOrDefault(HitEvent.HitResult.MISS, 0L));

            assertEquals(testCase.expected().hitResults().sliderTick(), sliderTicks);
            assertEquals(testCase.expected().hitResults().sliderEnd(), sliderEnds);

            assertEquals(testCase.expected().hitResults().spinnerSpin(), spinnerSpins);
            assertEquals(testCase.expected().hitResults().spinnerBonus(), spinnerBonuses);

            System.out.println("Ok");
        }
    }

    public record TestCase(String name,
                           String beatmap,
                           String replay,
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
