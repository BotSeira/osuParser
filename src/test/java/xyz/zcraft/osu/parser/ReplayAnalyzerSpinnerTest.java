package xyz.zcraft.osu.parser;

import org.junit.jupiter.api.Test;
import xyz.zcraft.osu.parser.data.beatmap.HitObject;
import xyz.zcraft.osu.parser.data.beatmap.OsuBeatmap;
import xyz.zcraft.osu.parser.data.replay.HitEvent;
import xyz.zcraft.osu.parser.data.replay.OsuReplay;
import xyz.zcraft.osu.parser.data.replay.ReplayAnalyze;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayAnalyzerSpinnerTest {
    private static final long START_TIME = 1000;
    private static final long END_TIME = 3200;

    @Test
    void countsSpinsAndBonusesInRecordedReplay() throws Exception {
        OsuBeatmap beatmap = BeatmapParser.parseBeatmap(Util.getRes("beatmaps/5198852.osu"));
        OsuReplay replay = ReplayParser.parseReplay(Util.getRes(
                "replays/solo-replay-osu_5198852_6610582386.osr"));
        List<HitEvent> events = ReplayAnalyzer.analyze(beatmap, replay).events();

        assertEquals(3, events.stream()
                .filter(event -> event.eventType() == HitEvent.EventType.SPINNER)
                .filter(HitEvent::wasHit)
                .count());
        assertEquals(52, events.stream()
                .filter(event -> event.eventType() == HitEvent.EventType.SPINNER_SPIN)
                .filter(HitEvent::wasHit)
                .count());
        assertEquals(33, events.stream()
                .filter(event -> event.eventType() == HitEvent.EventType.SPINNER_BONUS)
                .filter(HitEvent::wasHit)
                .count());
    }

    @Test
    void parsesSpinnerHitResultsFromCompletedRotations() throws Exception {
        assertSpinnerResult(6, HitEvent.HitResult.PERFECT);
        assertSpinnerResult(5, HitEvent.HitResult.PERFECT);
        assertSpinnerResult(4.75, HitEvent.HitResult.OK);
        assertSpinnerResult(4, HitEvent.HitResult.MEH);
        assertSpinnerResult(3.5, HitEvent.HitResult.MISS);
    }

    @Test
    void ignoresSpinnerMovementWhileNoKeyIsHeld() throws Exception {
        ReplayAnalyze analyze = ReplayAnalyzer.analyze(beatmap(), replay(spinFrames(6, 0), 0));

        HitEvent spinner = spinnerJudgement(analyze);
        assertEquals(HitEvent.HitResult.MISS, spinner.hitResult());
        assertFalse(spinner.wasHit());
        assertEquals(7, analyze.events().stream()
                .filter(event -> event.eventType() == HitEvent.EventType.SPINNER_SPIN)
                .filter(event -> event.hitResult() == HitEvent.HitResult.MISS)
                .count());
    }

    @Test
    void spunOutCompletesSpinnerWithoutCursorRotation() throws Exception {
        ReplayAnalyze analyze = ReplayAnalyzer.analyze(beatmap(), replay(List.of(
                frame(START_TIME, 356, 192, 0),
                frame(END_TIME, 356, 192, 0)
        ), 4096));

        HitEvent spinner = spinnerJudgement(analyze);
        assertEquals(HitEvent.HitResult.PERFECT, spinner.hitResult());
        assertTrue(spinner.wasHit());
        assertEquals(7, analyze.events().stream()
                .filter(event -> event.eventType() == HitEvent.EventType.SPINNER_SPIN)
                .count());
        assertEquals(6, analyze.events().stream()
                .filter(event -> event.eventType() == HitEvent.EventType.SPINNER_BONUS)
                .count());
        assertEquals(3, analyze.events().stream()
                .filter(event -> event.eventType() == HitEvent.EventType.SPINNER_BONUS)
                .filter(HitEvent::wasHit)
                .count());
    }

    @Test
    void clockRateChangesSpinnerDurationWithoutChangingItsOd() throws Exception {
        ReplayAnalyze analyze = ReplayAnalyzer.analyze(beatmap(), replay(spinFrames(4, 1), 64));

        assertEquals(HitEvent.HitResult.PERFECT, spinnerJudgement(analyze).hitResult());
    }

    @Test
    void emitsSpinAndBonusResults() throws Exception {
        ReplayAnalyze analyze = ReplayAnalyzer.analyze(beatmap(), replay(spinFrames(9, 1), 0));

        assertEquals(7, analyze.events().stream()
                .filter(event -> event.eventType() == HitEvent.EventType.SPINNER_SPIN)
                .filter(HitEvent::wasHit)
                .count());
        assertEquals(1, analyze.events().stream()
                .filter(event -> event.eventType() == HitEvent.EventType.SPINNER_BONUS)
                .filter(HitEvent::wasHit)
                .count());
    }

    @Test
    void emitsMissesForUncompletedRequiredSpins() throws Exception {
        ReplayAnalyze analyze = ReplayAnalyzer.analyze(beatmap(), replay(spinFrames(1, 1), 0));

        List<HitEvent> spins = analyze.events().stream()
                .filter(event -> event.eventType() == HitEvent.EventType.SPINNER_SPIN)
                .toList();
        assertEquals(7, spins.size());
        assertEquals(1, spins.stream().filter(HitEvent::wasHit).count());
        assertEquals(6, spins.stream().filter(event -> !event.wasHit()).count());
    }

    private static void assertSpinnerResult(double rotations, HitEvent.HitResult expected) throws Exception {
        ReplayAnalyze analyze = ReplayAnalyzer.analyze(beatmap(), replay(spinFrames(rotations, 1), 0));

        HitEvent spinner = spinnerJudgement(analyze);
        assertEquals(HitEvent.EventType.SPINNER, spinner.eventType());
        assertEquals(START_TIME, spinner.eventTime());
        assertEquals(END_TIME, spinner.hitTime());
        assertEquals(expected, spinner.hitResult());
        assertEquals(expected != HitEvent.HitResult.MISS, spinner.wasHit());
    }

    private static HitEvent spinnerJudgement(ReplayAnalyze analyze) {
        return analyze.events().stream()
                .filter(event -> event.eventType() == HitEvent.EventType.SPINNER)
                .findFirst()
                .orElseThrow();
    }

    private static OsuBeatmap beatmap() {
        OsuBeatmap beatmap = new OsuBeatmap();
        beatmap.setHash("spinner-test");
        beatmap.setCs(5.0);
        beatmap.setOd(5.0);
        beatmap.setAr(5.0);
        beatmap.setHp(5.0);

        HitObject spinner = new HitObject();
        spinner.setX(256);
        spinner.setY(192);
        spinner.setTime(START_TIME);
        spinner.setEndTime((int) END_TIME);
        spinner.setObjectType(HitObject.ObjectType.SPINNER);
        beatmap.getHitObjects().add(spinner);
        return beatmap;
    }

    private static OsuReplay replay(List<OsuReplay.TimedKeyFrame> frames, int mods) {
        return new OsuReplay((byte) 0, 20250701, "spinner-test", "player", "replay",
                (short) 0, (short) 0, (short) 0, (short) 0, (short) 0, (short) 0,
                0, (short) 0, false, mods, "", 0, frames);
    }

    private static List<OsuReplay.TimedKeyFrame> spinFrames(double rotations, int key) {
        int steps = Math.max(1, (int) Math.ceil(rotations * 16));
        List<OsuReplay.TimedKeyFrame> frames = new ArrayList<>(steps + 1);
        for (int i = 0; i <= steps; i++) {
            double progress = (double) i / steps;
            double angle = rotations * Math.PI * 2 * progress;
            long time = START_TIME + Math.round((END_TIME - START_TIME) * progress);
            frames.add(frame(time, 256 + 100 * Math.cos(angle), 192 + 100 * Math.sin(angle), key));
        }
        return frames;
    }

    private static OsuReplay.TimedKeyFrame frame(long time, double x, double y, int key) {
        return new OsuReplay.TimedKeyFrame(time, 0, (float) x, (float) y, key);
    }
}
