package br.a320sim.host.command;

import br.a320sim.core.bus.Labels;
import br.a320sim.host.Host;

/** Põe o avião em voo nivelado onde ele está, mantendo a proa, e alinha os sensores. */
public record RepositionCommand(double altFt, double iasKt) implements InstructorCommand {

    @Override
    public String description() {
        return "Reposicionou em voo a " + Math.round(altFt) + " ft";
    }

    @Override
    public void execute(Host host) {
        double heading = host.bus().getDouble(Labels.TRUE_HDG, 0);
        host.flightModel().resetAirborne(altFt, iasKt, heading);
        new InstantAlignCommand().execute(host);
    }
}
