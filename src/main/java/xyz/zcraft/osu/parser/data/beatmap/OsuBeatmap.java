package xyz.zcraft.osu.parser.data.beatmap;

import lombok.Data;
import lombok.Getter;

import java.awt.*;
import java.util.LinkedList;
import java.util.List;

@Data
public class OsuBeatmap {
    private String hash;
    private double bpm;
    private double hitLength;
    private double totalLength;

    // [General]
    private String audioFileName;
    private Long audioLeadIn;
    private Long previewTime;
    private Long countdown;
    private String sampleSet;
    private Double stackLeniency;
    private Integer mode;
    private Integer letterboxInBreaks;
    private Integer useSkinSprites;
    private String overlayPosition;
    private String skinPreference;
    private Integer epilepsyWarning;
    private Integer countdownOffset;
    private Integer specialStyle;
    private Integer widescreenStoryboard;
    private Integer samplesMatchPlaybackRate;

    // [Editor]
    private List<Long> bookmarks;
    private Double distanceSpacing;
    private Integer beatDivisor;
    private Integer gridSize;
    private Double timelineZoom;

    // [Metadata]
    private String title;
    private String titleUnicode;
    private String artist;
    private String artistUnicode;
    private String creator;
    private String version;
    private String source;
    private List<String> tags;
    private Long beatmapId;
    private Long beatmapSetId;

    // [Difficulty]
    private Double hp;
    private Double cs;
    private Double ar;
    private Double od;
    private Double sliderMultiplier;
    private Double sliderTickRate;

