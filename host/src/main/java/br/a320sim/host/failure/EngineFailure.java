package br.a320sim.host.failure;

import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;

/** Motor apagado: N1 vai a zero e o avião fica com metade do empuxo. */
public record EngineFailure(int engine) implements Failure {

    @Override
    public String name() {
        return "MOTOR " + engine + " PARADO";
    }

    @Override
    public void apply(DataBus bus) {
        String n1 = engine == 1 ? Labels.ENG1_N1 : Labels.ENG2_N1;
        String epr = engine == 1 ? Labels.ENG1_EPR : Labels.ENG2_EPR;
        if (bus.has(n1)) {
            bus.publish(n1, 0.0);
            bus.publish(epr, 1.0);
        }
    }
}
