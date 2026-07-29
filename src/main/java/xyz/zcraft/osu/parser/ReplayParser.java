package xyz.zcraft.osu.parser;

import com.google.gson.Gson;
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream;
import xyz.zcraft.osu.parser.data.replay.OsuReplay;
import xyz.zcraft.osu.parser.data.replay.ReplayInfo;
import xyz.zcraft.osu.parser.exception.ParseException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ReplayParser {
    private static final Gson GSON = new Gson();

    public static OsuReplay parseReplay(Path filePath) throws ParseException {
        try {
            byte[] bytes = Files.readAllBytes(filePath);
            return parseReplay(bytes);
        } catch (IOException e) {
            throw new ParseException("Failed to read replay file", e);
        }
    }

    public static OsuReplay parseReplay(byte[] bytes) throws ParseException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        byte gameMode = buffer.get();
        int gameVersion = buffer.getInt();

        String beatmapHash = readOsuString(buffer);
        String playerName = readOsuString(buffer);
        String replayHash = readOsuString(buffer);

        short count300 = buffer.getShort();
        short count100 = buffer.getShort();
        short count50 = buffer.getShort();
        short countGeki = buffer.getShort();
        short countKatu = buffer.getShort();
        short countMiss = buffer.getShort();

        int totalScore = buffer.getInt();
        short maxCombo = buffer.getShort();
        byte perfectCombo = buffer.get();
        int mods = buffer.getInt();

        String lifeBarGraph = readOsuString(buffer);

        long timestamp = buffer.getLong();

        final List<OsuReplay.KeyFrame> keyFrames = parseReplayFrames(buffer);

        final List<OsuReplay.TimedKeyFrame> timedKeyFrames = timeKeyFrame(keyFrames);

        long legacyScoreId = readLegacyScoreId(buffer, gameVersion);
        var replayInfo = readReplayInfo(buffer, gameVersion);

        return new OsuReplay(gameMode, gameVersion, beatmapHash, playerName, replayHash,
                count300, count100, count50, countGeki, countKatu, countMiss,
                totalScore, maxCombo, perfectCombo == 1, mods, lifeBarGraph, timestamp, timedKeyFrames,
                legacyScoreId, replayInfo);
    }

    private static long readLegacyScoreId(ByteBuffer buffer, int gameVersion) throws ParseException {
        try {
            long id;

            if (gameVersion >= 20140721) {
                id = buffer.getLong();
            } else if (gameVersion >= 20121008) {
                id = Integer.toUnsignedLong(buffer.getInt());
            } else {
                return -1;
            }

            return id == 0 ? -1 : id;
        } catch (BufferUnderflowException e) {
            throw new ParseException("Replay ended before the legacy score ID", e);
        }
    }

    private static ReplayInfo readReplayInfo(ByteBuffer buffer, int gameVersion) throws ParseException {
        if (gameVersion < 30000001) {
            return null;
        }

        try {
            final String s = readCompressedData(buffer);
            return GSON.fromJson(s, ReplayInfo.class);
        } catch (BufferUnderflowException e) {
            throw new ParseException("Byte underflow reading replay info", e);
        }
    }

    private static String readOsuString(ByteBuffer buffer) {
        byte indicator = buffer.get();
        if (indicator == 0x00) return "";
        if (indicator != 0x0b) throw new IllegalStateException("Expected 0x0b for string");

        int length = readULEB128(buffer);
        byte[] stringBytes = new byte[length];
        buffer.get(stringBytes);
        return new String(stringBytes, StandardCharsets.UTF_8);
    }

    private static List<OsuReplay.KeyFrame> parseReplayFrames(ByteBuffer buffer) throws ParseException {
        return analyzeFrames(readCompressedData(buffer));
    }

    private static String readCompressedData(ByteBuffer buffer) throws ParseException {
        int compressedDataLength = buffer.getInt();

        byte[] compressedBytes = new byte[compressedDataLength];
        buffer.get(compressedBytes);

        ByteArrayInputStream bais = new ByteArrayInputStream(compressedBytes);

        try (LZMACompressorInputStream lzmaIn = new LZMACompressorInputStream(bais)) {
            return new String(
                    lzmaIn.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            throw new ParseException("Failed to read compressed data", e);
        }
    }

    private static List<OsuReplay.TimedKeyFrame> timeKeyFrame(List<OsuReplay.KeyFrame> keyFrames) {
        ArrayList<OsuReplay.TimedKeyFrame> timedKeyFrames = new ArrayList<>(keyFrames.size());

        long t = 0L;
        for (final OsuReplay.KeyFrame cur : keyFrames) {
            t += cur.offset();
            timedKeyFrames.add(new OsuReplay.TimedKeyFrame(t, cur.offset(), cur.cursorX(), cur.cursorY(), cur.key()));
        }

        return timedKeyFrames;
    }

    private static List<OsuReplay.KeyFrame> analyzeFrames(String replayDataString) {
        List<OsuReplay.KeyFrame> keyFrames = new LinkedList<>();

        String[] frames = replayDataString.split(",");

        for (String frame : frames) {
            if (frame.trim().isEmpty()) continue;

            String[] data = frame.split("\\|");

            if (data.length != 4) continue;

            long w = Long.parseLong(data[0]); // Time elapsed since last frame
            float x = Float.parseFloat(data[1]); // Cursor X (0 - 512)
            float y = Float.parseFloat(data[2]); // Cursor Y (0 - 384)
            int z = Integer.parseInt(data[3]); // Keys pressed (Bitwise)

            if (w == -12345) {
                break;
            }

            keyFrames.add(new OsuReplay.KeyFrame(w, x, y, z));
        }

        return keyFrames;
    }

    private static int readULEB128(ByteBuffer buffer) {
        int result = 0;
        int shift = 0;

        while (true) {
            byte b = buffer.get();
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) break;
            shift += 7;
        }

        return result;
    }
}
