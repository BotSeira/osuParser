package xyz.zcraft.osu.parser;

import com.google.gson.Gson;
import desu.life.RosuFFI;
import org.apache.commons.lang3.tuple.Pair;
import xyz.zcraft.osu.model.Mod;
import xyz.zcraft.osu.model.ModSettings;
import xyz.zcraft.osu.model.Score;
import xyz.zcraft.osu.parser.data.beatmap.DifficultyAttributes;
import xyz.zcraft.osu.parser.data.PerformanceAttributes;
import xyz.zcraft.osu.parser.data.beatmap.DiffSpec;
import xyz.zcraft.osu.parser.data.beatmap.OsuBeatmap;
import xyz.zcraft.osu.parser.data.beatmap.WindowDifficulty;
import xyz.zcraft.osu.parser.data.replay.ReplayAnalyze;
import xyz.zcraft.osu.parser.data.replay.WdPerform;
import xyz.zcraft.osu.parser.exception.AnalyzeException;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

@SuppressWarnings("unused")
public class OsuParser {
    private static final Gson GSON = new Gson();

    public static RosuFFI.Mods toRosuMods(List<Mod> mods) {
        return RosuFFI.Mods.fromJson(GSON.toJson(mods == null ? List.of() : mods), RosuFFI.Mode.Osu, true);
    }

    public static DiffSpec getDiffSpecForMap(OsuBeatmap beatmap, String mod) throws AnalyzeException {
        return getDiffSpecForMods(beatmap, ModSettings.parse(mod));
    }

    public static DiffSpec getDiffSpecForMods(OsuBeatmap beatmap, List<Mod> modList) throws AnalyzeException {
        String mod = ModSettings.format(modList);
        try (final RosuFFI.Beatmap rosuBeatmap = new RosuFFI.Beatmap(beatmap.toBeatmapString().getBytes());
             final RosuFFI.Performance perf = new RosuFFI.Performance();
             final RosuFFI.Mods mods = toRosuMods(modList)
        ) {
            final DiffSpec diffSpec = new DiffSpec();

            perf.mods(mods);

            perf.accuracy(98.0);
            perf.misses(0);

            var calc = perf.calculate(rosuBeatmap);
            diffSpec.setPpFC(calc.asOsu().pp);

            perf.accuracy(95.0);

            calc = perf.calculate(rosuBeatmap);
            diffSpec.setPp95(calc.asOsu().pp);

            perf.accuracy(100.0);
            perf.misses(0);

            calc = perf.calculate(rosuBeatmap);
            diffSpec.setPpSS(calc.asOsu().pp);

            final var scoreState = perf.generateState(rosuBeatmap);

            final var attr = calc.asOsu().difficulty;
            diffSpec.setAim(attr.aim);
            diffSpec.setSpeed(attr.speed);
            diffSpec.setReading(attr.reading);

            diffSpec.setBpm(BeatmapAnalyzer.calculateBpm(beatmap));

            final Pair<Integer, Integer> lengths = BeatmapAnalyzer.calculateLengths(beatmap);

            diffSpec.setTotalLength(lengths.getKey());
            diffSpec.setLength(lengths.getValue());

            diffSpec.setStar(calc.asOsu().difficulty.stars);

            if (mod != null && !mod.isEmpty()) {
                diffSpec.setModStr(mod);
                diffSpec.setModded(true);
            }

            double rate = ModSettings.clockRate(modList);
            diffSpec.setBpm(diffSpec.getBpm() * rate);
            diffSpec.setLength(diffSpec.getLength() / rate);
            diffSpec.setTotalLength(diffSpec.getTotalLength() / rate);

            diffSpec.setMods(modList);
            diffSpec.setMaxCombo(scoreState.max_combo);

            diffSpec.setDifficulty(BeatmapAnalyzer.calculateDifficulty(beatmap, modList));

            return diffSpec;
        } catch (Exception e) {
            throw new AnalyzeException("Failed to calculate difficulty spec for beatmap", e);
        }
    }

    public static int getModBits(Mod mod) {
        return switch (mod.getAcronym()) {
            case "NF" -> 1;
            case "EZ" -> 2;
            case "TD" -> 4;
            case "HD" -> 8;
            case "HR" -> 16;
            case "SD" -> 32;
            case "DT" -> 64;
            case "RX" -> 128;
            case "HT" -> 256;
            case "NC" -> 512 | 64;   // NC implies DT
            case "FL" -> 1024;
            case "SO" -> 4096;
            case "AP" -> 8192;
            case "PF" -> 16384 | 32; // PF implies SD
            case "V2" -> 536870912;
            default -> 0;
        };
    }

