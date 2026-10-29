package br.a320sim.cartridges.cockpit;

import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;

import java.util.List;

/**
 * Versão neo: além dos alertas básicos, avisa configuração errada de decolagem,
 * trem recolhido na aproximação e perda da Lei Normal.
 */
public class FwcAdvanced extends Fwc {

    public FwcAdvanced() {
        super("neo - avançado");
    }

    @Override
    protected void collectAlerts(DataBus bus, List<String> warnings, List<String> cautions) {
        super.collectAlerts(bus, warnings, cautions);
        boolean onGround = bus.getBoolean(Labels.SIM_ON_GROUND);
        boolean gearDown = !bus.has(Labels.CTL_GEAR_DOWN) || bus.getBoolean(Labels.CTL_GEAR_DOWN);

        if (onGround && bus.getDouble(Labels.SIM_THRUST_LEVER, 0) > 0.7 && bus.getDouble(Labels.CTL_FLAPS, 0) == 0) {
            warnings.add("CONFIG FLAPS");
        }
        if (!onGround && !gearDown && bus.getDouble(Labels.SIM_RADIO_ALT, 9999) < 750
                && bus.getDouble(Labels.ADR_VS, 0) < -300) {
            warnings.add("L/G NOT DOWN");
        }
        if (!onGround && "DIRECT".equals(bus.getString(Labels.FCTL_LAW, ""))) {
            cautions.add("F/CTL DIRECT LAW");
        }
    }
}
