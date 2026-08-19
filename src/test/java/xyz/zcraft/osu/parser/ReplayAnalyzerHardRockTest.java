package xyz.zcraft.osu.parser;

import org.junit.jupiter.api.Test;
import xyz.zcraft.osu.model.Mod;
import xyz.zcraft.osu.parser.data.beatmap.HitObject;
import xyz.zcraft.osu.parser.data.beatmap.OsuBeatmap;
import xyz.zcraft.osu.parser.data.replay.HitEvent;
import xyz.zcraft.osu.parser.data.replay.OsuReplay;
import xyz.zcraft.osu.parser.data.replay.ReplayAnalyze;
import xyz.zcraft.osu.parser.data.replay.ReplayInfo;
import xyz.zcraft.osu.parser.exception.ParseException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayAnalyzerHardRockTest {
    private static final int HARD_ROCK = 16;

    @Test
    void mirrorsCirclePositionsAndApproachDirection() throws Exception {
        OsuBeatmap beatmap = beatmap("hr-circles");
        beatmap.getHitObjects().add(circle(100, 100, 1000));
        beatmap.getHitObjects().add(circle(100, 200, 2000));
        OsuReplay replay = replay(beatmap.getHash(), HARD_ROCK, null, List.of(
                frame(900, 100, 284, 0),
                frame(1000, 100, 284, 1),
                frame(1100, 100, 284, 0),
                frame(1900, 100, 184, 0),
                frame(2000, 100, 184, 1)
        ));

        List<HitEvent> events = ReplayAnalyzer.analyze(beatmap, replay).events();

        assertTrue(events.stream().allMatch(HitEvent::wasHit));
        assertEquals(0, events.getFirst().aimBias().distance(), 1e-7);
        assertEquals(0, events.getLast().aimBias().distance(), 1e-7);
        assertEquals(-Math.PI / 2, events.getLast().aimBias().angleFromLast(), 1e-7);
    }

    @Test
    void mirrorsCompleteSliderPath() throws Exception {
        OsuBeatmap beatmap = beatmap("hr-slider");
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

        OsuReplay replay = replay(beatmap.getHash(), HARD_ROCK, null, List.of(
                frame(900, 100, 284, 0),
                frame(1000, 100, 284, 1),
                frame(2000, 200, 284, 1),
                frame(2964, 296.4f, 284, 1),
                frame(3100, 300, 284, 0)
        ));

        List<HitEvent> events = ReplayAnalyzer.analyze(beatmap, replay).events();

        assertEquals(List.of(HitEvent.EventType.SLIDER_HEAD,
                        HitEvent.EventType.SLIDER_TICK, HitEvent.EventType.SLIDER_END),
                events.stream().map(HitEvent::eventType).toList());
        assertTrue(events.stream().allMatch(HitEvent::wasHit));
        assertTrue(events.stream().allMatch(event -> event.aimBias().distance() < 4));
    }

    @Test
    void appliesStackOffsetToMirroredPosition() throws Exception {
        OsuBeatmap beatmap = beatmap("hr-stack");
        beatmap.setStackLeniency(0.7);
        beatmap.getHitObjects().add(circle(100, 100, 1000));
        beatmap.getHitObjects().add(circle(100, 100, 1100));

        ReplayAnalyze preliminary = ReplayAnalyzer.analyze(beatmap,
                replay(beatmap.getHash(), HARD_ROCK, null, List.of(
                        frame(1000, 100, 284, 0), frame(1200, 100, 284, 0))));
        double cs = preliminary.calculatedDifficulty().cs();
        double stackOffset = (1 - 0.7 * (cs - 5) / 5) / 2 * 6.4;

        OsuReplay replay = replay(beatmap.getHash(), HARD_ROCK, null, List.of(
                frame(900, 100 - stackOffset, 284 - stackOffset, 0),
                frame(1000, 100 - stackOffset, 284 - stackOffset, 1),
                frame(1050, 100, 284, 0),
                frame(1100, 100, 284, 1)
        ));

        List<HitEvent> events = ReplayAnalyzer.analyze(beatmap, replay).events();

        assertEquals(0, events.getFirst().aimBias().distance(), 1e-4);
        assertEquals(0, events.getLast().aimBias().distance(), 1e-7);
    }

    @Test
    void readsHardRockFromLazerReplayMetadata() throws Exception {
        OsuBeatmap beatmap = beatmap("lazer-hr");
        beatmap.getHitObjects().add(circle(100, 100, 1000));
        Mod hardRock = new Mod();
        hardRock.setAcronym("HR");
        ReplayInfo replayInfo = new ReplayInfo("2026.811.0", "A", 1L, 2L,
                List.of(hardRock), null, null, null, null);
        OsuReplay replay = replay(beatmap.getHash(), 0, replayInfo, List.of(
                frame(900, 100, 284, 0),
                frame(1000, 100, 284, 1)
        ));

        assertTrue(ReplayAnalyzer.hasHardRock(replay));
        assertTrue(ReplayAnalyzer.analyze(beatmap, replay).events().getFirst().wasHit());
    }

    @Test
    void rejectsNullInputsWithParseException() {
        OsuBeatmap beatmap = beatmap("null-input");
        OsuReplay replay = replay(beatmap.getHash(), 0, null,
                List.of(frame(0, 0, 0, 0)));

        assertThrows(ParseException.class, () -> ReplayAnalyzer.analyze(null, replay));
        assertThrows(ParseException.class, () -> ReplayAnalyzer.analyze(beatmap, null));
    }

    private static OsuBeatmap beatmap(String hash) {
        OsuBeatmap beatmap = new OsuBeatmap();
        beatmap.setHash(hash);
        beatmap.setCs(5.0);
        beatmap.setOd(5.0);
        beatmap.setAr(5.0);
        beatmap.setHp(5.0);
        return beatmap;
    }

    private static HitObject circle(int x, int y, int time) {
        HitObject circle = new HitObject();
        circle.setX(x);
        circle.setY(y);
        circle.setTime(time);
        circle.setObjectType(HitObject.ObjectType.HIT_CIRCLE);
        return circle;
    }

    private static OsuReplay replay(String hash, int mods, ReplayInfo replayInfo,
                                    List<OsuReplay.TimedKeyFrame> frames) {
        return new OsuReplay((byte) 0, 30000001, hash, "player", "replay",
                (short) 0, (short) 0, (short) 0, (short) 0, (short) 0, (short) 0,
                0, (short) 0, false, mods, "", 0, frames, 0, replayInfo);
    }

    private static OsuReplay.TimedKeyFrame frame(long time, double x, double y, int key) {
        return new OsuReplay.TimedKeyFrame(time, 0, (float) x, (float) y, key);
    }
}
