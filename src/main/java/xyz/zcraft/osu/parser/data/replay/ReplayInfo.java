package xyz.zcraft.osu.parser.data.replay;

import com.google.gson.annotations.SerializedName;
import xyz.zcraft.osu.model.Mod;
import xyz.zcraft.osu.model.Score;

import java.util.List;

public record ReplayInfo(
        @SerializedName("client_version") String clientVersion,
        String rank,
        @SerializedName("user_id") Long userId,
        @SerializedName("online_id") Long onlineId,
        List<Mod> mods,
        Score.ScoreStatistics statistics,
        @SerializedName("maximum_statistics") Score.ScoreStatistics maximumStatistics,
        @SerializedName("total_score_without_mods") Long totalScoreWithoutMods,
        List<Long> pauses
) {
}
