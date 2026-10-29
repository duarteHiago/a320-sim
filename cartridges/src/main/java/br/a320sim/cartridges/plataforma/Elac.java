package br.a320sim.cartridges.plataforma;

import br.a320sim.core.bus.Labels;
import br.a320sim.core.module.BaseModule;
import br.a320sim.core.module.ModuleInfo;

/**
 * Fly-by-wire (ELAC). Traduz o sidestick (ou o piloto automático) em taxas de pitch e de rolagem.
 * Em Lei Normal (sensores alinhados, em voo) aplica as proteções do A320:
 * manche solto mantém a trajetória, inclinação máx. 67°, pitch entre -15° e +30°,
 * proteção de ângulo de ataque (anti-estol) e de sobrevelocidade.
 * Sem sensores alinhados cai para Lei Direta: o manche move as superfícies e nada protege.
 */
public class Elac extends BaseModule {

    private static final double MAX_PITCH_RATE = 6;
    private static final double MAX_ROLL_RATE = 15;
    private static final double NEUTRAL = 0.05;

    private Double heldFpa;

    public Elac() {
        super(new ModuleInfo("FCTL", "Fly-by-wire", "padrão"));
    }

    @Override
    protected void onTick(double dt) {
        boolean normalLaw = "NAV".equals(bus.getString(Labels.IR_ALIGN_STATUS, ""));
        boolean autopilot = bus.getBoolean(Labels.AP_ENGAGED);
        double pitchIn = bus.getDouble(autopilot ? Labels.AP_PITCH_CMD : Labels.CTL_PITCH, 0);
        double rollIn = bus.getDouble(autopilot ? Labels.AP_ROLL_CMD : Labels.CTL_ROLL, 0);

        double pitchRate = pitchIn * MAX_PITCH_RATE;
        double rollRate = rollIn * MAX_ROLL_RATE;
        String protection = "";

        if (normalLaw && !bus.getBoolean(Labels.SIM_ON_GROUND)) {
            double pitch = bus.getDouble(Labels.IR_PITCH, 0);
            double bank = bus.getDouble(Labels.IR_ROLL, 0);
            double fpa = bus.getDouble(Labels.IR_FPA, 0);

            if (!autopilot && Math.abs(pitchIn) < NEUTRAL) {
                if (heldFpa == null) {
                    heldFpa = fpa;
                }
                pitchRate = clamp(heldFpa - fpa, -3, 3);
            } else {
                heldFpa = null;
            }
            if (Math.abs(rollIn) < NEUTRAL && Math.abs(bank) > 33) {
                rollRate = -Math.signum(bank) * 5;
            }
            if (Math.abs(bank) >= 67 && Math.signum(rollRate) == Math.signum(bank)) {
                rollRate = 0;
                protection = "BANK";
            }
            if ((pitch >= 30 && pitchRate > 0) || (pitch <= -15 && pitchRate < 0)) {
                pitchRate = 0;
                protection = "PITCH";
            }
            double aoa = bus.getDouble(Labels.ADR_AOA, 0);
            if (aoa > 12) {
                pitchRate = Math.min(pitchRate, -3 * (aoa - 12));
                heldFpa = null;
                protection = "ALPHA";
            }
            if (bus.getDouble(Labels.ADR_IAS, 0) > 360) {
                pitchRate = Math.max(pitchRate, 2);
                heldFpa = null;
                protection = "SPEED";
            }
        } else {
            heldFpa = null;
        }

        bus.publish(Labels.FCTL_LAW, normalLaw ? "NORMAL" : "DIRECT");
        bus.publish(Labels.FCTL_PITCH_RATE, pitchRate);
        bus.publish(Labels.FCTL_ROLL_RATE, rollRate);
        bus.publish(Labels.FCTL_PROT, protection);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
