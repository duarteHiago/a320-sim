package br.a320sim.flightmodel;

import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;

/**
 * Física "arcade-realista" do A320: sustentação, arrasto, empuxo e peso.
 * A atitude (pitch/bank) segue diretamente as taxas comandadas pelo fly-by-wire;
 * a trajetória e a velocidade saem do balanço de forças.
 * Lê do barramento: comandos do ELAC, N1 dos motores e alavancas. Publica TRUE.* e SIM.*.
 */
public class FlightModel {

    /** Viracopos (SBKP), pista 15: base da Azul. */
    public static final double RUNWAY_HEADING = 148;
    public static final double FIELD_ELEVATION_FT = 2170;

    private static final double G = 9.81;
    private static final double KT = 0.514444;
    private static final double FT = 0.3048;
    private static final double NM = 1852;

    private static final double MASS = 66_000;
    private static final double WING_AREA = 122.6;
    private static final double MAX_THRUST_PER_ENGINE = 115_000;

    private static final double CL0 = 0.2;
    private static final double CL_PER_DEG = 0.09;
    private static final double STALL_AOA = 15;
    private static final double CD0 = 0.02;
    private static final double INDUCED_K = 0.045;
    private static final double GEAR_CD = 0.015;
    private static final double[] FLAP_CL = {0, 0.35, 0.5, 0.65, 0.85};
    private static final double[] FLAP_CD = {0, 0.008, 0.015, 0.03, 0.055};

    private static final double ROLLING_FRICTION = 0.02;
    private static final double BRAKE_FRICTION = 0.35;
    private static final double MIN_ROTATION_KT = 100;
    private static final double MAX_GROUND_PITCH = 12;
    private static final double STEERING_RATE = 8;

    private final DataBus bus;

    private double x;
    private double y;
    private double alt;
    private double v;
    private double gamma;
    private double pitch;
    private double bank;
    private double heading;
    private double aoa;
    private boolean onGround;
    private boolean crashed;

    public FlightModel(DataBus bus) {
        this.bus = bus;
        resetOnRunway();
    }

    public void resetOnRunway() {
        x = 0;
        y = 0;
        alt = 0;
        v = 0;
        gamma = 0;
        pitch = 0;
        bank = 0;
        heading = RUNWAY_HEADING;
        onGround = true;
        crashed = false;
        publish();
    }

    /** Reposiciona o avião em voo nivelado (atalho do instrutor). */
    public void resetAirborne(double altFt, double iasKt, double headingDeg) {
        resetOnRunway();
        alt = (altFt - FIELD_ELEVATION_FT) * FT;
        v = iasKt * KT / Math.sqrt(density() / 1.225);
        heading = headingDeg;
        onGround = false;
        pitch = trimAoa();
        publish();
    }

    public boolean isOnGround() {
        return onGround;
    }

    public boolean isCrashed() {
        return crashed;
    }

    public void step(double dt) {
        if (crashed || bus.getBoolean(Labels.SIM_FREEZE)) {
            publish();
            return;
        }
        int flaps = (int) clamp(bus.getDouble(Labels.CTL_FLAPS, 0), 0, FLAP_CL.length - 1);
        boolean gearDown = !bus.has(Labels.CTL_GEAR_DOWN) || bus.getBoolean(Labels.CTL_GEAR_DOWN);
        double pitchRate = bus.getDouble(Labels.FCTL_PITCH_RATE, 0);
        double rollRate = bus.getDouble(Labels.FCTL_ROLL_RATE, 0);

        double rho = density();
        double n1 = (bus.getDouble(Labels.ENG1_N1, 0) + bus.getDouble(Labels.ENG2_N1, 0)) / 2;
        double thrust = 2 * MAX_THRUST_PER_ENGINE * thrustFraction(n1) * Math.pow(rho / 1.225, 0.75);
        double q = 0.5 * rho * v * v;
        aoa = onGround ? pitch : pitch - Math.toDegrees(gamma);
        double cl = liftCoefficient(aoa, flaps);
        double cd = CD0 + FLAP_CD[flaps] + (gearDown ? GEAR_CD : 0) + INDUCED_K * cl * cl;
        double lift = q * WING_AREA * cl;
        double drag = q * WING_AREA * cd;
        double weight = MASS * G;

        if (onGround) {
            stepGround(dt, pitchRate, thrust, drag, lift, weight);
        } else {
            stepAir(dt, pitchRate, rollRate, thrust, drag, lift, weight, gearDown);
        }

        double hdg = Math.toRadians(heading);
        x += v * Math.cos(gamma) * Math.sin(hdg) * dt;
        y += v * Math.cos(gamma) * Math.cos(hdg) * dt;
        publish();
    }

