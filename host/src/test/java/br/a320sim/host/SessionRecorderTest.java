package br.a320sim.host;

import br.a320sim.host.plan.FlightPlan;
import br.a320sim.core.bus.Labels;
import br.a320sim.host.cartridge.CartridgeLibrary;
import br.a320sim.host.cartridge.CartridgeLoader;
import br.a320sim.host.cartridge.ClasspathModuleFactory;
import br.a320sim.host.cartridge.DevFolderReader;
import br.a320sim.host.command.ResetToRunwayCommand;
import br.a320sim.host.command.SendMessageCommand;
import br.a320sim.host.session.SessionRecorder;
import br.a320sim.host.session.SessionStore;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionRecorderTest {

    /** Store de mentira: guarda o que recebeu e pode fingir que o banco caiu. */
    private static final class FakeStore implements SessionStore {
        final List<List<String>> batches = new ArrayList<>();
        boolean failing;
        int closes;
        double simSeconds;
        boolean crashed;
        List<String> cartridges;

        @Override
        public void appendEvents(List<Host.LoggedCommand> events) {
            if (failing) {
                throw new IllegalStateException("banco fora do ar");
            }
            batches.add(events.stream().map(Host.LoggedCommand::description).toList());
        }

        @Override
        public void close(double simSeconds, boolean crashed, List<String> cartridges) {
            closes++;
            this.simSeconds = simSeconds;
            this.crashed = crashed;
            this.cartridges = cartridges;
        }
    }

    private final Host host = new Host();
    private final FakeStore store = new FakeStore();

    private void bootNeo() {
        CartridgeLibrary library = new CartridgeLibrary(new DevFolderReader(Path.of("sd-dev")),
                new CartridgeLoader(new ClasspathModuleFactory()));
        for (String pn : new String[]{"PLT-A320-NEO", "PRP-A320-NEO", "CKP-A320-NEO"}) {
            host.rack().insert(library.load(library.find(pn).orElseThrow()));
        }
        assertTrue(host.powerOn().report().ok());
    }

    @Test
    void gravaOsEventosAosPoucosNaoSoNoFim() {
        bootNeo();
        SessionRecorder recorder = new SessionRecorder(host, store);
        host.execute(new SendMessageCommand("bom dia"));
        recorder.update(0.5);
        assertTrue(store.batches.isEmpty(), "ainda nao deu um segundo");
        recorder.update(0.6);
        assertEquals(List.of(List.of("Mensagem: bom dia")), store.batches);

        host.execute(new ResetToRunwayCommand());
        recorder.update(1.0);
        recorder.update(1.0); // nada novo: nao manda lote vazio
        assertEquals(2, store.batches.size());
        assertEquals(List.of("Avião de volta à cabeceira"), store.batches.get(1));
    }

    @Test
    void fecharGravaORestoComDuracaoECartuchos() {
        bootNeo();
        SessionRecorder recorder = new SessionRecorder(host, store);
        host.advance(12.5);
        host.execute(new SendMessageCommand("fim"));
        recorder.close();
        recorder.close();

        assertEquals(List.of(List.of("Mensagem: fim")), store.batches);
        assertEquals(1, store.closes, "fechar duas vezes nao fecha de novo");
        assertEquals(12.5, store.simSeconds, Host.TICK_SECONDS);
        assertFalse(store.crashed);
        assertEquals(List.of("PLT-A320-NEO", "PRP-A320-NEO", "CKP-A320-NEO"), store.cartridges);
        assertTrue(recorder.isClosed());
    }

    @Test
    void crashFicaMarcadoMesmoDepoisDoReset() {
        SessionRecorder recorder = new SessionRecorder(host, store);
        host.bus().publish(Labels.SIM_CRASHED, true);
        recorder.update(0.1);
        host.execute(new ResetToRunwayCommand());
        recorder.close();
        assertTrue(store.crashed);
    }

    @Test
    void bancoFalhandoNaoPerdeEventos() {
        SessionRecorder recorder = new SessionRecorder(host, store);
        host.execute(new SendMessageCommand("um"));
        store.failing = true;
        recorder.update(1.0);
        host.execute(new SendMessageCommand("dois"));
        store.failing = false;
        recorder.update(1.0);
        assertEquals(List.of(List.of("Mensagem: um", "Mensagem: dois")), store.batches);
    }

    @Test
    void planoDeVooVaiParaOStoreUmaVezPorMudanca() {
        List<FlightPlan> plans = new ArrayList<>();
        SessionStore store = new SessionStore() {
            @Override
            public void appendEvents(List<Host.LoggedCommand> events) {
            }

            @Override
            public void savePlan(FlightPlan plan) {
                plans.add(plan);
            }

            @Override
            public void close(double simSeconds, boolean crashed, List<String> cartridges) {
            }
        };
        SessionRecorder recorder = new SessionRecorder(host, store);
        FlightPlan plan = new FlightPlan("SBKP", "SBGR", "10L", 100, 60_000, 8_000, 140, 143, 148);
        plan.publish(host.bus());
        host.advance(0.1);
        recorder.update(0.1);
        recorder.update(0.1);
        assertEquals(List.of(plan), plans);
        FlightPlan changed = new FlightPlan("SBKP", "SBGR", "28R", 100, 60_000, 8_000, 140, 143, 148);
        changed.publish(host.bus());
        host.advance(0.1);
        recorder.update(0.1);
        assertEquals(List.of(plan, changed), plans);
    }
}
