package xyz.zcraft.osu.parser.data.beatmap;

public record DifficultyAttribute(
        double cs,
        double od,
        double ar,
        double hp,
        double originalOd,
        double clockRate
) {
    public double getCircleRadiusInPixel() {
        return 54.4 - 4.48 * cs;
    }

    public double getPerfectWindow() {
        return (80 - 6 * originalOd) / clockRate;
    }

    public double getOkWindow() {
        return (140 - 8 * originalOd) / clockRate;
    }

    public double getMehWindow() {
        return (200 - 10 * originalOd) / clockRate;
    }

    public double getMissWindow() {
        return (400 / clockRate);
    }
}
