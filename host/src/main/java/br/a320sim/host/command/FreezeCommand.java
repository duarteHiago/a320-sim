package br.a320sim.host.command;

import br.a320sim.core.bus.Labels;
import br.a320sim.host.Host;

public record FreezeCommand(boolean frozen) implements InstructorCommand {

    @Override
    public String description() {
        return frozen ? "Pausou a simulação" : "Retomou a simulação";
    }

    @Override
    public void execute(Host host) {
        host.bus().publish(Labels.SIM_FREEZE, frozen);
    }
}
