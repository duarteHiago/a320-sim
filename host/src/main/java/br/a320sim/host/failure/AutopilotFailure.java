package br.a320sim.host.failure;

import br.a320sim.core.bus.DataBus;

/** Piloto automático inoperante: desengaja e não volta enquanto a falha estiver ativa. */
public record AutopilotFailure() implements Failure {

    @Override
    public String name() {
        return "PILOTO AUTOMATICO INOP";
    }

    @Override
    public void apply(DataBus bus) {
        bus.clear("AP.");
    }
}
