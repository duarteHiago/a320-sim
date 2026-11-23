package br.a320sim.host.command;

import br.a320sim.core.bus.Labels;
import br.a320sim.flightmodel.world.Runway;
import br.a320sim.flightmodel.world.World;
import br.a320sim.host.Host;

/**
 * Põe o avião numa posição inicial cadastrada, numa pista de um dos aeroportos.
 * Velocidade 0 e distância 0 = parado na cabeceira; senão, em voo nivelado no prolongamento da pista,
 * à distância indicada, com os sensores alinhados (como o {@link FinalApproachCommand}).
 * A pista vira o destino da sessão ({@code SIM.DEST_RUNWAY}).
 *
 * @param altFt altitude em relação ao nível do mar (ignorada no chão)
 */
public record ApplyPositionCommand(String name, double altFt, double speedKt, double headingDeg, double distanceNm,
                                   String airport, String runway) implements InstructorCommand {

    /** Na pista 15 de Viracopos, como antes de existirem outros aeroportos. */
    public ApplyPositionCommand(String name, double altFt, double speedKt, double headingDeg, double distanceNm) {
        this(name, altFt, speedKt, headingDeg, distanceNm, World.HOME.airport(), World.HOME.designator());
    }

    public boolean onGround() {
        return speedKt == 0 && distanceNm == 0;
    }

    /** A pista pedida; se ela não existe (cadastro antigo), a 15 de Viracopos. */
    public Runway target() {
        return World.runway(airport, runway).orElse(World.HOME);
    }

    @Override
    public String description() {
        return "Posicao: " + name;
    }

    @Override
    public void execute(Host host) {
        Runway target = target();
        host.bus().publish(Labels.SIM_DEST_RUNWAY, target.id());
        if (onGround()) {
            host.flightModel().resetOnRunway(target);
            return;
        }
        // No prolongamento do eixo, antes da cabeceira: voando na proa da pista, ela fica à frente
        double[] point = target.pointBefore(distanceNm);
        host.flightModel().resetAirborne(altFt, speedKt, headingDeg, point[0], point[1]);
        new InstantAlignCommand().execute(host);
    }
}
