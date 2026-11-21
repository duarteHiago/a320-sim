package br.a320sim.flightmodel;

import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import br.a320sim.flightmodel.world.Airport;
import br.a320sim.flightmodel.world.Geo;
import br.a320sim.flightmodel.world.Runway;
import br.a320sim.flightmodel.world.World;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldTest {

    private static Runway runway(String icao, String designator) {
        return World.runway(icao, designator).orElseThrow();
    }

    @Test
    void cabeceira15DeViracoposEAOrigemComAProaDeSempre() {
        Runway home = runway("SBKP", "15");
        assertEquals(0, home.xNm(), 1e-9);
        assertEquals(0, home.yNm(), 1e-9);
        assertEquals(FlightModel.RUNWAY_HEADING, home.headingDeg(), 1e-9);
        assertEquals(FlightModel.FIELD_ELEVATION_FT, World.SBKP.elevationFt());
        assertEquals(home, World.HOME);
    }

    @Test
    void converteCoordenadasMantendoDistancias() {
        // A pista 15/33 tem 3240 m entre as cabeceiras publicadas
        double[] end = Geo.toWorld(-23.0164, -47.1220);
        assertEquals(3240, Math.hypot(end[0], end[1]) * Runway.METERS_PER_NM, 15);
        // A rotação do mapa é a declinação magnética da região (~20° W)
        assertEquals(20, Geo.ROTATION_DEG, 1.5);
    }

    @Test
    void guarulhosECongonhasFicamOndeDeveriam() {
        double[] gru = World.SBGR.center();
        double[] cgh = World.SBSP.center();
        assertEquals(45.5, Math.hypot(gru[0], gru[1]), 1, "SBKP-SBGR ~84 km");
        assertEquals(46.5, Math.hypot(cgh[0], cgh[1]), 1, "SBKP-SBSP ~86 km");
        assertTrue(gru[0] > 0 && gru[1] < 0, "a sudeste de Viracopos");
        assertEquals(14.5, Math.hypot(gru[0] - cgh[0], gru[1] - cgh[1]), 1.5, "SBGR-SBSP ~27 km");
    }

    @Test
    void proasProximasDasMagneticasPublicadas() {
        assertEquals(94, runway("SBGR", "10L").headingDeg(), 2.5);
        assertEquals(274, runway("SBGR", "28R").headingDeg(), 2.5);
        assertEquals(168, runway("SBSP", "17R").headingDeg(), 2.5);
        assertEquals(348, runway("SBSP", "35L").headingDeg(), 2.5);
    }

    @Test
    void paralelasComOAfastamentoReal() {
        Runway gru10L = runway("SBGR", "10L");
        Runway gru10R = runway("SBGR", "10R");
        assertEquals(375, gru10L.acrossNm(gru10R.xNm(), gru10R.yNm()) * Runway.METERS_PER_NM, 30, "10R ao sul (direita)");
        Runway cgh17R = runway("SBSP", "17R");
        Runway cgh17L = runway("SBSP", "17L");
        assertEquals(-210, cgh17R.acrossNm(cgh17L.xNm(), cgh17L.yNm()) * Runway.METERS_PER_NM, 30, "17L a leste (esquerda)");
    }

    @Test
    void cadaPistaTemOsDoisSentidos() {
        assertEquals(List.of("10L", "28R", "10R", "28L"), World.SBGR.designators());
        assertEquals(List.of("17R", "35L", "17L", "35R"), World.SBSP.designators());
        assertEquals(List.of("15", "33"), World.SBKP.designators());
        Runway r28 = runway("SBGR", "28R");
        Runway r10 = runway("SBGR", "10L");
        assertEquals(r10.endXNm(), r28.xNm(), 1e-9, "a cabeceira 28R e o fim da 10L");
        assertTrue(World.runway("SBGR", "09L").isEmpty(), "numeracao antiga nao existe mais");
        assertTrue(World.runway("SBXX", "10L").isEmpty());
    }

    @Test
    void terrenoPlanoNosAeroportosESuaveEntreEles() {
        for (Airport a : World.AIRPORTS) {
            for (Runway r : a.runways()) {
                assertEquals(a.elevationFt(), World.terrainFt(r.xNm(), r.yNm()), 1e-9, r.id());
            }
        }
        double previous = World.terrainFt(0, 0);
        double[] gru = World.SBGR.center();
        for (int i = 1; i <= 100; i++) {
            double t = i / 100.0;
            double h = World.terrainFt(gru[0] * t, gru[1] * t);
            assertTrue(Math.abs(h - previous) < 30, "sem degrau no caminho SBKP-SBGR");
            assertTrue(h >= 2170 - 1e-9 && h <= 2631 + 1e-9);
            previous = h;
        }
    }

    // --- o modelo de voo no terreno ---

    private final DataBus bus = new DataBus();
    private final FlightModel model = new FlightModel(bus);

    @Test
    void naCabeceiraDeCongonhasOAltimetroMarcaAElevacaoDela() {
        model.resetOnRunway(runway("SBSP", "17R"));
        assertEquals(2631, bus.getDouble(Labels.TRUE_ALT, 0), 1e-6);
        assertEquals(0, bus.getDouble(Labels.SIM_RADIO_ALT, -1), 1e-6);
        assertEquals(runway("SBSP", "17R").headingDeg(), bus.getDouble(Labels.TRUE_HDG, 0), 1e-9);
    }

    @Test
    void altitudeDeRadioEemRelacaoAoTerrenoEmbaixo() {
        Runway gru = runway("SBGR", "10L");
        double[] p = gru.pointBefore(5);
        model.resetAirborne(4059, 150, gru.headingDeg(), p[0], p[1]);
        assertEquals(4059, bus.getDouble(Labels.TRUE_ALT, 0), 1e-6);
        assertEquals(1600, bus.getDouble(Labels.SIM_RADIO_ALT, 0), 1, "5 NM da pista, chao plano de GRU");
    }

    @Test
    void tocaOChaoNaAlturaDeGuarulhosNaoNaDeViracopos() {
        Runway gru = runway("SBGR", "10L");
        double[] p = gru.pointBefore(0.3);
        model.resetAirborne(2459 + 40, 140, gru.headingDeg(), p[0], p[1]);
        for (int i = 0; i < 60 * 60 && !model.isOnGround(); i++) {
            model.step(1.0 / 60);
        }
        // (sem flaps, a 140 kt e no ar mais rarefeito de GRU o avião estola: o toque é duro, e tudo bem;
        // o que interessa aqui é que ele toca na elevação de Guarulhos)
        assertTrue(model.isOnGround());
        assertEquals(2459, bus.getDouble(Labels.TRUE_ALT, 0), 1e-6);
        assertEquals(0, bus.getDouble(Labels.SIM_RADIO_ALT, -1), 1e-6);
    }

    @Test
    void aeronaveAbaixoDoTerrenoNoReposicionamentoFicaNoChao() {
        model.resetAirborne(1000, 200, 90, 0, -10);
        assertTrue(bus.getDouble(Labels.TRUE_ALT, 0) >= World.terrainFt(0, -10) - 1e-6);
    }
}
