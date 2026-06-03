package xyz.zcraft.osu.parser;

import module java.base;
import org.junit.jupiter.api.RepeatedTest;
import xyz.zcraft.osu.parser.data.beatmap.OsuBeatmap;
import xyz.zcraft.osu.parser.data.replay.OsuReplay;
import xyz.zcraft.osu.parser.data.replay.ReplayAnalyze;
import xyz.zcraft.osu.parser.data.replay.WdPerform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static xyz.zcraft.osu.parser.BeatmapParser.parseBeatmap;
import static xyz.zcraft.osu.parser.Util.getRes;

public class HighlightTest {
    @RepeatedTest(1)
    void highlightTest() throws Exception {
        Path beatmapPath = getRes("beatmaps/5198852.osu");
        Path replayPath = getRes("replays/solo-replay-osu_5198852_6610582386.osr");

        final OsuReplay replay = ReplayParser.parseReplay(replayPath);
        final OsuBeatmap beatmap = parseBeatmap(beatmapPath);

        final ReplayAnalyze analyze = ReplayAnalyzer.analyze(beatmap, replay);

        final WdPerform highlight = OsuParser.getHighlight(analyze);

        assertEquals(5198852, beatmap.getBeatmapId());

        assertEquals(50540, highlight.startTime());
        assertEquals(70540, highlight.endTime());
        assertEquals(171.193, highlight.beatmapPp(), 0.01);
        assertEquals(0.946, highlight.accuracy(), 0.01);
        assertEquals(145.039, highlight.wdScore(), 0.01);
    }
}