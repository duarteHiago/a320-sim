package br.a320sim.cartridges.propulsao;

import java.util.List;

public class AutopilotBasic extends Autopilot {

    public AutopilotBasic() {
        super("ceo - básico", List.of("HDG", "ALT"));
    }
}
