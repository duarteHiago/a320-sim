package br.a320sim.host.command;

import br.a320sim.flightmodel.FlightModel;
import br.a320sim.host.Host;

/** Põe o avião na final da pista 15: alinhado, a 5 milhas, na rampa de 3° (~1600 ft acima da pista). */
public record FinalApproachCommand() implements InstructorCommand {

    private static final double DISTANCE_NM = 5;
    private static final double HEIGHT_FT = 1600;
    private static final double SPEED_KT = 150;

    @Override
    public String description() {
        return "Reposicionou na final a 5 NM";
    }

    @Override
    public void execute(Host host) {
        double rwy = Math.toRadians(FlightModel.RUNWAY_HEADING);
        host.flightModel().resetAirborne(FlightModel.FIELD_ELEVATION_FT + HEIGHT_FT, SPEED_KT,
                FlightModel.RUNWAY_HEADING, -Math.sin(rwy) * DISTANCE_NM, -Math.cos(rwy) * DISTANCE_NM);
        new InstantAlignCommand().execute(host);
    }
}