    public static double estimatePp(Score score, OsuBeatmap beatmap) throws AnalyzeException {
        return getScorePerfAttr(score, beatmap).pp();
    }

    public static PerformanceAttributes getScorePerfAttr(Score score, OsuBeatmap beatmap) throws AnalyzeException {
        try (final RosuFFI.Beatmap rosuBeatmap = new RosuFFI.Beatmap(beatmap.toBeatmapString().getBytes());
             final RosuFFI.Performance perf = new RosuFFI.Performance();
             final RosuFFI.Mods mods = toRosuMods(score.getMods())
        ) {
            perf.mods(mods);

            perf.accuracy(score.getAccuracy() * 100);
            perf.n300(score.getStatistics().getOrDefault("great", 0L));
            perf.n100(score.getStatistics().getOrDefault("ok", 0L));
            perf.n50(score.getStatistics().getOrDefault("meh", 0L));
            perf.misses(score.getStatistics().getOrDefault("miss", 0L));

            var calc = perf.calculate(rosuBeatmap);

            final RosuFFI.OsuPerformanceAttributes rosu = calc.asOsu();

            return new PerformanceAttributes(
                    rosu.pp_aim, rosu.pp_speed, rosu.pp_acc, rosu.pp_flashlight, rosu.pp_reading,
                    rosu.effective_miss_count, rosu.speed_deviation.some, rosu.combo_based_estimated_miss_count,
                    rosu.score_based_estimated_miss_count.some, rosu.aim_estimated_slider_breaks, rosu.speed_estimated_slider_breaks,
                    rosu.pp
            );
        } catch (RosuFFI.FFIException e) {
            throw new AnalyzeException("Failed to calculate ScorePerfAttr", e);
        }
    }

    public static DifficultyAttributes getBeatmapDiffAttr(OsuBeatmap beatmap, List<Mod> modList) throws AnalyzeException {
        try (final RosuFFI.Beatmap rosuBeatmap = new RosuFFI.Beatmap(beatmap.toBeatmapString().getBytes());
             final RosuFFI.Difficulty diff = new RosuFFI.Difficulty();
             final RosuFFI.Mods mods = toRosuMods(modList)
        ) {
            diff.mods(mods);
            final var calc = diff.calculate(rosuBeatmap);
            final var rosu = calc.asOsu();

            return new DifficultyAttributes(
                    rosu.stars, rosu.max_combo, rosu.aim, rosu.aim_difficult_slider_count,
                    rosu.speed, rosu.speed_note_count, rosu.reading, rosu.slider_factor,
                    rosu.aim_top_weighted_slider_factor, rosu.speed_top_weighted_slider_factor, rosu.aim_difficult_strain_count,
                    rosu.speed_difficult_strain_count, rosu.reading_difficult_note_count, rosu.nested_score_per_object,
                    rosu.legacy_score_base_multiplier, rosu.maximum_legacy_combo_score
            );
        } catch (RosuFFI.FFIException e) {
            throw new AnalyzeException("Failed to calculate BeatmapDiffAttr", e);
        }
    }

    public static WdPerform getHighlight(ReplayAnalyze ra) throws AnalyzeException {
        List<Mod> mods;
        if (ra.replay().replayInfo() != null && ra.replay().replayInfo().mods() != null) {
            mods = ra.replay().replayInfo().mods();
        } else try (var legacyMods = RosuFFI.Mods.fromBits(ra.replay().mods(), RosuFFI.Mode.Osu)) {
            mods = java.util.Arrays.asList(GSON.fromJson(legacyMods.json(), Mod[].class));
        }
        return BeatmapAnalyzer.getWindowDifficulties(ra.beatmap(), Duration.ofSeconds(20), mods)
                .stream().map(wd -> calculatePerform(ra, wd))
                .sorted(Comparator.comparing(WdPerform::wdScore))
                .toList()
                .getLast();
    }

    private static WdPerform calculatePerform(ReplayAnalyze ra, WindowDifficulty wd) {
        final double acc = ReplayAnalyzer.calculateWindowAccuracy(ra, wd.start(), wd.end());
        return new WdPerform(wd.start(), wd.end(), wd.pp(), acc, wd.pp() * Math.pow(acc, 3));
    }
}
