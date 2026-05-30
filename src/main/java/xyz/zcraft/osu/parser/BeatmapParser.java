package xyz.zcraft.osu.parser;

import xyz.zcraft.osu.parser.data.DifficultyAttribute;
import xyz.zcraft.osu.parser.data.HitObject;
import xyz.zcraft.osu.parser.data.OsuBeatmap;
import xyz.zcraft.osu.parser.exception.ParseException;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;

public class BeatmapParser {
    public static OsuBeatmap parseBeatmap(Path beatmapPath) throws ParseException {
        try {
            OsuBeatmap beatmap = new OsuBeatmap();
            beatmap.setHitObjects(new LinkedList<>());

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

            try (BufferedReader reader = new BufferedReader(new FileReader(beatmapPath.toFile()))) {
                String line;

                while ((line = reader.readLine()) != null) {
                    line = line.trim();

                    if (line.isEmpty() || line.startsWith("//")) continue;

                    if (line.startsWith("[") && line.endsWith("]")) {
                        currentSection = line;
                        continue;
                    }

                    switch (currentSection) {
                        case "[General]" -> parseGeneralLine(beatmap, line);
                        case "[Metadata]" -> parseMetadataLine(beatmap, line);
                        case "[Difficulty]" -> parseDifficultyLine(beatmap, line);
                        case "[HitObjects]" -> parseHitObjectLine(beatmap, line);
                        // Ignore [Editor], [Events], [TimingPoints], [Colours] for now
                    }
                }
            }
            return beatmap;
        } catch (Exception e) {
            throw new ParseException("Cannot parse beatmap", e);
        }
    }

    private static void parseDifficultyLine(OsuBeatmap beatmap, String line) {
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

    private static void parseGeneralLine(OsuBeatmap beatmap, String line) {
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
            case "WidescreenStoryboard" -> beatmap.setWidescreenStoryboard(parseIntSafe(value));
        }
    }

    private static void parseMetadataLine(OsuBeatmap beatmap, String line) {
        String[] kv = line.split(":", 2);
        if (kv.length < 2) return;

        String key = kv[0].trim();
        String value = kv[1].trim();

        switch (key) {
            case "Title" -> beatmap.setTitle(value);
            case "TitleUnicode" -> beatmap.setTitleUnicode(value);
            case "Artist" -> beatmap.setArtist(value);
            case "ArtistUnicode" -> beatmap.setArtistUnicode(value);
            case "Creator" -> beatmap.setCreator(value);
            case "Version" -> beatmap.setVersion(value);
            case "Source" -> beatmap.setSource(value);
            case "Tags" -> beatmap.setTags(value);
            case "BeatmapID" -> beatmap.setBeatmapId(parseLongSafe(value));
            case "BeatmapSetID" -> beatmap.setBeatmapSetId(parseLongSafe(value));
        }
    }

    private static void parseHitObjectLine(OsuBeatmap beatmap, String line) {
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
                    obj.getControlPoints().add(new HitObject.ControlPoint(Integer.parseInt(coords[0]), Integer.parseInt(coords[1])));
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

    private static long parseLongSafe(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double parseDoubleSafe(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static DifficultyAttribute calculateDifficulty(OsuBeatmap beatmap, long mods) {
        boolean hasEZ = (mods & 2) > 0;
        boolean hasHR = (mods & 16) > 0;
        boolean hasDT = (mods & 64) > 0;
        boolean hasHT = (mods & 256) > 0;
        boolean hasNC = (mods & 512) > 0;

        double cs = beatmap.getCs();
        double od = beatmap.getOd();
        double ar = beatmap.getAr();
        double hp = beatmap.getHp();

        double approachTime = ar >= 5 ? (1200 - 150 * (ar - 5)) : (1800 - 120 * ar);

        if (hasHR) {
            cs = Math.min(10.0, cs * 1.3);
            od = Math.min(10.0, od * 1.4);
            hp = Math.min(10.0, hp * 1.4);
        } else if (hasEZ) {
            cs = cs * 0.5;
            od = od * 0.5;
            hp = hp * 0.5;
        }

        double clockRate = 1.0;
        if (hasDT || hasNC) {
            clockRate = 1.5;
        } else if (hasHT) {
            clockRate = 0.75;
        }

        approachTime = approachTime / clockRate;

        if (approachTime > 1200) {
            ar = (1800 - approachTime) / 120;
        } else {
            ar = 5 + (1200 - approachTime) / 150;
        }

        double window = (80.0 - (6.0 * od)) / clockRate;
        od = (80.0 - window) / 6;

        return new DifficultyAttribute(cs, od, ar, hp, beatmap.getOd(), clockRate);
    }
}
