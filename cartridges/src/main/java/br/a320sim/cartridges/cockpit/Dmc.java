package br.a320sim.cartridges.cockpit;

import br.a320sim.core.bus.Labels;
import br.a320sim.core.module.BaseModule;
import br.a320sim.core.module.ModuleInfo;

/** Telas (DMC). Na Fase 2 passa a desenhar PFD, ND e ECAM; por ora só anuncia o estilo. */
public abstract class Dmc extends BaseModule {

    private final String style;

    protected Dmc(String variant, String style) {
        super(new ModuleInfo("DMC", "Telas", variant));
        this.style = style;
    }

    @Override
    protected void onPowerUp() {
        bus.publish(Labels.DMC_STYLE, style);
    }

    @Override
    protected void onTick(double dt) {
    }
}
