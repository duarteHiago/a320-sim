package br.a320sim.host.command;

import br.a320sim.host.Host;

/** Liga ou desliga os bandos ao acaso perto dos aeroportos, abaixo de 3000 ft. */
public record SetAutoBirdsCommand(boolean on) implements InstructorCommand {

    @Override
    public String description() {
        return on ? "Aves automaticas ligadas" : "Aves automaticas desligadas";
    }

    @Override
    public void execute(Host host) {
        host.birds().setAuto(on, host.bus());
    }
}
