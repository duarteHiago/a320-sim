package br.a320sim.cockpit.audio;

import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Transforma os avisos do FWC em sons e nas luzes MASTER WARN / MASTER CAUT.
 * Warnings repetem o chime até o piloto apertar a luz (reconhecer).
 */
public final class AlertSounds {

    private static final String AP_OFF = "AP OFF";
    private static final String STALL = "STALL";

    private Set<String> previousWarnings = Set.of();
    private Set<String> previousCautions = Set.of();
    private boolean warnLight;
    private boolean cautLight;
    private boolean repeating;
    private double repeatTimer;
    private double stallTimer;

    public void update(DataBus bus, double dt, SoundFx fx) {
        List<String> warnings = bus.getList(Labels.FWC_WARNINGS);
        List<String> cautions = bus.getList(Labels.FWC_CAUTIONS);

        for (String warning : warnings) {
            if (!previousWarnings.contains(warning)) {
                warnLight = true;
                if (warning.equals(AP_OFF)) {
                    fx.play(Sfx.CAVALRY);
                } else if (!warning.equals(STALL)) {
                    repeating = true;
                    repeatTimer = 0;
                }
            }
        }
        for (String caution : cautions) {
            if (!previousCautions.contains(caution)) {
                cautLight = true;
                fx.play(Sfx.CHIME);
            }
        }
        if (warnings.isEmpty()) {
            warnLight = false;
            repeating = false;
        }
        if (cautions.isEmpty()) {
            cautLight = false;
        }

        if (repeating) {
            repeatTimer -= dt;
            if (repeatTimer <= 0) {
                fx.play(Sfx.WARNING);
                repeatTimer = 1.6;
            }
        }
        if (warnings.contains(STALL)) {
            stallTimer -= dt;
            if (stallTimer <= 0) {
                fx.play(Sfx.STALL);
                stallTimer = 0.8;
            }
        }
        previousWarnings = new HashSet<>(warnings);
        previousCautions = new HashSet<>(cautions);
    }

    /** O piloto apertou MASTER WARN / MASTER CAUT: apaga as luzes e cala o chime. */
    public void acknowledge() {
        warnLight = false;
        cautLight = false;
        repeating = false;
    }

    public boolean warnLight() {
        return warnLight;
    }

    public boolean cautLight() {
        return cautLight;
    }
}
