package xyz.zcraft.osu.parser.data;

import com.google.gson.annotations.SerializedName;

public record PerformanceAttributes(
            Double aim,
            Double speed,
            Double accuracy,
            Double flashlight,
            Double reading,
            @SerializedName("effective_miss_count") Double effectiveMissCount,
            @SerializedName("speed_deviation") Double speedDeviation,
            @SerializedName("combo_based_estimated_miss_count") Double comboBasedEstimatedMissCount,
            @SerializedName("score_based_estimated_miss_count") Double scoreBasedEstimatedMissCount,
            @SerializedName("aim_estimated_slider_breaks") Double aimEstimateSliderBreaks,
            @SerializedName("speed_estimated_slider_breaks") Double speedEstimateSliderBreaks,
            Double pp
    ) {
    }