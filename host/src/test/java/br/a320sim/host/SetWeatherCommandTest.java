package br.a320sim.host;

import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import br.a320sim.flightmodel.world.Runway;
import br.a320sim.flightmodel.world.World;
import br.a320sim.host.command.ApplyPositionCommand;
import br.a320sim.host.command.SetWeatherCommand;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetWeatherCommandTest {

    private final Host host = new Host();
    private final DataBus bus = host.bus();

    @Test
    void predefinicoesSaoRelativasAPistaDeDestino() {
        Runway gru = World.runway("SBGR", "10L").orElseThrow();
        List<SetWeatherCommand> presets = SetWeatherCommand.presets(gru);
        assertEquals(List.of("CALMO", "TRAVES 15 KT", "FORTE 30 KT"), presets.stream().map(SetWeatherCommand::name).toList());
        assertEquals(gru.headingDeg() + 90, presets.get(1).fromDeg(), 1e-9, "atravessado, pela direita");
        assertEquals(1, presets.get(1).turbulence());
        assertEquals(30, presets.get(2).speedKt());
        assertEquals(3, presets.get(2).turbulence());
    }

    @Test
    void publicaNoBarramentoEFicaNoLog() {
        SetWeatherCommand crosswind = SetWeatherCommand.presets(World.HOME).get(1);
        host.execute(crosswind);
        assertEquals(238, bus.getDouble(Labels.SIM_WIND_DIR, 0), 1e-9);
        assertEquals(15, bus.getDouble(Labels.SIM_WIND_SPD, 0));
        assertEquals(1, bus.getDouble(Labels.SIM_TURBULENCE, 0));
        assertEquals("Vento: TRAVES 15 KT (238/15, turbulencia 1)", host.log().get(host.log().size() - 1).description());

        host.execute(SetWeatherCommand.CALM);
        assertEquals(0, bus.getDouble(Labels.SIM_WIND_SPD, 1));
        assertEquals("Vento: CALMO", host.log().get(host.log().size() - 1).description());
    }

    @Test
    void comVentoDeTravesOAviaoSaiDoEixoDaFinal() {
        host.execute(new ApplyPositionCommand("FINAL", 3770, 150, 148, 5));
        host.execute(SetWeatherCommand.presets(World.HOME).get(1));
        host.advance(30);
        double x = bus.getDouble(Labels.TRUE_POS_X, 0);
        double y = bus.getDouble(Labels.TRUE_POS_Y, 0);
        // Vento pela direita empurra para a esquerda do eixo
        assertTrue(World.HOME.acrossNm(x, y) < -0.05, "derivou " + World.HOME.acrossNm(x, y) + " NM");
    }
}
