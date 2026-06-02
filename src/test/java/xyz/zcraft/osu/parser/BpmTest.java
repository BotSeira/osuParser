package xyz.zcraft.osu.parser;

import module java.base;
import org.junit.jupiter.api.RepeatedTest;
import xyz.zcraft.osu.parser.data.beatmap.OsuBeatmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BpmTest {
    @RepeatedTest(3)
    void bpmTest() throws Exception {
        URL resourceUrl = getClass().getClassLoader().getResource("beatmaps/5198852.osu");
        assertNotNull(resourceUrl, "Test file not found!");

        Path beatmapPath = Path.of(resourceUrl.toURI());

        final OsuBeatmap osuBeatmap = BeatmapParser.parseBeatmap(beatmapPath);

        final Long beatmapId = osuBeatmap.getBeatmapId();
        assertEquals(5198852, beatmapId);

        final double calculatedBpm = BeatmapAnalyzer.calculateBpm(osuBeatmap);
        assertEquals(140.0, calculatedBpm, 0.01);
    }
}
