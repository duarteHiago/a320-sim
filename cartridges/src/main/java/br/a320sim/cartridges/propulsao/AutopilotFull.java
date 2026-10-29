package br.a320sim.cartridges.propulsao;

import java.util.List;

public class AutopilotFull extends Autopilot {

    public AutopilotFull() {
        super("neo - completo", List.of("HDG", "ALT", "SPD", "V/S"));
    }
}
