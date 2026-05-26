package xyz.zcraft.osu.parser.data;

import lombok.Data;

import xyz.zcraft.osu.model.*;

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
    private double od;
    private double cs;
    private double ar;
    private double hp;
    private double star;
    private double bpm;
    private String modStr;
    private boolean modded = false;
    private double length;
    private double totalLength;
    private int maxCombo;
    private List<Mod> mods;

    public double getPerfectWindow() {
        return 80 - 6 * od;
    }

    public double getOkWindow() {
        return 140 - 8 * od;
    }

    public double getMehWindow() {
        return 200 - 10 * od;
    }

    public double getMissWindow() {
        return 400;
    }

    public double getCircleRadius() {
        return 54.4 - 4.48 * cs;
    }
}
