package xyz.zcraft.osu.parser;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import desu.life.RosuFFI;
import org.apache.commons.lang3.tuple.Pair;
import xyz.zcraft.osu.model.Mod;
import xyz.zcraft.osu.model.Score;
import xyz.zcraft.osu.parser.data.beatmap.DiffSpec;
import xyz.zcraft.osu.parser.data.beatmap.OsuBeatmap;
import xyz.zcraft.osu.parser.data.beatmap.WindowDifficulty;
import xyz.zcraft.osu.parser.data.replay.ReplayAnalyze;
import xyz.zcraft.osu.parser.data.replay.WdPerform;
import xyz.zcraft.osu.parser.exception.AnalyzeException;

import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedList;

@SuppressWarnings("unused")
public class OsuParser {
    private static final Gson GSON = new Gson();

    public static DiffSpec getDiffSpecForMap(OsuBeatmap beatmap, String mod) throws AnalyzeException {
        try (final RosuFFI.Beatmap rosuBeatmap = new RosuFFI.Beatmap(beatmap.toBeatmapString().getBytes());
             final RosuFFI.Performance perf = new RosuFFI.Performance()
        ) {
            final DiffSpec diffSpec = new DiffSpec();

            final RosuFFI.Mods mods = RosuFFI.Mods.fromAcronyms(mod == null ? "" : mod, RosuFFI.Mode.Osu);

            mods.removeUnknownMods();
            mods.sanitize();

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

            if (mods.contains("DT") || mods.contains("NC")) {
                diffSpec.setBpm(diffSpec.getBpm() * 1.5);
                diffSpec.setLength(diffSpec.getLength() / 1.5);
                diffSpec.setTotalLength(diffSpec.getTotalLength() / 1.5);
            } else if (mods.contains("HT") || mods.contains("DC")) {
                diffSpec.setBpm(diffSpec.getBpm() * 0.75);
                diffSpec.setLength(diffSpec.getLength() / 0.75);
                diffSpec.setTotalLength(diffSpec.getTotalLength() / 0.75);
            }

            final LinkedList<Mod> modList = new LinkedList<>();

            for (JsonElement jsonElement : JsonParser.parseString(mods.json()).getAsJsonArray().asList()) {
                modList.add(GSON.fromJson(jsonElement, Mod.class));
            }

            diffSpec.setMods(modList);
            diffSpec.setMaxCombo(scoreState.max_combo);

            diffSpec.setDifficulty(BeatmapAnalyzer.calculateDifficulty(beatmap, mods.bits()));

            return diffSpec;
        } catch (Exception e) {
            throw new AnalyzeException("Failed to calculate difficulty spec for beatmap", e);
        }
    }

    public static double estimatePp(Score score, OsuBeatmap beatmap) throws AnalyzeException {
        try (final RosuFFI.Beatmap rosuBeatmap = new RosuFFI.Beatmap(beatmap.toBeatmapString().getBytes());
             final RosuFFI.Performance perf = new RosuFFI.Performance()
        ) {
            perf.mods(RosuFFI.Mods.fromAcronyms(score.getMods().stream().map(Mod::getAcronym).reduce("", String::concat), RosuFFI.Mode.Osu));

            perf.accuracy(score.getAccuracy() * 100);
            perf.n300(score.getStatistics().getOrDefault("great", 0L));
            perf.n100(score.getStatistics().getOrDefault("ok", 0L));
            perf.n50(score.getStatistics().getOrDefault("meh", 0L));
            perf.misses(score.getStatistics().getOrDefault("miss", 0L));

            var calc = perf.calculate(rosuBeatmap);

            return calc.asOsu().pp;
        } catch (RosuFFI.FFIException e) {
            throw new AnalyzeException("Failed to calculate PP", e);
        }
    }

    public static WdPerform getHighlight(ReplayAnalyze ra) throws AnalyzeException {
        return BeatmapAnalyzer.getWindowDifficulties(ra.beatmap(), Duration.ofSeconds(20))
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
