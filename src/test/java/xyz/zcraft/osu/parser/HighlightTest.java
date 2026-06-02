package xyz.zcraft.osu.parser;

import module java.base;
import org.junit.jupiter.api.Test;
import xyz.zcraft.osu.parser.data.beatmap.OsuBeatmap;
import xyz.zcraft.osu.parser.data.replay.OsuReplay;
import xyz.zcraft.osu.parser.data.replay.ReplayAnalyze;
import xyz.zcraft.osu.parser.data.replay.WdPerform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static xyz.zcraft.osu.parser.BeatmapParser.parseBeatmap;
import static xyz.zcraft.osu.parser.Util.getRes;

public class HighlightTest {
    @Test
    void highlightTest() throws Exception {
        Path beatmapPath = getRes("beatmaps/5198852.osu");
        Path replayPath = getRes("replays/solo-replay-osu_5198852_6610582386.osr");

        final OsuReplay replay = ReplayParser.parseReplay(replayPath);
        final OsuBeatmap beatmap = parseBeatmap(beatmapPath);

        final ReplayAnalyze analyze = ReplayAnalyzer.analyze(beatmap, replay);

        final WdPerform highlight = OsuParser.getHighlight(analyze);

        assertEquals("Pokkan Color", beatmap.getTitle());
        assertEquals("Super Extra Color", beatmap.getVersion());

        assertEquals(50004, highlight.startTime());
        assertEquals(70004, highlight.endTime());
        assertEquals(171.91458, highlight.beatmapPp(), 0.1);
        assertEquals(0.94, highlight.accuracy(), 0.1);
        assertEquals(0.94, highlight.accuracy(), 0.1);
        assertEquals(145.67, highlight.wdScore(), 0.1);

        System.out.println(highlight);
    }
}