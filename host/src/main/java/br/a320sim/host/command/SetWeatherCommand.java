package br.a320sim.host.command;

import br.a320sim.core.bus.Labels;
import br.a320sim.flightmodel.world.Runway;
import br.a320sim.host.Host;

import java.util.List;

/**
 * Muda o vento e a turbulência da sessão (vale para o modelo de voo a partir do próximo passo).
 *
 * @param fromDeg    de onde o vento sopra, na mesma referência das proas do simulador
 * @param turbulence 0 = nenhuma, 1 = leve, 2 = moderada, 3 = forte
 */
public record SetWeatherCommand(String name, double fromDeg, double speedKt, int turbulence)
        implements InstructorCommand {

    public static final SetWeatherCommand CALM = new SetWeatherCommand("CALMO", 0, 0, 0);

    /**
     * As predefinições da IOS, montadas para a pista de destino: calmo; 15 kt de través pela direita
     * com turbulência leve; 30 kt a 40° da pista com turbulência forte.
     */
    public static List<SetWeatherCommand> presets(Runway runway) {
        double heading = runway.headingDeg();
        return List.of(
                CALM,
                new SetWeatherCommand("TRAVES 15 KT", wrap(heading + 90), 15, 1),
                new SetWeatherCommand("FORTE 30 KT", wrap(heading + 40), 30, 3));
    }

    @Override
    public String description() {
        if (speedKt == 0) {
            return "Vento: " + name;
        }
        return String.format("Vento: %s (%03d/%d, turbulencia %d)", name, Math.round(fromDeg) % 360,
                Math.round(speedKt), turbulence);
    }

    @Override
    public void execute(Host host) {
        host.bus().publish(Labels.SIM_WIND_DIR, fromDeg);
        host.bus().publish(Labels.SIM_WIND_SPD, speedKt);
        host.bus().publish(Labels.SIM_TURBULENCE, (double) turbulence);
    }

    private static double wrap(double degrees) {
        return ((degrees % 360) + 360) % 360;
    }
}
