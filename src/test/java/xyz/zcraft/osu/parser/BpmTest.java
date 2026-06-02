package xyz.zcraft.osu.parser;

import module java.base;
import org.junit.jupiter.api.Test;
import xyz.zcraft.osu.parser.data.beatmap.OsuBeatmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BpmTest {
    @Test
    void bpmTest() throws Exception {
        URL resourceUrl = getClass().getClassLoader().getResource("beatmaps/5198852.osu");
        assertNotNull(resourceUrl, "Test file not found!");

        Path beatmapPath = Path.of(resourceUrl.toURI());

        final OsuBeatmap osuBeatmap = BeatmapParser.parseBeatmap(beatmapPath);

        final Long beatmapId = osuBeatmap.getBeatmapId();
        assertEquals(5198852, beatmapId, "BeatmapSetID does not match expected value!");

        final double calculatedBpm = BeatmapAnalyzer.calculateBpm(osuBeatmap, 0);
        assertEquals(140.0, calculatedBpm, 0.01, "Calculated BPM does not match expected value!");
    }
}
