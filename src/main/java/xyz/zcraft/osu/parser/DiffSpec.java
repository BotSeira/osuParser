package xyz.zcraft.osu.parser;

import lombok.Data;

import xyz.zcraft.osu.model.*;

import java.io.Serializable;
import java.util.List;

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
}
