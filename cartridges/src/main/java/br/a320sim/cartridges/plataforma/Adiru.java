package br.a320sim.cartridges.plataforma;

import br.a320sim.core.bus.Labels;
import br.a320sim.core.module.BaseModule;
import br.a320sim.core.module.ModuleInfo;

import java.util.List;

/**
 * Sensores (ADIRU). Os dados de ar (velocidade, altitude) funcionam logo ao ligar;
 * os inerciais (atitude, proa, posição) só depois que as plataformas alinham.
 * Cada versão alinha num tempo diferente.
 */
public abstract class Adiru extends BaseModule {

    private final double alignSeconds;
    private double elapsed;
    private double instantAlignRequests;

    protected Adiru(String variant, double alignSeconds) {
        super(new ModuleInfo("IR", "Sensores", variant));
        this.alignSeconds = alignSeconds;
    }

    @Override
    protected List<String> labelPrefixes() {
        return List.of("IR.", "ADR.");
    }

    @Override
    protected void onPowerUp() {
        elapsed = 0;
        instantAlignRequests = bus.getDouble(Labels.SIM_IR_INSTANT_ALIGN, 0);
        bus.publish(Labels.IR_ALIGN_STATUS, "ALIGNING");
        bus.publish(Labels.IR_ALIGN_PROGRESS, 0.0);
    }

    @Override
    protected void onTick(double dt) {
        bus.publish(Labels.ADR_IAS, bus.getDouble(Labels.TRUE_IAS, 0));
        bus.publish(Labels.ADR_ALT, bus.getDouble(Labels.TRUE_ALT, 0));
        bus.publish(Labels.ADR_VS, bus.getDouble(Labels.TRUE_VS, 0));
        bus.publish(Labels.ADR_AOA, bus.getDouble(Labels.TRUE_AOA, 0));

        double requests = bus.getDouble(Labels.SIM_IR_INSTANT_ALIGN, 0);
        if (requests != instantAlignRequests) {
            instantAlignRequests = requests;
            elapsed = alignSeconds - dt;
        }
        elapsed = Math.min(alignSeconds, elapsed + dt);
        bus.publish(Labels.IR_ALIGN_PROGRESS, elapsed / alignSeconds);
        boolean aligned = elapsed >= alignSeconds;
        bus.publish(Labels.IR_ALIGN_STATUS, aligned ? "NAV" : "ALIGNING");
        if (!aligned) {
            return;
        }
        bus.publish(Labels.IR_PITCH, bus.getDouble(Labels.TRUE_PITCH, 0));
        bus.publish(Labels.IR_ROLL, bus.getDouble(Labels.TRUE_ROLL, 0));
        bus.publish(Labels.IR_HDG, bus.getDouble(Labels.TRUE_HDG, 0));
        bus.publish(Labels.IR_FPA, bus.getDouble(Labels.TRUE_FPA, 0));
        bus.publish(Labels.IR_GS, bus.getDouble(Labels.TRUE_GS, 0));
        bus.publish(Labels.IR_POS_X, bus.getDouble(Labels.TRUE_POS_X, 0));
        bus.publish(Labels.IR_POS_Y, bus.getDouble(Labels.TRUE_POS_Y, 0));
    }
}
