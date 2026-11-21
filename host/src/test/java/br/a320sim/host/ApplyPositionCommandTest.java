package br.a320sim.host;

import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import br.a320sim.flightmodel.FlightModel;
import br.a320sim.flightmodel.world.Runway;
import br.a320sim.flightmodel.world.World;
import br.a320sim.host.command.ApplyPositionCommand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplyPositionCommandTest {

    private final Host host = new Host();
    private final DataBus bus = host.bus();

    @Test
    void emVooFicaNoProlongamentoDaPistaADistanciaIndicada() {
        host.execute(new ApplyPositionCommand("EM VOO 8000", 8000, 250, 148, 10));

        assertFalse(host.flightModel().isOnGround());
        assertEquals(8000, bus.getDouble(Labels.TRUE_ALT, 0), 1);
        assertEquals(148, bus.getDouble(Labels.TRUE_HDG, 0), 0.01);
        assertEquals(250, bus.getDouble(Labels.TRUE_IAS, 0), 1);
        double x = bus.getDouble(Labels.TRUE_POS_X, 0);
        double y = bus.getDouble(Labels.TRUE_POS_Y, 0);
        assertEquals(10, Math.hypot(x, y), 1e-6, "10 NM da cabeceira");
        // Voando na proa da pista, a cabeceira está à frente: o avião está do lado oposto
        double rwy = Math.toRadians(FlightModel.RUNWAY_HEADING);
        assertEquals(-10, x * Math.sin(rwy) + y * Math.cos(rwy), 1e-6, "no prolongamento, antes da pista");
    }

    @Test
    void emVooAlinhaOsSensoresNaHora() {
        double before = bus.getDouble(Labels.SIM_IR_INSTANT_ALIGN, 0);
        host.execute(new ApplyPositionCommand("NA FINAL 5NM", 3770, 150, 148, 5));
        assertEquals(before + 1, bus.getDouble(Labels.SIM_IR_INSTANT_ALIGN, 0));
    }

    @Test
    void noChaoVoltaParaACabeceira() {
        host.execute(new ApplyPositionCommand("EM VOO 8000", 8000, 250, 148, 10));
        double aligns = bus.getDouble(Labels.SIM_IR_INSTANT_ALIGN, 0);
        host.execute(new ApplyPositionCommand("PISTA 15", 2170, 0, 148, 0));

        assertTrue(host.flightModel().isOnGround());
        assertEquals(0, bus.getDouble(Labels.TRUE_POS_X, 1), 1e-9);
        assertEquals(0, bus.getDouble(Labels.TRUE_POS_Y, 1), 1e-9);
        assertEquals(FlightModel.RUNWAY_HEADING, bus.getDouble(Labels.TRUE_HDG, 0), 1e-9);
        assertEquals(aligns, bus.getDouble(Labels.SIM_IR_INSTANT_ALIGN, 0), "no chao o aluno espera o alinhamento");
    }

    @Test
    void ficaRegistradoNoLog() {
        host.execute(new ApplyPositionCommand("NA FINAL 5NM", 3770, 150, 148, 5));
        assertEquals("Posicao: NA FINAL 5NM", host.log().get(host.log().size() - 1).description());
    }

    @Test
    void noChaoEmGuarulhosFicaNaCabeceiraDaPistaEscolhida() {
        host.execute(new ApplyPositionCommand("PISTA GRU 10L", 2459, 0, 94, 0, "SBGR", "10L"));
        Runway gru = World.runway("SBGR", "10L").orElseThrow();
        assertTrue(host.flightModel().isOnGround());
        assertEquals(gru.xNm(), bus.getDouble(Labels.TRUE_POS_X, 0), 1e-9);
        assertEquals(gru.yNm(), bus.getDouble(Labels.TRUE_POS_Y, 0), 1e-9);
        assertEquals(gru.headingDeg(), bus.getDouble(Labels.TRUE_HDG, 0), 1e-9);
        assertEquals(2459, bus.getDouble(Labels.TRUE_ALT, 0), 1e-6);
        assertEquals("SBGR 10L", bus.getString(Labels.SIM_DEST_RUNWAY, ""));
    }

    @Test
    void naFinalDeCongonhasFicaNoProlongamentoDa17R() {
        Runway cgh = World.runway("SBSP", "17R").orElseThrow();
        host.execute(new ApplyPositionCommand("FINAL CGH 17R", 2631 + 1600, 150, cgh.headingDeg(), 5, "SBSP", "17R"));
        double x = bus.getDouble(Labels.TRUE_POS_X, 0);
        double y = bus.getDouble(Labels.TRUE_POS_Y, 0);
        assertEquals(-5, cgh.alongNm(x, y), 1e-6, "5 NM antes da cabeceira");
        assertEquals(0, cgh.acrossNm(x, y), 1e-6, "no eixo");
        assertEquals(1600, bus.getDouble(Labels.SIM_RADIO_ALT, 0), 1);
        assertEquals("SBSP 17R", bus.getString(Labels.SIM_DEST_RUNWAY, ""));
    }

    @Test
    void sentidoContrarioUsaAOutraCabeceira() {
        host.execute(new ApplyPositionCommand("PISTA 33", 2170, 0, 328, 0, "SBKP", "33"));
        Runway r33 = World.runway("SBKP", "33").orElseThrow();
        assertEquals(r33.xNm(), bus.getDouble(Labels.TRUE_POS_X, 0), 1e-9);
        assertEquals(328, bus.getDouble(Labels.TRUE_HDG, 0), 1e-6);
    }

    @Test
    void pistaDesconhecidaCaiNaCabeceira15() {
        host.execute(new ApplyPositionCommand("ANTIGA", 2170, 0, 148, 0, "SBGR", "09L"));
        assertEquals(0, bus.getDouble(Labels.TRUE_POS_X, 1), 1e-9);
        assertEquals("SBKP 15", bus.getString(Labels.SIM_DEST_RUNWAY, ""));
    }
}
