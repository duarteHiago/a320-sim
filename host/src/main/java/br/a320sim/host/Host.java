package br.a320sim.host;

import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import br.a320sim.core.module.AvionicsModule;
import br.a320sim.core.module.SelfTestResult;
import br.a320sim.flightmodel.FlightModel;
import br.a320sim.host.rack.Rack;
import br.a320sim.host.rack.RackReport;

import java.util.LinkedHashMap;
import java.util.Map;

/** O "supercomputador": dono do barramento, do rack, do modelo de voo e do relógio. */
public class Host {

    public static final double TICK_SECONDS = 1.0 / 60;

    private final DataBus bus = new DataBus();
    private final Rack rack = new Rack();
    private final FlightModel flightModel = new FlightModel(bus);
    private boolean powered;
    private double simTime;
    private double pending;

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

    /** Avança a simulação em passos fixos; sobras menores que um passo ficam para a próxima chamada. */
    public void advance(double seconds) {
        pending += seconds;
        while (pending >= TICK_SECONDS) {
            pending -= TICK_SECONDS;
            simTime += TICK_SECONDS;
            bus.publish(Labels.SIM_TIME, simTime);
            for (AvionicsModule module : rack.modules()) {
                module.tick(TICK_SECONDS);
            }
            flightModel.step(TICK_SECONDS);
        }
    }
}
