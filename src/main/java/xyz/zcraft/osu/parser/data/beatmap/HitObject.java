package xyz.zcraft.osu.parser.data.beatmap;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class HitObject {
    private int x, y, typeFlag, hitSoundFlag;
    private long time;
    private boolean isNewCombo;
    private ObjectType objectType;
    private String rawData;

    private String curveType;
    private List<ControlPoint> controlPoints = new ArrayList<>();
    private int slides = 1;
    private double length = 0.0;

    public int endTime;

    @Override
    public String toString() {
        return String.format("Time: %dms | Type: %-10s | X:%-3d Y:%-3d", time, objectType, x, y);
    }

    public enum ObjectType {
        HIT_CIRCLE, SLIDER, SPINNER
    }

    public record ControlPoint(int x, int y){}
}
