package xyz.zcraft.osu.parser;

import org.apache.commons.lang3.tuple.Pair;
import xyz.zcraft.osu.parser.data.beatmap.HitObject;
import xyz.zcraft.osu.parser.data.beatmap.OsuBeatmap;
import xyz.zcraft.osu.parser.exception.ParseException;

import java.awt.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class BeatmapParser {
    public static OsuBeatmap parseBeatmap(Path beatmapPath) throws ParseException {
        try {
            OsuBeatmap beatmap = new OsuBeatmap();

            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                final byte[] digest = md.digest(Files.readAllBytes(beatmapPath));
                StringBuilder hexString = new StringBuilder();
                for (byte b : digest) {
                    hexString.append(String.format("%02x", b));
                }
                beatmap.setHash(hexString.toString());
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }

            String currentSection = "";
            String currentStoryboardSection = "";

            try (BufferedReader reader = new BufferedReader(new FileReader(beatmapPath.toFile()))) {
                String line;

                while ((line = reader.readLine()) != null) {
                    line = line.trim();


                    if (line.startsWith("[") && line.endsWith("]")) {
                        currentSection = line;
                        continue;
                    }

                    if ((line.isEmpty() || line.startsWith("//")) && !"[Events]".equals(currentSection)) continue;

                    switch (currentSection) {
                        case "[General]" -> BeatmapFileParser.parseGeneralLine(beatmap, line);
                        case "[Editor]" -> BeatmapFileParser.parseEditorLine(beatmap, line);
                        case "[Metadata]" -> BeatmapFileParser.parseMetadataLine(beatmap, line);
                        case "[Difficulty]" -> BeatmapFileParser.parseDifficultyLine(beatmap, line);
                        case "[Events]" ->
                                currentStoryboardSection = BeatmapFileParser.parseStoryboardLine(beatmap, line, currentStoryboardSection);
                        case "[TimingPoints]" -> BeatmapFileParser.parseTimingPointsLine(beatmap, line);
                        case "[Colours]" -> BeatmapFileParser.parseColoursLine(beatmap, line);
                        case "[HitObjects]" -> BeatmapFileParser.parseHitObjectLine(beatmap, line);
                    }
                }
            }

            beatmap.setBpm(BeatmapAnalyzer.calculateBpm(beatmap));

            final Pair<Integer, Integer> len = BeatmapAnalyzer.calculateLengths(beatmap);

            beatmap.setTotalLength(len.getLeft());
            beatmap.setHitLength(len.getRight());

            return beatmap;
        } catch (Exception e) {
            throw new ParseException("Cannot parse beatmap", e);
        }
    }
}

class BeatmapFileParser {
    static void parseDifficultyLine(OsuBeatmap beatmap, String line) {
        String[] kv = line.split(":", 2);
        if (kv.length < 2) return;

        String key = kv[0].trim();
        double value = Double.parseDouble(kv[1].trim());

        switch (key) {
            case "HPDrainRate" -> beatmap.setHp(value);
            case "CircleSize" -> beatmap.setCs(value);
            case "ApproachRate" -> beatmap.setAr(value);
            case "OverallDifficulty" -> beatmap.setOd(value);
            case "SliderMultiplier" -> beatmap.setSliderMultiplier(value);
            case "SliderTickRate" -> beatmap.setSliderTickRate(value);
        }
    }

