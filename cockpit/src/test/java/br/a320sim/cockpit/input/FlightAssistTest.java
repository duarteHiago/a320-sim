package br.a320sim.cockpit.input;

import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** O copiloto do modo simplificado: trem e flaps numa decolagem e numa aproximação. */
class FlightAssistTest {

    private final FlightAssist assist = new FlightAssist();
    private final DataBus bus = new DataBus();
    private final ControlInputs stick = new ControlInputs(0, 0, 0, 1, 0, true, 0, false);

    private ControlInputs fly(boolean onGround, double ias, double vs, double height) {
        bus.publish(Labels.SIM_ON_GROUND, onGround);
        bus.publish(Labels.TRUE_IAS, ias);
        bus.publish(Labels.TRUE_VS, vs);
        bus.publish(Labels.SIM_RADIO_ALT, height);
        return assist.apply(stick, bus);
    }

    @Test
    void takeoffRetractsGearThenFlapsAsSpeedBuilds() {
        ControlInputs out = fly(true, 0, 0, 0);
        assertEquals(1, out.flaps());
        assertTrue(out.gearDown());

        out = fly(true, 140, 0, 0);
        assertEquals(1, out.flaps(), "na corrida não mexe nos flaps");

        out = fly(false, 160, 1500, 80);
        assertTrue(out.gearDown(), "ainda muito baixo para recolher o trem");
        assertEquals(1, out.flaps(), "subindo devagar: nunca baixa mais flap");

        out = fly(false, 175, 1800, 400);
        assertFalse(out.gearDown());
        out = fly(false, 215, 1800, 2500);
        assertEquals(0, out.flaps());
    }

    @Test
    void approachLowersFlapsAndGearAsItSlowsDown() {
        assist.sync(new ControlInputs(0, 0, 0, 0.5, 0, false, 0, false));
        ControlInputs out = fly(false, 250, -1000, 6000);
        assertEquals(0, out.flaps(), "longe do aeroporto não mexe em nada");
        assertFalse(out.gearDown());

        out = fly(false, 190, -700, 2800);
        assertEquals(1, out.flaps());
        out = fly(false, 160, -700, 1400);
        assertEquals(3, out.flaps());
        assertTrue(out.gearDown());
        out = fly(false, 140, -700, 500);
        assertEquals(4, out.flaps());

        out = fly(false, 150, -700, 300);
        assertEquals(4, out.flaps(), "descendo para pousar: nunca recolhe");
    }
}
