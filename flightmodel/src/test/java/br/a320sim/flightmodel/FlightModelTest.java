package br.a320sim.flightmodel;

import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightModelTest {

    private static final double DT = 1.0 / 60;

    private final DataBus bus = new DataBus();
    private final FlightModel model = new FlightModel(bus);

    private void run(double seconds, Runnable eachTick) {
        for (int i = 0; i < seconds / DT; i++) {
            eachTick.run();
            model.step(DT);
        }
    }

    @Test
    void parkedWithIdleThrustAndBrakesStaysStill() {
        bus.publish(Labels.ENG1_N1, 20.0);
        bus.publish(Labels.ENG2_N1, 20.0);
        bus.publish(Labels.CTL_BRAKES, 1.0);

        run(30, () -> {
        });

        assertEquals(0, bus.getDouble(Labels.TRUE_GS, -1), 0.1);
        assertTrue(model.isOnGround());
    }

    @Test
    void takesOffAndClimbsWithTogaAndFlaps() {
        bus.publish(Labels.ENG1_N1, 100.0);
        bus.publish(Labels.ENG2_N1, 100.0);
        bus.publish(Labels.CTL_FLAPS, 1);

        run(90, () -> {
            boolean rotate = bus.getDouble(Labels.TRUE_IAS, 0) > 140 && bus.getDouble(Labels.TRUE_PITCH, 0) < 12;
            bus.publish(Labels.FCTL_PITCH_RATE, rotate ? 3.0 : 0.0);
        });

        assertFalse(model.isOnGround());
        assertFalse(model.isCrashed());
        assertTrue(bus.getDouble(Labels.TRUE_ALT, 0) > FlightModel.FIELD_ELEVATION_FT + 1000);
    }

    @Test
    void repositionedAirborneHoldsRoughlyLevel() {
        model.resetAirborne(10_000, 250, 90);
        bus.publish(Labels.ENG1_N1, 62.0);
        bus.publish(Labels.ENG2_N1, 62.0);

        run(5, () -> {
        });

        assertEquals(0, bus.getDouble(Labels.TRUE_VS, 999), 1500);
        assertEquals(10_000, bus.getDouble(Labels.TRUE_ALT, 0), 300);
    }

    /** Distância (m) de corrida até sair do chão com TOGA, girando a 140 kt. */
    private static double takeoffRun(Double grossWeight) {
        DataBus bus = new DataBus();
        FlightModel model = new FlightModel(bus);
        if (grossWeight != null) {
            bus.publish(Labels.SIM_GROSS_WEIGHT, grossWeight);
        }
        bus.publish(Labels.ENG1_N1, 100.0);
        bus.publish(Labels.ENG2_N1, 100.0);
        bus.publish(Labels.CTL_FLAPS, 1);
        for (int i = 0; i < 120 / DT && model.isOnGround(); i++) {
            boolean rotate = bus.getDouble(Labels.TRUE_IAS, 0) > 140 && bus.getDouble(Labels.TRUE_PITCH, 0) < 12;
            bus.publish(Labels.FCTL_PITCH_RATE, rotate ? 3.0 : 0.0);
            model.step(DT);
        }
        assertFalse(model.isOnGround());
        return Math.hypot(bus.getDouble(Labels.TRUE_POS_X, 0), bus.getDouble(Labels.TRUE_POS_Y, 0)) * 1852;
    }

    @Test
    void aviaoMaisPesadoPrecisaDeMaisPista() {
        double light = takeoffRun(55_000.0);
        double standard = takeoffRun(null);
        double heavy = takeoffRun(77_000.0);
        assertTrue(light < standard && standard < heavy, light + " < " + standard + " < " + heavy);
    }
}
