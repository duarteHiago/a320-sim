package br.a320sim.host.failure;

import br.a320sim.core.bus.DataBus;

/** ADIRU em pane: velocidade, altitude, atitude e proa somem. O aluno tem que voar olhando pela janela. */
public record SensorFailure() implements Failure {

    @Override
    public String name() {
        return "PANE NOS SENSORES";
    }

    @Override
    public void apply(DataBus bus) {
        bus.clear("IR.");
        bus.clear("ADR.");
    }
}
