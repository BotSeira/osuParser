package xyz.zcraft.osu.parser.data;

import lombok.Data;

import java.util.List;

@Data
public class OsuBeatmap {
    private String hash;

    // [General]
    private String audioFileName;
    private long audioLeadIn;
    private long previewTime;
    private long countdown;
    private String sampleSet;
    private double stackLeniency;
    private int mode;
    private int letterboxInBreaks;
    private int widescreenStoryboard;

    // [Metadata]
    private String title;
    private String titleUnicode;
    private String artist;
    private String artistUnicode;
    private String creator;
    private String version;
    private String source;
    private String tags;
    private long beatmapId;
    private long beatmapSetId;

    // [Difficulty]
    private double hp;
    private double cs;
    private double ar;
    private double od;
    private double sliderMultiplier;
    private double sliderTickRate;

    // [HitObjects]
    private List<HitObject> hitObjects;
}
