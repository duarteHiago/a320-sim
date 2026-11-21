package br.a320sim.host.command;

import br.a320sim.flightmodel.FlightModel;
import br.a320sim.host.Host;

/**
 * Põe o avião numa posição inicial cadastrada. Velocidade 0 e distância 0 = na cabeceira;
 * senão, em voo nivelado no prolongamento da pista 15, à distância indicada, com os sensores alinhados
 * (como o {@link FinalApproachCommand}).
 */
public record ApplyPositionCommand(String name, double altFt, double speedKt, double headingDeg, double distanceNm)
        implements InstructorCommand {

    public boolean onGround() {
        return speedKt == 0 && distanceNm == 0;
    }

    @Override
    public String description() {
        return "Posicao: " + name;
    }

    @Override
    public void execute(Host host) {
        if (onGround()) {
            host.flightModel().resetOnRunway();
            return;
        }
        // Antes da cabeceira, olhando para ela: o avião fica "atrás" na direção oposta à da pista
        double rwy = Math.toRadians(FlightModel.RUNWAY_HEADING);
        host.flightModel().resetAirborne(altFt, speedKt, headingDeg,
                -Math.sin(rwy) * distanceNm, -Math.cos(rwy) * distanceNm);
        new InstantAlignCommand().execute(host);
    }
}
