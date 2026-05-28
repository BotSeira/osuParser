package xyz.zcraft.osu.parser.data;

public record DifficultyAttribute(
        double cs,
        double od,
        double ar,
        double hp
) {
    public double getCircleRadiusInPixel() {
        return 54.4 - 4.48 * cs;
    }

    public double getPerfectWindow() {
        return (80 - 6 * od);
    }

    public double getOkWindow() {
        return (140 - 8 * od);
    }

    public double getMehWindow() {
        return (200 - 10 * od);
    }

    public double getMissWindow() {
        return (400);
    }
}
