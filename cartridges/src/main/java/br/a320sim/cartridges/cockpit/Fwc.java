package br.a320sim.cartridges.cockpit;

import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import br.a320sim.core.module.BaseModule;
import br.a320sim.core.module.ModuleInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Alarmes (FWC). Observa o barramento e gera os avisos do ECAM:
 * warnings (vermelho, urgentes) e cautions (âmbar).
 * Versões mais novas reconhecem mais situações (sobrescrevem collectAlerts).
 */
public abstract class Fwc extends BaseModule {

    private static final double AP_OFF_SECONDS = 3;

    private boolean apWasEngaged;
    private double apOffTimer;

    protected Fwc(String variant) {
        super(new ModuleInfo("FWC", "Alarmes", variant));
    }

    @Override
    protected void onTick(double dt) {
        List<String> warnings = new ArrayList<>();
        List<String> cautions = new ArrayList<>();

        boolean apEngaged = bus.getBoolean(Labels.AP_ENGAGED);
        if (apWasEngaged && !apEngaged) {
            apOffTimer = AP_OFF_SECONDS;
        }
        apWasEngaged = apEngaged;
        if (apOffTimer > 0) {
            apOffTimer -= dt;
            warnings.add("AP OFF");
        }

        collectAlerts(bus, warnings, cautions);
        bus.publish(Labels.FWC_WARNINGS, List.copyOf(warnings));
        bus.publish(Labels.FWC_CAUTIONS, List.copyOf(cautions));
        bus.publish(Labels.FWC_MASTER_WARN, !warnings.isEmpty());
        bus.publish(Labels.FWC_MASTER_CAUT, !cautions.isEmpty());
    }

    protected void collectAlerts(DataBus bus, List<String> warnings, List<String> cautions) {
        boolean airborne = !bus.getBoolean(Labels.SIM_ON_GROUND);
        if (!bus.has(Labels.IR_ALIGN_STATUS)) {
            cautions.add("NAV ADIRU FAULT");
        } else if (!"NAV".equals(bus.getString(Labels.IR_ALIGN_STATUS, ""))) {
            cautions.add("NAV IR NOT ALIGNED");
        }
        if (bus.has(Labels.ENG1_N1) && bus.getDouble(Labels.ENG1_N1, 0) < 10) {
            warnings.add("ENG 1 FAIL");
        }
        if (bus.has(Labels.ENG2_N1) && bus.getDouble(Labels.ENG2_N1, 0) < 10) {
            warnings.add("ENG 2 FAIL");
        }
        if (airborne && bus.getDouble(Labels.ADR_AOA, 0) > 14) {
            warnings.add("STALL");
        }
        if (bus.getDouble(Labels.ADR_IAS, 0) > 350) {
            warnings.add("OVERSPEED");
        }
    }
}