    static void parseGeneralLine(OsuBeatmap beatmap, String line) {
        String[] kv = line.split(":", 2);
        if (kv.length < 2) return;

        String key = kv[0].trim();
        String value = kv[1].trim();

        switch (key) {
            case "AudioFilename" -> beatmap.setAudioFileName(value);
            case "AudioLeadIn" -> beatmap.setAudioLeadIn(parseLongSafe(value));
            case "PreviewTime" -> beatmap.setPreviewTime(parseLongSafe(value));
            case "Countdown" -> beatmap.setCountdown(parseLongSafe(value));
            case "SampleSet" -> beatmap.setSampleSet(value);
            case "StackLeniency" -> beatmap.setStackLeniency(parseDoubleSafe(value));
            case "Mode" -> beatmap.setMode(parseIntSafe(value));
            case "LetterboxInBreaks" -> beatmap.setLetterboxInBreaks(parseIntSafe(value));
            case "UseSkinSprites" -> beatmap.setUseSkinSprites(parseIntSafe(value));
            case "OverlayPosition" -> beatmap.setOverlayPosition(value);
            case "SkinPreference" -> beatmap.setSkinPreference(value);
            case "EpilepsyWarning" -> beatmap.setEpilepsyWarning(parseIntSafe(value));
            case "CountdownOffset" -> beatmap.setCountdownOffset(parseIntSafe(value));
            case "SpecialStyle" -> beatmap.setSpecialStyle(parseIntSafe(value));
            case "WidescreenStoryboard" -> beatmap.setWidescreenStoryboard(parseIntSafe(value));
            case "SamplesMatchPlaybackRate" -> beatmap.setSamplesMatchPlaybackRate(parseIntSafe(value));
        }
    }

    static void parseEditorLine(OsuBeatmap beatmap, String line) {
        String[] kv = line.split(":", 2);
        if (kv.length < 2) return;

        String key = kv[0].trim();
        String value = kv[1].trim();

        switch (key) {
            case "Bookmarks" -> beatmap.setBookmarks(parseLongListSafe(value));
            case "DistanceSpacing" -> beatmap.setDistanceSpacing(parseDoubleSafe(value));
            case "BeatDivisor" -> beatmap.setBeatDivisor(parseIntSafe(value));
            case "GridSize" -> beatmap.setGridSize(parseIntSafe(value));
            case "TimelineZoom" -> beatmap.setTimelineZoom(parseDoubleSafe(value));
        }
    }

    static String parseStoryboardLine(OsuBeatmap beatmap, String line, String section) {
        if (line.startsWith("//")) {
            return line;
        }

        if ("//Background and Video events".equals(section) && line.startsWith("0,")) {
            String[] kv = line.split(",");
            if (kv.length == 5) {
                beatmap.getBgAndVideoEvents().add(new OsuBeatmap.Event.BackgroundEvent(
                        parseLongSafe(kv[1]),
                        kv[2],
                        parseIntSafe(kv[3]),
                        parseIntSafe(kv[4])
                ));
            }
        } else if ("//Background and Video events".equals(section) && (line.startsWith("1,") || line.startsWith("Video,"))) {
            String[] kv = line.split(",");
            if (kv.length == 5) {
                beatmap.getBgAndVideoEvents().add(new OsuBeatmap.Event.VideoEvent(
                        parseLongSafe(kv[1]),
                        kv[2],
                        parseIntSafe(kv[3]),
                        parseIntSafe(kv[4])
                ));
            }
        } else if ("//Break Periods".equals(section) && (line.startsWith("2,") || line.startsWith("Break,"))) {
            String[] kv = line.split(",");
            if (kv.length == 3) {
                beatmap.getBreakEvents().add(new OsuBeatmap.Event.BreakEvent(
                        parseLongSafe(kv[1]),
                        parseLongSafe(kv[2])
                ));
            }
        } else {
            switch (section) {
                case "//Storyboard Layer 0 (Background)" ->
                        beatmap.getStoryBoardLayer0().add(new OsuBeatmap.Event.StoryboardEvent(line));
                case "//Storyboard Layer 1 (Fail)" ->
                        beatmap.getStoryBoardLayer1().add(new OsuBeatmap.Event.StoryboardEvent(line));
                case "//Storyboard Layer 2 (Pass)" ->
                        beatmap.getStoryBoardLayer2().add(new OsuBeatmap.Event.StoryboardEvent(line));
                case "//Storyboard Layer 3 (Foreground)" ->
                        beatmap.getStoryBoardLayer3().add(new OsuBeatmap.Event.StoryboardEvent(line));
                case "//Storyboard Layer 4 (Overlay)" ->
                        beatmap.getStoryBoardLayer4().add(new OsuBeatmap.Event.StoryboardEvent(line));
                case "//Storyboard Sound Samples" ->
                        beatmap.getAudioSampleEvents().add(new OsuBeatmap.Event.StoryboardEvent(line));
            }
        }

        return section;
    }

