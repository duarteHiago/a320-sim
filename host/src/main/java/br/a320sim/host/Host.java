package br.a320sim.host;

import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import br.a320sim.core.cartridge.Cartridge;
import br.a320sim.core.cartridge.CartridgeGroup;
import br.a320sim.core.module.AvionicsModule;
import br.a320sim.core.module.SelfTestResult;
import br.a320sim.flightmodel.FlightModel;
import br.a320sim.host.command.InstructorCommand;
import br.a320sim.host.failure.Failure;
import br.a320sim.host.rack.Rack;
import br.a320sim.host.rack.RackReport;
import br.a320sim.host.session.FlightEvents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** O "supercomputador": dono do barramento, do rack, do modelo de voo, das falhas e do relógio. */
public class Host {

    public static final double TICK_SECONDS = 1.0 / 60;

    private final DataBus bus = new DataBus();
    private final Rack rack = new Rack();
    private final FlightModel flightModel = new FlightModel(bus);
    private final Set<Failure> failures = new LinkedHashSet<>();
    private final List<LoggedCommand> log = new ArrayList<>();
    private final FlightEvents flightEvents = new FlightEvents();
    private boolean powered;
    private double simTime;
    private double pending;

    /** Um comando do instrutor (ou um evento do voo, como DECOLAGEM) e o instante da simulação em que aconteceu. */
    public record LoggedCommand(double simTime, String description) {
    }

    public DataBus bus() {
        return bus;
    }

    public Rack rack() {
        return rack;
    }

    public FlightModel flightModel() {
        return flightModel;
    }

    public boolean isPowered() {
        return powered;
    }

    public double simTime() {
        return simTime;
    }

    public record PowerOnResult(RackReport report, Map<AvionicsModule, SelfTestResult> selfTests) {
    }

    /** Valida o rack e, se estiver tudo certo, põe o avião na pista, liga e testa cada caixa. */
    public PowerOnResult powerOn() {
        Map<AvionicsModule, SelfTestResult> selfTests = new LinkedHashMap<>();
        RackReport report = rack.validate();
        if (report.ok()) {
            simTime = 0;
            pending = 0;
            failures.clear();
            log.clear();
            bus.publish(Labels.SIM_WIND_DIR, 0.0); // cada sessão começa sem vento
            bus.publish(Labels.SIM_WIND_SPD, 0.0);
            bus.publish(Labels.SIM_TURBULENCE, 0.0);
            bus.publish(Labels.SIM_FREEZE, false);
            flightModel.resetOnRunway();
            flightEvents.resync(bus);
            for (AvionicsModule module : rack.modules()) {
                module.powerUp(bus);
                selfTests.put(module, module.selfTest());
            }
            powered = true;
            publishMissingSlots();
        }
        return new PowerOnResult(report, selfTests);
    }

    public void powerOff() {
        rack.modules().forEach(AvionicsModule::powerDown);
        powered = false;
        bus.clear(Labels.RACK_MISSING);
    }

    public enum HotInsertResult { LOADED, SLOT_OCCUPIED, INCOMPATIBLE }

    /**
     * Tira o cartucho de um slot. Com o avião ligado, as caixas dele desligam na hora
     * (somem do barramento) e o resto do avião segue sem elas.
     */
    public Optional<Cartridge> ejectCartridge(CartridgeGroup group) {
        Optional<Cartridge> removed = rack.remove(group);
        if (removed.isPresent() && powered) {
            removed.get().modules().forEach(AvionicsModule::powerDown);
            log.add(new LoggedCommand(simTime, "CARTUCHO " + group + " REMOVIDO"));
            publishMissingSlots();
        }
        return removed;
    }

    /** Encaixa um cartucho num slot vazio; com o avião ligado, as caixas fazem power-up e self-test. */
    public HotInsertResult hotInsert(Cartridge cartridge) {
        if (rack.insert(cartridge).isPresent()) {
            return HotInsertResult.SLOT_OCCUPIED;
        }
        if (!rack.incompatibilities().isEmpty()) {
            rack.remove(cartridge.group());
            return HotInsertResult.INCOMPATIBLE;
        }
        if (powered) {
            for (AvionicsModule module : cartridge.modules()) {
                module.powerUp(bus);
                module.selfTest();
            }
            log.add(new LoggedCommand(simTime, "CARTUCHO " + cartridge.manifest().partNumber() + " CARREGADO"));
            publishMissingSlots();
        }
        return HotInsertResult.LOADED;
    }

    private void publishMissingSlots() {
        List<String> missing = new ArrayList<>();
        for (CartridgeGroup group : CartridgeGroup.values()) {
            if (rack.slot(group).isEmpty()) {
                missing.add(group.name());
            }
        }
        bus.publish(Labels.RACK_MISSING, missing);
    }

    public void execute(InstructorCommand command) {
        command.execute(this);
        flightEvents.resync(bus); // reposicionar não é decolar nem pousar
        log.add(new LoggedCommand(simTime, command.description()));
    }

    /** Escreve no log algo que aconteceu no voo (não é um comando). */
    public void logEvent(String description) {
        log.add(new LoggedCommand(simTime, description));
    }

    public List<LoggedCommand> log() {
        return Collections.unmodifiableList(log);
    }

    public void toggleFailure(Failure failure) {
        if (!failures.remove(failure)) {
            failures.add(failure);
        }
        bus.publish(Labels.IOS_FAILURES, failures.stream().map(Failure::name).toList());
    }

    public boolean hasFailure(Failure failure) {
        return failures.contains(failure);
    }

    /** Avança a simulação em passos fixos; sobras menores que um passo ficam para a próxima chamada. */
    public void advance(double seconds) {
        pending += seconds;
        while (pending >= TICK_SECONDS) {
            pending -= TICK_SECONDS;
            simTime += TICK_SECONDS;
            bus.publish(Labels.SIM_TIME, simTime);
            for (AvionicsModule module : rack.modules()) {
                module.tick(TICK_SECONDS);
                // As falhas agem depois de cada caixa, assim quem lê o barramento depois já vê a pane
                failures.forEach(failure -> failure.apply(bus));
            }
            flightModel.step(TICK_SECONDS);
            flightEvents.update(bus).forEach(this::logEvent);
        }
    }
}
