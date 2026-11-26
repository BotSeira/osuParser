package xyz.zcraft.osu.parser.data.beatmap;

import com.google.gson.annotations.SerializedName;

public record DifficultyAttributes(
            @SerializedName("star_rating") Double starRating,
            @SerializedName("max_combo") Integer maxCombo,
            @SerializedName("aim_difficulty") Double aimDifficulty,
            @SerializedName("aim_difficult_slider_count") Double aimDifficultSliderCount,
            @SerializedName("speed_difficulty") Double speedDifficulty,
            @SerializedName("speed_note_count") Double speedNoteCount,
            @SerializedName("reading_difficulty") Double readingDifficulty,
            @SerializedName("slider_factor") Double sliderFactor,
            @SerializedName("aim_top_weighted_slider_factor") Double aimTopWeightedSliderFactor,
            @SerializedName("speed_top_weighted_slider_factor") Double speedTopWeightedSliderFactor,
            @SerializedName("aim_difficult_strain_count") Double aimDifficultStrainCount,
            @SerializedName("speed_difficult_strain_count") Double speedDifficultStrainCount,
            @SerializedName("reading_difficult_note_count") Double readingDifficultNoteCount,
            @SerializedName("nested_score_per_object") Double nestedScorePerObject,
            @SerializedName("legacy_score_base_multiplier") Double legacyScoreBaseMultiplier,
            @SerializedName("maximum_legacy_combo_score") Double maximumLegacyComboScore
    ) {
    }