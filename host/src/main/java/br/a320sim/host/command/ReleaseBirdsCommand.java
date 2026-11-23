package br.a320sim.host.command;

import br.a320sim.host.Host;

/** Solta um bando de aves cruzando o caminho do avião, logo à frente. */
public record ReleaseBirdsCommand() implements InstructorCommand {

    @Override
    public String description() {
        return "Bando de aves a frente";
    }

    @Override
    public void execute(Host host) {
        host.birds().release(host.bus());
    }
}
