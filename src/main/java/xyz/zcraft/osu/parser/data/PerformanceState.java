package xyz.zcraft.osu.parser.data;

import xyz.zcraft.osu.parser.ReplayAnalyzer;
import xyz.zcraft.osu.parser.data.replay.HitEvent;

public class PerformanceState {
    public int n300;
    public int n100;
    public int n50;
    public int misses;

    public int currentCombo;
    public int maxCombo;

    public void process(HitEvent event) {
        this.process(event, false);
    }

    public void process(HitEvent event, boolean replaceMissWith300) {
        if (event.isObjectStart()) {
            if (replaceMissWith300 && event.hitResult() == HitEvent.HitResult.MISS) {
                n300++;
            } else {
                switch (event.hitResult()) {
                    case PERFECT -> n300++;
                    case OK -> n100++;
                    case MEH -> n50++;
                    case MISS -> misses++;
                }
            }
        }

        if (ReplayAnalyzer.isComboEvent(event)) {
            boolean hit = event.wasHit();

            if (replaceMissWith300
                    && event.isObjectStart()
                    && event.hitResult() == HitEvent.HitResult.MISS) {
                hit = true;
            }

            if (hit) {
                currentCombo++;
                maxCombo = Math.max(maxCombo, currentCombo);
            } else {
                currentCombo = 0;
            }
        }
    }
}