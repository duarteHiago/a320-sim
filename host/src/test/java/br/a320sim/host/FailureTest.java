package br.a320sim.host;

import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import br.a320sim.host.cartridge.CartridgeLibrary;
import br.a320sim.host.cartridge.CartridgeLoader;
import br.a320sim.host.cartridge.ClasspathModuleFactory;
import br.a320sim.host.cartridge.DevFolderReader;
import br.a320sim.host.command.FinalApproachCommand;
import br.a320sim.host.command.ToggleFailureCommand;
import br.a320sim.host.failure.AutopilotFailure;
import br.a320sim.host.failure.EngineFailure;
import br.a320sim.host.failure.SensorFailure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** As falhas do instrutor chegam às caixas certas e somem quando removidas. */
class FailureTest {

    private final Host host = new Host();
    private final DataBus bus = host.bus();

    @BeforeEach
    void setUp() {
        CartridgeLibrary library = new CartridgeLibrary(new DevFolderReader(Path.of("sd-dev")),
                new CartridgeLoader(new ClasspathModuleFactory()));
        for (String pn : new String[]{"PLT-A320-NEO", "PRP-A320-NEO", "CKP-A320-NEO"}) {
            host.rack().insert(library.load(library.find(pn).orElseThrow()));
        }
        host.powerOn();
        host.execute(new FinalApproachCommand());
        bus.publish(Labels.SIM_THRUST_LEVER, 0.6);
        host.advance(1);
    }

    @Test
    void engineFailureCutsN1AndRaisesWarning() {
        host.execute(new ToggleFailureCommand(new EngineFailure(1)));
        host.advance(1);
        assertEquals(0, bus.getDouble(Labels.ENG1_N1, -1));
        assertTrue(bus.getDouble(Labels.ENG2_N1, 0) > 30);
        assertTrue(bus.getList(Labels.FWC_WARNINGS).contains("ENG 1 FAIL"));

        host.execute(new ToggleFailureCommand(new EngineFailure(1)));
        host.advance(1);
        assertFalse(bus.getList(Labels.FWC_WARNINGS).contains("ENG 1 FAIL"));
    }

    @Test
    void sensorFailureDropsToDirectLawAndRecovers() {
        assertEquals("NORMAL", bus.getString(Labels.FCTL_LAW, ""));
        host.execute(new ToggleFailureCommand(new SensorFailure()));
        host.advance(1);
        assertFalse(bus.has(Labels.IR_PITCH));
        assertEquals("DIRECT", bus.getString(Labels.FCTL_LAW, ""));
        assertTrue(bus.getList(Labels.FWC_CAUTIONS).contains("NAV ADIRU FAULT"));

        host.execute(new ToggleFailureCommand(new SensorFailure()));
        host.advance(1);
        assertEquals("NORMAL", bus.getString(Labels.FCTL_LAW, ""));
    }

    @Test
    void autopilotFailureDisengagesAndBlocksEngagement() {
        bus.publish(Labels.PANEL_AP_PUSH, 1.0);
        host.advance(0.5);
        assertTrue(bus.getBoolean(Labels.AP_ENGAGED));

        host.execute(new ToggleFailureCommand(new AutopilotFailure()));
        host.advance(0.5);
        assertFalse(bus.getBoolean(Labels.AP_ENGAGED));
        assertTrue(bus.getList(Labels.FWC_WARNINGS).contains("AP OFF"));
        assertEquals(2, host.log().size());
    }
}
