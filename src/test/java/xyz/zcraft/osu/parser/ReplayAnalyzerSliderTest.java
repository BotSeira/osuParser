package xyz.zcraft.osu.parser;

import org.junit.jupiter.api.Test;
import xyz.zcraft.osu.parser.data.beatmap.HitObject;
import xyz.zcraft.osu.parser.data.beatmap.OsuBeatmap;
import xyz.zcraft.osu.parser.data.replay.HitEvent;
import xyz.zcraft.osu.parser.data.replay.OsuReplay;
import xyz.zcraft.osu.parser.data.replay.ReplayAnalyze;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayAnalyzerSliderTest {
    @Test
    void emitsHitEventsForSliderTicksAndEnd() throws Exception {
        OsuBeatmap beatmap = beatmapWithLinearSlider();
        OsuReplay replay = replay(beatmap.getHash(), List.of(
                frame(900, 100, 100, 0),
                frame(1000, 100, 100, 1),
                frame(2000, 200, 100, 1),
                frame(2964, 296.4f, 100, 1),
                frame(3100, 300, 100, 0)
        ));

        ReplayAnalyze analyze = ReplayAnalyzer.analyze(beatmap, replay);

        assertEquals(List.of(
                        HitEvent.EventType.SLIDER_HEAD,
                        HitEvent.EventType.SLIDER_TICK,
                        HitEvent.EventType.SLIDER_END),
                analyze.events().stream().map(HitEvent::eventType).toList());
        assertEquals(List.of(1000L, 2000L, 3000L),
                analyze.events().stream().map(HitEvent::eventTime).toList());
        assertTrue(analyze.events().stream().allMatch(HitEvent::wasHit));
        assertEquals(2964, analyze.events().getLast().hitTime());

        // Passive slider judgements do not pollute tap timing statistics.
        assertEquals(0, analyze.unstableRate());
        assertEquals(1, ReplayAnalyzer.calculateWindowAccuracy(analyze, 0, 4000));
    }

    @Test
    void marksTickAndEndMissedWhenSliderIsReleased() throws Exception {
        OsuBeatmap beatmap = beatmapWithLinearSlider();
        OsuReplay replay = replay(beatmap.getHash(), List.of(
                frame(900, 100, 100, 0),
                frame(1000, 100, 100, 1),
                frame(1500, 150, 100, 0),
                frame(2000, 200, 100, 0),
                frame(3000, 300, 100, 0)
        ));

        List<HitEvent> events = ReplayAnalyzer.analyze(beatmap, replay).events();

        assertTrue(events.getFirst().wasHit());
        assertEquals(HitEvent.HitResult.MISS, events.get(1).hitResult());
        assertEquals(HitEvent.HitResult.MISS, events.get(2).hitResult());
    }

    @Test
    void appliesInheritedVelocityAndEmitsRepeatsAsSliderTicks() throws Exception {
        OsuBeatmap beatmap = beatmapWithLinearSlider();
        beatmap.getTimingPoints().add(new OsuBeatmap.TimingPoint(500, -50, 4, 0, 0, 100, 0, 0));
        beatmap.setSliderTickRate(2.0);
        beatmap.getHitObjects().getFirst().setSlides(2);
        OsuReplay replay = replay(beatmap.getHash(), List.of(
                frame(900, 100, 100, 0),
                frame(1000, 100, 100, 1),
                frame(1500, 200, 100, 1),
                frame(2000, 300, 100, 1),
                frame(2500, 200, 100, 1),
                frame(2964, 107.2f, 100, 1),
                frame(3100, 100, 100, 0)
        ));

        List<HitEvent> events = ReplayAnalyzer.analyze(beatmap, replay).events();

        assertEquals(List.of(
                        HitEvent.EventType.SLIDER_HEAD,
                        HitEvent.EventType.SLIDER_TICK,
                        HitEvent.EventType.SLIDER_TICK,
                        HitEvent.EventType.SLIDER_TICK,
                        HitEvent.EventType.SLIDER_END),
                events.stream().map(HitEvent::eventType).toList());
        assertEquals(List.of(1000L, 1500L, 2000L, 2500L, 3000L),
                events.stream().map(HitEvent::eventTime).toList());
        assertTrue(events.stream().allMatch(HitEvent::wasHit));
    }

    private static OsuBeatmap beatmapWithLinearSlider() {
        OsuBeatmap beatmap = new OsuBeatmap();
        beatmap.setHash("slider-test");
        beatmap.setCs(5.0);
        beatmap.setOd(5.0);
        beatmap.setAr(5.0);
        beatmap.setHp(5.0);
        beatmap.setSliderMultiplier(1.0);
        beatmap.setSliderTickRate(1.0);
        beatmap.getTimingPoints().add(new OsuBeatmap.TimingPoint(0, 1000, 4, 0, 0, 100, 1, 0));

        HitObject slider = new HitObject();
        slider.setX(100);
        slider.setY(100);
        slider.setTime(1000);
        slider.setObjectType(HitObject.ObjectType.SLIDER);
        slider.setCurveType("L");
        slider.getControlPoints().add(new HitObject.ControlPoint(300, 100));
        slider.setSlides(1);
        slider.setLength(200);
        beatmap.getHitObjects().add(slider);
        return beatmap;
    }

    private static OsuReplay replay(String hash, List<OsuReplay.TimedKeyFrame> frames) {
        return new OsuReplay((byte) 0, 0, hash, "player", "replay", (short) 1,
                (short) 0, (short) 0, (short) 0, (short) 0, (short) 0,
                300, (short) 1, true, 0, "", 0, frames);
    }

    private static OsuReplay.TimedKeyFrame frame(long time, float x, float y, int key) {
        return new OsuReplay.TimedKeyFrame(time, 0, x, y, key);
    }
}
