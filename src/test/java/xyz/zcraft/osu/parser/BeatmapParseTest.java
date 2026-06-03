package xyz.zcraft.osu.parser;

import module java.base;
import org.junit.jupiter.api.Test;
import xyz.zcraft.osu.parser.data.beatmap.OsuBeatmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static xyz.zcraft.osu.parser.Util.getRes;

public class BeatmapParseTest {
    @Test
    void bpmTest() throws Exception {
        Path beatmapPath = getRes("beatmaps/5198852.osu");

        final OsuBeatmap osuBeatmap = BeatmapParser.parseBeatmap(beatmapPath);

        final Long beatmapId = osuBeatmap.getBeatmapId();
        assertEquals(5198852, beatmapId);

        final double calculatedBpm = BeatmapAnalyzer.calculateBpm(osuBeatmap);
        assertEquals(140.0, calculatedBpm, 0.01);
    }

    @Test
    void lengthTest() throws Exception {
        Path beatmapPath = getRes("beatmaps/1056889.osu");

        final OsuBeatmap osuBeatmap = BeatmapParser.parseBeatmap(beatmapPath);

        final Long beatmapId = osuBeatmap.getBeatmapId();
        assertEquals(1056889, beatmapId);

        final var len = BeatmapAnalyzer.calculateLengths(osuBeatmap);
        final Integer totalLength = len.getKey();
        final Integer hitLength = len.getValue();

        assertEquals(78, totalLength, 1);
        assertEquals(76, hitLength, 1);
    }
}
