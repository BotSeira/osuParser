package xyz.zcraft.osu.parser.data.beatmap;

import lombok.Data;

import java.awt.*;
import java.util.LinkedList;
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

    // [Editor]
    private List<Long> bookmarks;
    private double distanceSpacing;
    private int beatDivisor;
    private int gridSize;
    private double timelineZoom;

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

    // [Events]
    // Ignoring for now...

    // [TimingPoints]
    private List<TimingPoint> timingPoints;

    // [Colours]
    private List<Color> colours;

    // [HitObjects]
    private List<HitObject> hitObjects;

    public OsuBeatmap() {
        timingPoints = new LinkedList<>();
        colours = new LinkedList<>();
        hitObjects = new LinkedList<>();
    }

    public record TimingPoint(
            long time,
            double beatLength,
            int meter,
            int sampleSet,
            int sampleIndex,
            int volume,
            int uninherited,
            int effects
    ) {
        public String toTimingPointLine() {
            return String.format("%d,%f,%d,%d,%d,%d,%d,%d",
                    time, beatLength, meter, sampleSet, sampleIndex, volume, uninherited, effects);
        }
    }

    private String getHeadersString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("osu file format v14").append("\n");
        sb.append("\n");

        sb.append("[General]").append("\n");
        sb.append("AudioFilename: ").append(audioFileName).append("\n");
        sb.append("AudioLeadIn: ").append(audioLeadIn).append("\n");
        sb.append("PreviewTime: ").append(previewTime).append("\n");
        sb.append("Countdown: ").append(countdown).append("\n");
        sb.append("SampleSet: ").append(sampleSet).append("\n");
        sb.append("StackLeniency: ").append(stackLeniency).append("\n");
        sb.append("Mode: ").append(mode).append("\n");
        sb.append("LetterboxInBreaks: ").append(letterboxInBreaks).append("\n");
        sb.append("WidescreenStoryboard: ").append(widescreenStoryboard).append("\n");
        sb.append("\n");

        sb.append("[Editor]").append("\n");
        sb.append("DistanceSpacing: ").append(distanceSpacing).append("\n");
        sb.append("BeatDivisor: ").append(beatDivisor).append("\n");
        sb.append("GridSize: ").append(gridSize).append("\n");
        sb.append("TimelineZoom: ").append(timelineZoom).append("\n");
        sb.append("\n");

        sb.append("[Metadata]").append("\n");
        sb.append("Title: ").append(title).append("\n");
        sb.append("TitleUnicode: ").append(titleUnicode).append("\n");
        sb.append("Artist: ").append(artist).append("\n");
        sb.append("ArtistUnicode: ").append(artistUnicode).append("\n");
        sb.append("Creator: ").append(creator).append("\n");
        sb.append("Version: ").append(version).append("\n");
        sb.append("Source: ").append(source).append("\n");
        sb.append("Tags: ").append(tags).append("\n");
        sb.append("BeatmapID: ").append(beatmapId).append("\n");
        sb.append("BeatmapSetID: ").append(beatmapSetId).append("\n");
        sb.append("\n");

        sb.append("[Difficulty]").append("\n");
        sb.append("HPDrainRate: ").append(hp).append("\n");
        sb.append("CircleSize: ").append(cs).append("\n");
        sb.append("OverallDifficulty: ").append(od).append("\n");
        sb.append("ApproachRate: ").append(ar).append("\n");
        sb.append("SliderMultiplier: ").append(sliderMultiplier).append("\n");
        sb.append("SliderTickRate: ").append(sliderTickRate).append("\n");
        sb.append("\n");

        sb.append("[Events]").append("\n");
        sb.append("//Background and Video events").append("\n");
        sb.append("//Break Periods").append("\n");
        sb.append("//Storyboard Layer 0 (Background)").append("\n");
        sb.append("//Storyboard Layer 1 (Fail)").append("\n");
        sb.append("//Storyboard Layer 2 (Pass)").append("\n");
        sb.append("//Storyboard Layer 3 (Foreground)").append("\n");
        sb.append("//Storyboard Sound Samples").append("\n");
        sb.append("\n");

        sb.append("[TimingPoints]").append("\n");
        for (TimingPoint timingPoint : timingPoints) {
            sb.append(timingPoint.toTimingPointLine()).append("\n");
        }
        sb.append("\n");

        sb.append("[Colours]").append("\n");
        for (int i = 0, coloursSize = colours.size(); i < coloursSize; i++) {
            var v = colours.get(i);
            sb.append("Color").append(i + 1).append(" : ").append("%d,%d,%d".formatted(v.getRed(), v.getGreen(), v.getBlue())).append("\n");
        }
        sb.append("\n");

        return sb.toString();
    }

    public String toBeatmapString() {
        StringBuilder sb = new StringBuilder();

        sb.append(getHeadersString());

        sb.append("[HitObjects]").append("\n");
        for (HitObject hitObject : hitObjects) {
            sb.append(hitObject.getRawData()).append("\n");
        }
        sb.append("\n");

        return sb.toString();
    }

    public String toWindowedBeatmapString(long startTimeMs, long endTimeMs) {
        StringBuilder sb = new StringBuilder();
        sb.append(getHeadersString());

        sb.append("[HitObjects]\n");
        for (HitObject obj : this.hitObjects) {
            if (obj.getTime() >= startTimeMs && obj.getTime() <= endTimeMs) {
                sb.append(obj.getRawData()).append("\n");
            }
        }

        return sb.toString();
    }
}
