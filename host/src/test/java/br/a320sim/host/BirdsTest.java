package br.a320sim.host;

import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import br.a320sim.flightmodel.world.Runway;
import br.a320sim.flightmodel.world.World;
import br.a320sim.host.birds.BirdFlock;
import br.a320sim.host.birds.Birds;
import br.a320sim.host.cartridge.CartridgeLibrary;
import br.a320sim.host.cartridge.CartridgeLoader;
import br.a320sim.host.cartridge.ClasspathModuleFactory;
import br.a320sim.host.cartridge.DevFolderReader;
import br.a320sim.host.command.ApplyPositionCommand;
import br.a320sim.host.command.FreezeCommand;
import br.a320sim.host.command.ReleaseBirdsCommand;
import br.a320sim.host.command.SetAutoBirdsCommand;
import br.a320sim.host.failure.EngineFailure;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BirdsTest {

    private final Host host = new Host();
    private final DataBus bus = host.bus();

    /** Em voo nivelado para leste, a 5000 ft e 250 kt, perto de Viracopos (empuxo fixo, sem cartuchos). */
    private void flyEast() {
        host.flightModel().resetAirborne(5000, 250, 90, 0, -3);
        bus.publish(Labels.ENG1_N1, 62.0);
        bus.publish(Labels.ENG2_N1, 62.0);
        host.advance(Host.TICK_SECONDS);
    }

    /** Põe um bando parado logo à frente do avião, deslocado {@code acrossNm} para a direita. */
    private void flockAhead(double acrossNm, double altOffsetFt) {
        double x = bus.getDouble(Labels.TRUE_POS_X, 0);
        double y = bus.getDouble(Labels.TRUE_POS_Y, 0);
        double alt = bus.getDouble(Labels.TRUE_ALT, 0);
        // Proa 090: à frente é +x, à direita é -y
        host.birds().place(new BirdFlock(x + 0.3, y - acrossNm, alt + altOffsetFt, 0, 0), bus);
    }

    private List<String> log() {
        return host.log().stream().map(Host.LoggedCommand::description).toList();
    }

    @Test
    void bandoPeloMeioParaOsDoisMotores() {
        flyEast();
        flockAhead(0, 0);
        host.advance(10);
        assertTrue(host.hasFailure(new EngineFailure(1)));
        assertTrue(host.hasFailure(new EngineFailure(2)));
        assertTrue(log().contains("COLISAO COM AVES (MOTORES 1 E 2)"), log().toString());
        assertTrue(host.birds().flock().isEmpty(), "o bando se espalha");
    }

    @Test
    void bandoDoLadoDireitoParaOMotor2() {
        flyEast();
        flockAhead(0.05, 0);
        host.advance(10);
        assertTrue(host.hasFailure(new EngineFailure(2)));
        assertFalse(host.hasFailure(new EngineFailure(1)));
        assertTrue(log().contains("COLISAO COM AVES (MOTOR 2)"));
    }

    @Test
    void bandoDoLadoEsquerdoParaOMotor1() {
        flyEast();
        flockAhead(-0.05, 0);
        host.advance(10);
        assertTrue(host.hasFailure(new EngineFailure(1)));
        assertFalse(host.hasFailure(new EngineFailure(2)));
    }

    @Test
    void passarPorCimaOuDoLadoNaoBate() {
        flyEast();
        flockAhead(0, 400);
        host.advance(10);
        flockAhead(0.2, 0);
        host.advance(10);
        assertFalse(host.hasFailure(new EngineFailure(1)));
        assertFalse(host.hasFailure(new EngineFailure(2)));
    }

    @Test
    void motorJaParadoContinuaParado() {
        flyEast();
        host.toggleFailure(new EngineFailure(1));
        flockAhead(0, 0);
        host.advance(10);
        assertTrue(host.hasFailure(new EngineFailure(1)), "a colisao nao religa o motor 1");
        assertTrue(host.hasFailure(new EngineFailure(2)));
    }

    @Test
    void botaoAvesSoltaUmBandoQueCruzaOCaminho() {
        flyEast();
        host.execute(new ReleaseBirdsCommand());
        assertTrue(bus.getBoolean(Labels.SIM_BIRDS), "o bando vai para o barramento (o aluno ve)");
        host.advance(Birds.AHEAD_NM / 250 * 3600 + 5);
        assertTrue(host.hasFailure(new EngineFailure(1)) || host.hasFailure(new EngineFailure(2)),
                "voando reto, o aviao atravessa o bando: " + log());
        assertTrue(log().contains("Bando de aves a frente"));
    }

    @Test
    void pausadoOBandoNaoAnda() {
        flyEast();
        host.execute(new ReleaseBirdsCommand());
        double x = bus.getDouble(Labels.SIM_BIRDS_X, 0);
        host.execute(new FreezeCommand(true));
        host.advance(5);
        assertEquals(x, bus.getDouble(Labels.SIM_BIRDS_X, 1), 0);
    }

    @Test
    void avesAutomaticasDesligadasNaoSoltamNada() {
        Runway home = World.HOME;
        host.execute(new ApplyPositionCommand("FINAL", 2170 + 1500, 160, home.headingDeg(), 6));
        for (int i = 0; i < 300; i++) {
            host.advance(1);
            assertFalse(bus.getBoolean(Labels.SIM_BIRDS));
        }
    }

    @Test
    void avesAutomaticasPertoDoAeroportoEBaixo() {
        host.execute(new SetAutoBirdsCommand(true));
        assertTrue(bus.getBoolean(Labels.SIM_BIRDS_AUTO));
        host.execute(new ApplyPositionCommand("FINAL", 2170 + 2000, 200, World.HOME.headingDeg(), 7));
        boolean released = false;
        for (int i = 0; i < 300 && !released; i++) {
            host.advance(1);
            released = bus.getBoolean(Labels.SIM_BIRDS);
        }
        assertTrue(released, "em 5 minutos, com media de 1 bando por minuto");
        assertTrue(log().contains("Aves automaticas ligadas"));
    }

    @Test
    void avesAutomaticasNaoAparecemNoAltoNemLonge() {
        host.execute(new SetAutoBirdsCommand(true));
        host.flightModel().resetAirborne(15_000, 250, 90, 0, -3); // alto
        for (int i = 0; i < 300; i++) {
            host.advance(1);
            assertFalse(bus.getBoolean(Labels.SIM_BIRDS), "alto demais");
        }
        host.flightModel().resetAirborne(4000, 200, 270, -30, 10); // baixo, mas a 30 NM de tudo
        for (int i = 0; i < 300; i++) {
            host.advance(1);
            assertFalse(bus.getBoolean(Labels.SIM_BIRDS), "longe dos aeroportos");
        }
    }

    @Test
    void ligarOAviaoDesligaAsAvesAutomaticas() {
        CartridgeLibrary library = new CartridgeLibrary(new DevFolderReader(Path.of("sd-dev")),
                new CartridgeLoader(new ClasspathModuleFactory()));
        for (String pn : new String[]{"PLT-A320-NEO", "PRP-A320-NEO", "CKP-A320-NEO"}) {
            host.rack().insert(library.load(library.find(pn).orElseThrow()));
        }
        host.execute(new SetAutoBirdsCommand(true));
        assertTrue(host.powerOn().report().ok());
        assertFalse(host.birds().isAuto());
        assertFalse(bus.getBoolean(Labels.SIM_BIRDS_AUTO));
    }
}
