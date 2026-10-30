package br.a320sim.host;

import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import br.a320sim.core.module.AvionicsModule;
import br.a320sim.core.module.SelfTestResult;
import br.a320sim.flightmodel.FlightModel;
import br.a320sim.host.command.InstructorCommand;
import br.a320sim.host.failure.Failure;
import br.a320sim.host.rack.Rack;
import br.a320sim.host.rack.RackReport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** O "supercomputador": dono do barramento, do rack, do modelo de voo, das falhas e do relógio. */
public class Host {

    public static final double TICK_SECONDS = 1.0 / 60;

    private final DataBus bus = new DataBus();
    private final Rack rack = new Rack();
    private final FlightModel flightModel = new FlightModel(bus);
    private final Set<Failure> failures = new LinkedHashSet<>();
    private final List<LoggedCommand> log = new ArrayList<>();
    private boolean powered;
    private double simTime;
    private double pending;

    /** Um comando do instrutor e o instante da simulação em que ele aconteceu. */
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
            bus.publish(Labels.SIM_FREEZE, false);
            flightModel.resetOnRunway();
            for (AvionicsModule module : rack.modules()) {
                module.powerUp(bus);
                selfTests.put(module, module.selfTest());
            }
            powered = true;
        }
        return new PowerOnResult(report, selfTests);
    }

    public void powerOff() {
        rack.modules().forEach(AvionicsModule::powerDown);
        powered = false;
    }

    public void execute(InstructorCommand command) {
        command.execute(this);
        log.add(new LoggedCommand(simTime, command.description()));
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
        }
    }
}
