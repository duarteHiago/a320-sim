package br.a320sim.host.command;

import br.a320sim.host.Host;

public record ResetToRunwayCommand() implements InstructorCommand {

    @Override
    public String description() {
        return "Avião de volta à cabeceira";
    }

    @Override
    public void execute(Host host) {
        host.flightModel().resetOnRunway();
    }
}
