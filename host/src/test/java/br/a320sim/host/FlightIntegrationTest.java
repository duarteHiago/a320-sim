package br.a320sim.host;

import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import br.a320sim.host.cartridge.CartridgeLibrary;
import br.a320sim.host.cartridge.CartridgeLoader;
import br.a320sim.host.cartridge.ClasspathModuleFactory;
import br.a320sim.host.cartridge.DevFolderReader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Voo completo com os cartuchos de verdade: decolagem manual, depois piloto automático. */
class FlightIntegrationTest {

    private final Host host = new Host();
    private final DataBus bus = host.bus();
    private final CartridgeLibrary library = new CartridgeLibrary(new DevFolderReader(Path.of("sd-dev")),
            new CartridgeLoader(new ClasspathModuleFactory()));

    private void insert(String... partNumbers) {
        for (String pn : partNumbers) {
            host.rack().insert(library.load(library.find(pn).orElseThrow()));
        }
    }

    private void fly(double seconds, Runnable eachTick) {
        for (int i = 0; i < seconds / Host.TICK_SECONDS; i++) {
            eachTick.run();
            host.advance(Host.TICK_SECONDS);
        }
    }

    @Test
    void incompatibleRackDoesNotPowerOn() {
        insert("PLT-A320-NEO", "PRP-A320-NEO", "CKP-A320-CEO");
        assertFalse(host.powerOn().report().ok());
        assertFalse(host.isPowered());
    }

    @Test
    void takeoffThenAutopilotCapturesAltitudeAndHeading() {
        insert("PLT-A320-NEO", "PRP-A320-NEO", "CKP-A320-NEO");
        assertTrue(host.powerOn().report().ok());
        bus.publish(Labels.PANEL_ALT, 6000.0);
        bus.publish(Labels.PANEL_HDG, 200.0);
        bus.publish(Labels.PANEL_SPD, 250.0);
        bus.publish(Labels.CTL_FLAPS, 1.0);
        bus.publish(Labels.CTL_GEAR_DOWN, true);

        fly(25, () -> {
        });
        assertEquals("NORMAL", bus.getString(Labels.FCTL_LAW, ""));

        bus.publish(Labels.SIM_THRUST_LEVER, 1.0);
        fly(60, () -> {
            boolean rotate = bus.getDouble(Labels.ADR_IAS, 0) > 145 && bus.getDouble(Labels.IR_PITCH, 0) < 12
                    && bus.getDouble(Labels.SIM_RADIO_ALT, 0) < 50;
            bus.publish(Labels.CTL_PITCH, rotate ? 0.6 : 0.0);
        });
        assertFalse(bus.getBoolean(Labels.SIM_ON_GROUND), "deveria ter decolado");

        bus.publish(Labels.CTL_FLAPS, 0.0);
        bus.publish(Labels.CTL_GEAR_DOWN, false);
        bus.publish(Labels.SIM_THRUST_LEVER, 0.85);
        bus.publish(Labels.PANEL_AP_PUSH, 1.0);
        bus.publish(Labels.PANEL_ATHR_PUSH, 1.0);
        fly(240, () -> {
        });

        assertTrue(bus.getBoolean(Labels.AP_ENGAGED), "AP deveria estar engajado");
        assertFalse(bus.getBoolean(Labels.SIM_CRASHED));
        assertEquals(6000, bus.getDouble(Labels.ADR_ALT, 0), 150, "altitude");
        assertEquals(200, bus.getDouble(Labels.IR_HDG, 0), 5, "proa");
        assertEquals(250, bus.getDouble(Labels.ADR_IAS, 0), 15, "velocidade");
    }
}
