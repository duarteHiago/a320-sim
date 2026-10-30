package br.a320sim.host.command;

import br.a320sim.core.bus.Labels;
import br.a320sim.host.Host;

public record InstantAlignCommand() implements InstructorCommand {

    @Override
    public String description() {
        return "Alinhamento instantâneo dos sensores";
    }

    @Override
    public void execute(Host host) {
        double requests = host.bus().getDouble(Labels.SIM_IR_INSTANT_ALIGN, 0);
        host.bus().publish(Labels.SIM_IR_INSTANT_ALIGN, requests + 1);
    }
}
