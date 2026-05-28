package xyz.zcraft.osu.parser.data;

import lombok.Data;
import xyz.zcraft.osu.model.Mod;

import java.io.Serializable;
import java.util.List;

@SuppressWarnings("unused")
@Data
public final class DiffSpec implements Serializable {
    private double ppSS;
    private double ppFC;
    private double pp95;
    private double aim;
    private double speed;

    @Deprecated
    private double od;

    @Deprecated
    private double cs;

    @Deprecated
    private double ar;

    @Deprecated
    private double hp;

    private DifficultyAttribute difficulty;
    private double star;
    private double bpm;
    private String modStr;
    private boolean modded = false;
    private double length;
    private double totalLength;
    private int maxCombo;
    private List<Mod> mods;

    @Deprecated
    public double getPerfectWindow() {
        return difficulty.getPerfectWindow();
    }

    @Deprecated
    public double getOkWindow() {
        return difficulty.getOkWindow();
    }

    @Deprecated
    public double getMehWindow() {
        return difficulty.getMehWindow();
    }

    @Deprecated
    public double getMissWindow() {
        return difficulty.getMissWindow();
    }

    @Deprecated
    public double getCircleRadius() {
        return difficulty.getCircleRadiusInPixel();
    }
}
