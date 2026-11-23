package br.a320sim.host;

import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import br.a320sim.flightmodel.FlightModel;
import br.a320sim.host.command.ApplyPositionCommand;
import br.a320sim.host.command.ResetToRunwayCommand;
import br.a320sim.host.session.FlightEvents;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightEventsTest {

    private final DataBus bus = new DataBus();
    private final FlightEvents events = new FlightEvents();

    private List<String> step(boolean onGround, boolean crashed, double vs) {
        bus.publish(Labels.SIM_ON_GROUND, onGround);
        bus.publish(Labels.SIM_CRASHED, crashed);
        bus.publish(Labels.TRUE_VS, vs);
        return events.update(bus);
    }

    @Test
    void decolagemQuandoSaiDoChao() {
        events.resync(bus);
        assertEquals(List.of(), step(true, false, 0));
        assertEquals(List.of("DECOLAGEM"), step(false, false, 900));
        assertEquals(List.of(), step(false, false, 1500), "uma vez so");
    }

    @Test
    void pousoComARazaoDeDescidaDoUltimoInstanteNoAr() {
        step(true, false, 0);
        step(false, false, 1000);
        step(false, false, -480);
        step(false, false, -620.4);
        // No passo do toque o modelo ja zerou a V/S
        assertEquals(List.of("POUSO V/S -620 FT/MIN"), step(true, false, 0));
    }

    @Test
    void crashNoLugarDoPouso() {
        step(false, false, -2400);
        assertEquals(List.of("CRASH"), step(true, true, 0));
        assertEquals(List.of(), step(true, true, 0));
    }

    @Test
    void colisaoViraEventoComOQueFoiAtingido() {
        step(false, false, -300);
        bus.publish(Labels.SIM_COLLISION, "ARVORE");
        assertEquals(List.of("COLISAO COM ARVORE"), step(true, true, 0));
    }

    @Test
    void colisaoComPredio() {
        step(false, false, 0);
        bus.publish(Labels.SIM_COLLISION, "PREDIO");
        assertEquals(List.of("COLISAO COM PREDIO"), step(true, true, 0));
    }

    @Test
    void resyncAceitaOEstadoSemEvento() {
        step(true, false, 0);
        bus.publish(Labels.SIM_ON_GROUND, false); // reposicionado em voo
        events.resync(bus);
        assertEquals(List.of(), step(false, false, 0));
    }

    // --- No host de verdade ---

    private final Host host = new Host();

    private List<String> descriptions() {
        return host.log().stream().map(Host.LoggedCommand::description).toList();
    }

    private void flyUntilOnGround(double maxSeconds) {
        for (double t = 0; t < maxSeconds && !host.flightModel().isOnGround(); t += 0.5) {
            host.advance(0.5);
        }
    }

    @Test
    void reposicionarEmVooEVoltarParaAPistaNaoViramDecolagemNemPouso() {
        host.execute(new ApplyPositionCommand("EM VOO 8000", 8000, 250, 148, 10));
        host.advance(1);
        host.execute(new ResetToRunwayCommand());
        host.advance(1);
        assertEquals(List.of("Posicao: EM VOO 8000", "Avião de volta à cabeceira"), descriptions());
    }

    @Test
    void planarAtePousarGravaOPousoNoLog() {
        // 30 ft acima da pista, sem motores (rack vazio): o avião perde velocidade e encosta
        host.execute(new ApplyPositionCommand("RENTE", FlightModel.FIELD_ELEVATION_FT + 30, 140, 148, 0.5));
        flyUntilOnGround(60);
        List<String> log = descriptions();
        assertEquals(2, log.size(), log.toString());
        assertTrue(log.get(1).matches("POUSO V/S -\\d+ FT/MIN"), log.toString());
    }

    @Test
    void pousarSemTremGravaCrash() {
        host.bus().publish(Labels.CTL_GEAR_DOWN, false);
        host.execute(new ApplyPositionCommand("RENTE", FlightModel.FIELD_ELEVATION_FT + 30, 140, 148, 0.5));
        flyUntilOnGround(60);
        assertEquals("CRASH", descriptions().get(descriptions().size() - 1));
    }
}