    static void parseMetadataLine(OsuBeatmap beatmap, String line) {
        String[] kv = line.split(":", 2);

        String key = kv[0].trim();
        String value = kv.length < 2 ? "" : kv[1].trim();

        switch (key) {
            case "Title" -> beatmap.setTitle(value);
            case "TitleUnicode" -> beatmap.setTitleUnicode(value);
            case "Artist" -> beatmap.setArtist(value);
            case "ArtistUnicode" -> beatmap.setArtistUnicode(value);
            case "Creator" -> beatmap.setCreator(value);
            case "Version" -> beatmap.setVersion(value);
            case "Source" -> beatmap.setSource(value);
            case "Tags" -> beatmap.setTags(List.of(value.split(",")));
            case "BeatmapID" -> beatmap.setBeatmapId(parseLongSafe(value));
            case "BeatmapSetID" -> beatmap.setBeatmapSetId(parseLongSafe(value));
        }
    }

    static void parseHitObjectLine(OsuBeatmap beatmap, String line) {
        HitObject obj = new HitObject();
        obj.setRawData(line);
        String[] parts = line.split(",");

        obj.setX(Integer.parseInt(parts[0]));
        obj.setY(Integer.parseInt(parts[1]));
        obj.setTime(Long.parseLong(parts[2]));
        obj.setTypeFlag(Integer.parseInt(parts[3]));
        obj.setHitSoundFlag(Integer.parseInt(parts[4]));

        obj.setNewCombo((obj.getTypeFlag() & 4) > 0);

        if ((obj.getTypeFlag() & 1) > 0) {
            obj.setObjectType(HitObject.ObjectType.HIT_CIRCLE);
        } else if ((obj.getTypeFlag() & 2) > 0) {
            obj.setObjectType(HitObject.ObjectType.SLIDER);
            if (parts.length > 5) {
                String[] curveData = parts[5].split("\\|");
                obj.setCurveType(curveData[0]);
                for (int i = 1; i < curveData.length; i++) {
                    String[] coords = curveData[i].split(":");
                    obj.getControlPoints().add(new HitObject.ControlPoint(Double.parseDouble(coords[0]), Double.parseDouble(coords[1])));
                }
            }
            if (parts.length > 6) obj.setSlides(Integer.parseInt(parts[6]));
            if (parts.length > 7) obj.setLength(Double.parseDouble(parts[7]));
        } else if ((obj.getTypeFlag() & 8) > 0) {
            obj.setObjectType(HitObject.ObjectType.SPINNER);
            obj.setEndTime(Integer.parseInt(parts[5]));
        }

        beatmap.getHitObjects().add(obj);
    }

    static void parseTimingPointsLine(OsuBeatmap beatmap, String line) {
        final String[] split = line.split(",");
        beatmap.getTimingPoints().add(
                new OsuBeatmap.TimingPoint(
                        parseLongSafe(split[0]),
                        parseDoubleSafe(split[1]),
                        parseIntSafe(split[2]),
                        parseIntSafe(split[3]),
                        parseIntSafe(split[4]),
                        parseIntSafe(split[5]),
                        parseIntSafe(split[6]),
                        parseIntSafe(split[7])
                ));
    }

    static void parseColoursLine(OsuBeatmap beatmap, String line) {
        final String[] kv = line.split(":");
        final String[] rgb = kv[1].split(",");

        Color c = new Color(parseIntSafe(rgb[0]), parseIntSafe(rgb[1]), parseIntSafe(rgb[2]));
        beatmap.getColours().add(c);
    }

    private static Long parseLongSafe(String value) {
        if (value == null) return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseIntSafe(String value) {
        if (value == null) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double parseDoubleSafe(String value) {
        if (value == null) return null;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<Long> parseLongListSafe(String value) {
        try {
            final String[] split = value.split(",");
            final ArrayList<Long> values = new ArrayList<>();
            for (String s : split) {
                values.add(Long.parseLong(s));
            }
            return values;
        } catch (Exception e) {
            return List.of();
        }
    }
}
