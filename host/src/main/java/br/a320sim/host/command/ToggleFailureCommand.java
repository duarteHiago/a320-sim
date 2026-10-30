package br.a320sim.host.command;

import br.a320sim.host.Host;
import br.a320sim.host.failure.Failure;

public record ToggleFailureCommand(Failure failure) implements InstructorCommand {

    @Override
    public String description() {
        return "Falha " + failure.name();
    }

    @Override
    public void execute(Host host) {
        host.toggleFailure(failure);
    }
}