    // [Events]
    private List<Event> bgAndVideoEvents;
    private List<Event.BreakEvent> breakEvents;
    private List<Event.StoryboardEvent> storyBoardLayer0;
    private List<Event.StoryboardEvent> storyBoardLayer1;
    private List<Event.StoryboardEvent> storyBoardLayer2;
    private List<Event.StoryboardEvent> storyBoardLayer3;
    private List<Event.StoryboardEvent> storyBoardLayer4;
    private List<Event.StoryboardEvent> audioSampleEvents;

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
        bookmarks = new LinkedList<>();
        tags = new LinkedList<>();
        bgAndVideoEvents = new LinkedList<>();
        breakEvents = new LinkedList<>();
        storyBoardLayer0 = new LinkedList<>();
        storyBoardLayer1 = new LinkedList<>();
        storyBoardLayer2 = new LinkedList<>();
        storyBoardLayer3 = new LinkedList<>();
        storyBoardLayer4 = new LinkedList<>();
        audioSampleEvents =  new LinkedList<>();
    }

    private static void append(StringBuilder sb, String key, Object val) {
        if (val != null) sb.append(key).append(": ").append(val).append("\n");
    }

    private String getHeadersString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("osu file format v14").append("\n");
        sb.append("\n");
        sb.append("[General]").append("\n");
        append(sb, "AudioFilename", audioFileName);
        append(sb, "AudioLeadIn", audioLeadIn);
        append(sb, "PreviewTime", previewTime);
        append(sb, "Countdown", countdown);
        append(sb, "SampleSet", sampleSet);
        append(sb, "StackLeniency", stackLeniency);
        append(sb, "Mode", mode);
        append(sb, "LetterboxInBreaks", letterboxInBreaks);
        append(sb, "UseSkinSprites", useSkinSprites);
        append(sb, "OverlayPosition", overlayPosition);
        append(sb, "SkinPreference", skinPreference);
        append(sb, "EpilepsyWarning", epilepsyWarning);
        append(sb, "CountdownOffset", countdownOffset);
        append(sb, "SpecialStyle", specialStyle);
        append(sb, "WidescreenStoryboard", widescreenStoryboard);
        append(sb, "SamplesMatchPlaybackRate", samplesMatchPlaybackRate);
        sb.append("\n");

        sb.append("[Editor]").append("\n");
        append(sb, "Bookmarks", bookmarks.stream().map(String::valueOf).reduce((a,b)->a + "," + b).orElse(null));
        append(sb, "DistanceSpacing", distanceSpacing);
        append(sb, "BeatDivisor", beatDivisor);
        append(sb, "GridSize", gridSize);
        append(sb, "TimelineZoom", timelineZoom);
        sb.append("\n");

        sb.append("[Metadata]").append("\n");
        append(sb, "Title", title);
        append(sb, "TitleUnicode", titleUnicode);
        append(sb, "Artist", artist);
        append(sb, "ArtistUnicode", artistUnicode);
        append(sb, "Creator", creator);
        append(sb, "Version", version);
        append(sb, "Source", source);
        append(sb, "Tags", String.join(",", tags));
        append(sb, "BeatmapID", beatmapId);
        append(sb, "BeatmapSetID", beatmapSetId);
        sb.append("\n");

        sb.append("[Difficulty]").append("\n");
        append(sb, "HPDrainRate", hp);
        append(sb, "CircleSize", cs);
        append(sb, "OverallDifficulty", od);
        append(sb, "ApproachRate", ar);
        append(sb, "SliderMultiplier", sliderMultiplier);
        append(sb, "SliderTickRate", sliderTickRate);
        sb.append("\n");

        sb.append("[Events]").append("\n");
        sb.append("//Background and Video events").append("\n");
        for (Event event : bgAndVideoEvents) {
            sb.append(event.toEventString()).append("\n");
        }
        sb.append("//Break Periods").append("\n");
        for (Event.BreakEvent event : breakEvents) {
            sb.append(event.toEventString()).append("\n");
        }
        sb.append("//Storyboard Layer 0 (Background)").append("\n");
        for (Event.StoryboardEvent event : storyBoardLayer0) {
            sb.append(event.toEventString()).append("\n");
        }
        sb.append("//Storyboard Layer 1 (Fail)").append("\n");
        for (Event.StoryboardEvent event : storyBoardLayer1) {
            sb.append(event.toEventString()).append("\n");
        }
        sb.append("//Storyboard Layer 2 (Pass)").append("\n");
        for (Event.StoryboardEvent event : storyBoardLayer2) {
            sb.append(event.toEventString()).append("\n");
        }
        sb.append("//Storyboard Layer 3 (Foreground)").append("\n");
        for (Event.StoryboardEvent event : storyBoardLayer3) {
            sb.append(event.toEventString()).append("\n");
        }
        sb.append("//Storyboard Layer 4 (Overlay)").append("\n");
        for (Event.StoryboardEvent event : storyBoardLayer4) {
            sb.append(event.toEventString()).append("\n");
        }
        sb.append("//Storyboard Sound Samples").append("\n");
        for (Event.StoryboardEvent event : audioSampleEvents) {
            sb.append(event.toEventString()).append("\n");
        }
        sb.append("\n");

        sb.append("[TimingPoints]").append("\n");
        for (TimingPoint timingPoint : timingPoints) {
            sb.append(timingPoint.toTimingPointLine()).append("\n");
        }
        sb.append("\n");

        sb.append("[Colours]").append("\n");
        for (int i = 0, coloursSize = colours.size(); i < coloursSize; i++) {
            var v = colours.get(i);
            sb.append("Combo").append(i + 1).append(" : ").append("%d,%d,%d".formatted(v.getRed(), v.getGreen(), v.getBlue())).append("\n");
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

    @Getter
    public abstract static class Event {
        protected final Type type;

        private Event(Type type) {
            this.type = type;
        }

        public abstract String toEventString();

        public enum Type {
            BACKGROUND(0), VIDEO(1), BREAK(2), STORYBOARD(3);

            @Getter
            final int typeNum;

            Type(int typeNum) {
                this.typeNum = typeNum;
            }
        }

        @Getter
        public static class BackgroundEvent extends Event {
            private final long startTime;
            private final String fileName;
            private final int xOffset;
            private final int yOffset;

            public BackgroundEvent(long startTime, String fileName, int xOffset, int yOffset) {
                super(Type.BACKGROUND);
                this.startTime = startTime;
                this.fileName = fileName;
                this.xOffset = xOffset;
                this.yOffset = yOffset;
            }

            @Override
            public String toEventString() {
                return type.getTypeNum() + "," + startTime + "," + fileName + "," + xOffset + "," + yOffset;
            }
        }

        @Getter
        public static class VideoEvent extends Event {
            private final long startTime;
            private final String fileName;
            private final int xOffset;
            private final int yOffset;

            public VideoEvent(long startTime, String fileName, int xOffset, int yOffset) {
                super(Type.VIDEO);
                this.startTime = startTime;
                this.fileName = fileName;
                this.xOffset = xOffset;
                this.yOffset = yOffset;
            }

            @Override
            public String toEventString() {
                return type.getTypeNum() + "," + startTime + "," + fileName + "," + xOffset + "," + yOffset;
            }
        }

        @Getter
        public static class BreakEvent extends Event {
            private final long startTime;
            private final long endTime;

            public BreakEvent(long startTime, long endTime) {
                super(Type.BREAK);
                this.startTime = startTime;
                this.endTime = endTime;
            }

            @Override
            public String toEventString() {
                return type.getTypeNum() + "," + startTime + "," + endTime;
            }
        }

        @Getter
        public static class StoryboardEvent extends Event {
            private final String rawData;

            public StoryboardEvent(String rawData) {
                super(Type.STORYBOARD);
                this.rawData = rawData;
            }

            @Override
            public String toEventString() {
                return rawData;
            }
        }
    }
}