    private void stepGround(double dt, double pitchRate, double thrust, double drag, double lift, double weight) {
        bank = 0;
        gamma = 0;
        double ias = v / KT;
        pitch = clamp(pitch + (pitchRate - 1.5) * dt, 0, MAX_GROUND_PITCH);
        if (ias < MIN_ROTATION_KT) {
            pitch = Math.max(0, pitch - 4 * dt);
        }

        double brakes = bus.getDouble(Labels.CTL_BRAKES, 0);
        double friction = Math.max(0, weight - lift) * (ROLLING_FRICTION + BRAKE_FRICTION * brakes);
        v = Math.max(0, v + (thrust - drag - friction) / MASS * dt);
        if (v > 0.5) {
            heading = wrap360(heading + bus.getDouble(Labels.CTL_YAW, 0) * STEERING_RATE * dt);
        }
        if (lift > weight) {
            onGround = false;
        }
    }

    private void stepAir(double dt, double pitchRate, double rollRate, double thrust, double drag,
                         double lift, double weight, boolean gearDown) {
        pitch = clamp(pitch + pitchRate * dt, -90, 90);
        bank = wrap180(bank + rollRate * dt);

        v = Math.max(15, v + ((thrust - drag) / MASS - G * Math.sin(gamma)) * dt);
        double gammaRate = (lift * Math.cos(Math.toRadians(bank)) - weight * Math.cos(gamma)) / (MASS * v);
        gamma = clamp(gamma + gammaRate * dt, -1.4, 1.4);
        double turnBank = Math.toRadians(clamp(bank, -80, 80));
        heading = wrap360(heading + Math.toDegrees(G * Math.tan(turnBank) / v) * dt);
        alt += v * Math.sin(gamma) * dt;

        if (alt <= 0) {
            touchdown(gearDown);
        }
    }

    private void touchdown(boolean gearDown) {
        double vsFpm = v * Math.sin(gamma) / FT * 60;
        crashed = !gearDown || vsFpm < -1500 || Math.abs(bank) > 15 || pitch < -5;
        alt = 0;
        gamma = 0;
        bank = 0;
        pitch = Math.max(0, pitch);
        onGround = true;
        if (crashed) {
            v = 0;
        }
    }

    private double density() {
        return 1.225 * Math.exp(-(alt + FIELD_ELEVATION_FT * FT) / 8500);
    }

    /** Ângulo de ataque que sustenta o peso na velocidade atual (para reposicionar em voo). */
    private double trimAoa() {
        double q = 0.5 * density() * v * v;
        return (MASS * G / (q * WING_AREA) - CL0) / CL_PER_DEG;
    }

    private static double thrustFraction(double n1) {
        if (n1 <= 0) {
            return 0;
        }
        double f = clamp((n1 - 20) / 80, 0, 1);
        return 0.04 + 0.96 * f * f;
    }

    private static double liftCoefficient(double aoa, int flaps) {
        double base = CL0 + FLAP_CL[flaps];
        if (aoa <= STALL_AOA) {
            return base + CL_PER_DEG * aoa;
        }
        double peak = base + CL_PER_DEG * STALL_AOA;
        return Math.max(0.2, peak - 0.06 * (aoa - STALL_AOA));
    }

    private void publish() {
        double ias = v / KT * Math.sqrt(density() / 1.225);
        bus.publish(Labels.TRUE_IAS, ias);
        bus.publish(Labels.TRUE_ALT, alt / FT + FIELD_ELEVATION_FT);
        bus.publish(Labels.TRUE_VS, v * Math.sin(gamma) / FT * 60);
        bus.publish(Labels.TRUE_PITCH, pitch);
        bus.publish(Labels.TRUE_ROLL, bank);
        bus.publish(Labels.TRUE_HDG, heading);
        bus.publish(Labels.TRUE_FPA, Math.toDegrees(gamma));
        bus.publish(Labels.TRUE_AOA, aoa);
        bus.publish(Labels.TRUE_GS, v * Math.cos(gamma) / KT);
        bus.publish(Labels.TRUE_POS_X, x / NM);
        bus.publish(Labels.TRUE_POS_Y, y / NM);
        bus.publish(Labels.SIM_ON_GROUND, onGround);
        bus.publish(Labels.SIM_RADIO_ALT, alt / FT);
        bus.publish(Labels.SIM_CRASHED, crashed);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double wrap180(double deg) {
        return wrap360(deg + 180) - 180;
    }

    private static double wrap360(double deg) {
        double d = deg % 360;
        return d < 0 ? d + 360 : d;
    }
}
