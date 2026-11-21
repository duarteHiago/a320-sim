package br.a320sim.flightmodel;

import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindTest {

    private static final double DT = 1.0 / 60;

    private static void wind(DataBus bus, double fromDeg, double kt, int turbulence) {
        bus.publish(Labels.SIM_WIND_DIR, fromDeg);
        bus.publish(Labels.SIM_WIND_SPD, kt);
        bus.publish(Labels.SIM_TURBULENCE, (double) turbulence);
    }

    /** Voo nivelado a 8000 ft, 250 kt, proa norte, com motores firmes, por alguns segundos. */
    private static FlightModel cruise(DataBus bus, double seconds) {
        FlightModel model = new FlightModel(bus);
        model.resetAirborne(8000, 250, 0, 0, 0);
        bus.publish(Labels.ENG1_N1, 62.0);
        bus.publish(Labels.ENG2_N1, 62.0);
        for (int i = 0; i < seconds / DT; i++) {
            model.step(DT);
        }
        return model;
    }

    @Test
    void ventoCalmoNaoMudaNada() {
        DataBus without = new DataBus();
        cruise(without, 30);
        DataBus calm = new DataBus();
        wind(calm, 270, 0, 0);
        cruise(calm, 30);
        for (String label : new String[]{Labels.TRUE_POS_X, Labels.TRUE_POS_Y, Labels.TRUE_ALT, Labels.TRUE_IAS,
                Labels.TRUE_ROLL}) {
            assertEquals(without.getDouble(label, 0), calm.getDouble(label, 1), 0, label);
        }
    }

    @Test
    void ventoDeTravesFazOAviaoDerivar() {
        DataBus bus = new DataBus();
        wind(bus, 270, 30, 0); // de oeste, empurra para leste
        cruise(bus, 60);
        // 30 kt por 1 minuto = 0,5 NM para leste, com a proa ainda no norte
        assertEquals(0.5, bus.getDouble(Labels.TRUE_POS_X, 0), 0.01);
        assertEquals(0, bus.getDouble(Labels.TRUE_HDG, 0), 0.5);
        double ias = bus.getDouble(Labels.TRUE_IAS, 0);
        assertEquals(250, ias, 15, "o velocimetro mede o ar, nao o chao");
    }

    @Test
    void ventoDeCaudaAumentaAVelocidadeSobreOChao() {
        DataBus calm = new DataBus();
        cruise(calm, 10);
        DataBus tail = new DataBus();
        wind(tail, 180, 30, 0); // de sul, empurrando para o norte
        cruise(tail, 10);
        assertEquals(calm.getDouble(Labels.TRUE_GS, 0) + 30, tail.getDouble(Labels.TRUE_GS, 0), 1);
        assertEquals(calm.getDouble(Labels.TRUE_IAS, 0), tail.getDouble(Labels.TRUE_IAS, 0), 1);
    }

    @Test
    void paradoNaPistaComVentoDeProaOVelocimetroMarcaOVento() {
        DataBus bus = new DataBus();
        FlightModel model = new FlightModel(bus);
        wind(bus, FlightModel.RUNWAY_HEADING, 20, 0);
        bus.publish(Labels.CTL_BRAKES, 1.0);
        for (int i = 0; i < 60; i++) {
            model.step(DT);
        }
        assertEquals(0, bus.getDouble(Labels.TRUE_GS, -1), 1e-9, "o aviao nao anda");
        // O vento é velocidade verdadeira; a 2170 ft o ar é menos denso e o velocímetro marca ~4% a menos
        assertEquals(20 * Math.sqrt(Math.exp(-2170 * 0.3048 / 8500)), bus.getDouble(Labels.TRUE_IAS, 0), 0.05,
                "mas o ar passa pelo aviao");
    }

    @Test
    void turbulenciaFazOAviaoBalancarDentroDeLimites() {
        DataBus bus = new DataBus();
        wind(bus, 0, 0, 3);
        FlightModel model = new FlightModel(bus);
        model.resetAirborne(8000, 250, 0, 0, 0);
        bus.publish(Labels.ENG1_N1, 62.0);
        bus.publish(Labels.ENG2_N1, 62.0);
        double minVs = 0;
        double maxVs = 0;
        for (int i = 0; i < 30 / DT; i++) {
            model.step(DT);
            double vs = bus.getDouble(Labels.TRUE_VS, 0);
            minVs = Math.min(minVs, vs);
            maxVs = Math.max(maxVs, vs);
        }
        assertTrue(maxVs - minVs > 300, "turbulencia forte sacode o variometro: " + minVs + " a " + maxVs);
        // A rajada vertical tem desvio de 3,5 m/s (~690 ft/min); mais de 5 desvios seria um bug
        assertTrue(maxVs < 4000 && minVs > -4000, "rajadas sem exagero: " + minVs + " a " + maxVs);
        assertNotEquals(0, bus.getDouble(Labels.TRUE_ROLL, 0), "rola o aviao");
    }

    @Test
    void turbulenciaEARepetivelComAMesmaSemente() {
        DataBus a = new DataBus();
        DataBus b = new DataBus();
        wind(a, 200, 15, 2);
        wind(b, 200, 15, 2);
        cruise(a, 20);
        cruise(b, 20);
        assertEquals(a.getDouble(Labels.TRUE_ALT, 0), b.getDouble(Labels.TRUE_ALT, 1), 0);
        assertEquals(a.getDouble(Labels.TRUE_ROLL, 0), b.getDouble(Labels.TRUE_ROLL, 1), 0);
    }

    @Test
    void noChaoNaoHaRajadas() {
        DataBus bus = new DataBus();
        FlightModel model = new FlightModel(bus);
        wind(bus, FlightModel.RUNWAY_HEADING, 0, 3);
        for (int i = 0; i < 120; i++) {
            model.step(DT);
        }
        assertEquals(0, bus.getDouble(Labels.TRUE_VS, 1), 1e-9);
        assertEquals(0, bus.getDouble(Labels.TRUE_ROLL, 1), 1e-9);
    }

    @Test
    void turbulenciaForteBalancaMasNaoDeitaOAviao() {
        DataBus bus = new DataBus();
        wind(bus, 0, 0, 3);
        FlightModel model = new FlightModel(bus);
        model.resetAirborne(8000, 250, 0, 0, 0);
        double maxBank = 0;
        for (int i = 0; i < 60 / DT; i++) {
            model.step(DT);
            maxBank = Math.max(maxBank, Math.abs(bus.getDouble(Labels.TRUE_ROLL, 0)));
        }
        assertTrue(maxBank > 3, "balanca: " + maxBank);
        assertTrue(maxBank < 30, "sem manche, o fly-by-wire segura a atitude: " + maxBank);
    }
}
