package br.a320sim.cartridges.propulsao;

import br.a320sim.core.bus.Labels;
import br.a320sim.core.module.BaseModule;
import br.a320sim.core.module.ModuleInfo;

import java.util.List;

/**
 * Piloto automático (FCU/FMGC simplificado). Lê o painel FCU e gera comandos
 * "de manche" que o fly-by-wire executa no lugar do sidestick.
 * Todas as versões seguram proa (HDG) e altitude (ALT); versões completas
 * acrescentam razão de subida escolhida (V/S) e autothrust (SPD).
 */
public abstract class Autopilot extends BaseModule {

    private static final double KT = 0.514444;
    private static final double FT = 0.3048;
    private static final double DEFAULT_VS = 1500;

    private final List<String> modes;
    private boolean engaged;
    private boolean athr;
    private double apPushes;
    private double athrPushes;
    private double discPushes;
    private double athrIntegral;

    protected Autopilot(String variant, List<String> modes) {
        super(new ModuleInfo("AP", "Piloto automático", variant));
        this.modes = List.copyOf(modes);
    }

    public boolean supports(String mode) {
        return modes.contains(mode);
    }

    @Override
    protected void onPowerUp() {
        engaged = false;
        athr = false;
        apPushes = bus.getDouble(Labels.PANEL_AP_PUSH, 0);
        athrPushes = bus.getDouble(Labels.PANEL_ATHR_PUSH, 0);
        discPushes = bus.getDouble(Labels.PANEL_AP_DISC, 0);
        bus.publish(Labels.AP_MODES, modes);
    }

    @Override
    protected void onTick(double dt) {
        boolean irValid = "NAV".equals(bus.getString(Labels.IR_ALIGN_STATUS, ""));
        boolean airborne = !bus.getBoolean(Labels.SIM_ON_GROUND);

        if (pushed(Labels.PANEL_AP_PUSH, apPushes)) {
            apPushes = bus.getDouble(Labels.PANEL_AP_PUSH, 0);
            engaged = !engaged && irValid && airborne;
        }
        if (pushed(Labels.PANEL_AP_DISC, discPushes)) {
            discPushes = bus.getDouble(Labels.PANEL_AP_DISC, 0);
            engaged = false;
        }
        if (pushed(Labels.PANEL_ATHR_PUSH, athrPushes)) {
            athrPushes = bus.getDouble(Labels.PANEL_ATHR_PUSH, 0);
            athr = !athr && supports("SPD");
            athrIntegral = bus.getDouble(Labels.SIM_THRUST_LEVER, 0.5);
        }
        boolean pilotTakeover = Math.abs(bus.getDouble(Labels.CTL_PITCH, 0)) > 0.5
                || Math.abs(bus.getDouble(Labels.CTL_ROLL, 0)) > 0.5;
        if (pilotTakeover || !irValid) {
            engaged = false;
        }

        if (engaged) {
            lateral();
            vertical();
        } else {
            bus.publish(Labels.AP_LAT_MODE, "");
            bus.publish(Labels.AP_VERT_MODE, "");
            bus.publish(Labels.AP_PITCH_CMD, 0.0);
            bus.publish(Labels.AP_ROLL_CMD, 0.0);
        }
        if (athr) {
            autothrust(dt);
        }
        bus.publish(Labels.AP_ENGAGED, engaged);
        bus.publish(Labels.AP_ATHR_ACTIVE, athr);
    }

    private boolean pushed(String label, double lastSeen) {
        return bus.getDouble(label, 0) != lastSeen;
    }

    private void lateral() {
        double error = wrap180(bus.getDouble(Labels.PANEL_HDG, 0) - bus.getDouble(Labels.IR_HDG, 0));
        double targetBank = clamp(error * 1.5, -25, 25);
        double bank = bus.getDouble(Labels.IR_ROLL, 0);
        bus.publish(Labels.AP_ROLL_CMD, clamp((targetBank - bank) * 0.08, -1, 1));
        bus.publish(Labels.AP_LAT_MODE, "HDG");
    }

    private void vertical() {
        double diff = bus.getDouble(Labels.PANEL_ALT, 0) - bus.getDouble(Labels.ADR_ALT, 0);
        double selectedVs = supports("V/S") ? bus.getDouble(Labels.PANEL_VS, 0) : 0;
        double targetVs;
        String mode;
        if (Math.abs(diff) <= 300) {
            targetVs = clamp(diff * 5, -DEFAULT_VS, DEFAULT_VS);
            mode = Math.abs(diff) < 30 ? "ALT" : "ALT*";
        } else if (selectedVs != 0 && Math.signum(selectedVs) == Math.signum(diff)) {
            targetVs = selectedVs;
            mode = "V/S";
        } else {
            targetVs = Math.signum(diff) * DEFAULT_VS;
            mode = diff > 0 ? "CLB" : "DES";
        }
        double speed = Math.max(60, bus.getDouble(Labels.ADR_IAS, 0)) * KT;
        double targetFpa = Math.toDegrees(Math.asin(clamp(targetVs * FT / 60 / speed, -0.3, 0.3)));
        double fpa = bus.getDouble(Labels.IR_FPA, 0);
        bus.publish(Labels.AP_PITCH_CMD, clamp((targetFpa - fpa) * 0.17, -1, 1));
        bus.publish(Labels.AP_VERT_MODE, mode);
    }

    private void autothrust(double dt) {
        double error = bus.getDouble(Labels.PANEL_SPD, 250) - bus.getDouble(Labels.ADR_IAS, 0);
        athrIntegral = clamp(athrIntegral + error * 0.004 * dt, 0, 1);
        bus.publish(Labels.AP_ATHR_CMD, clamp(athrIntegral + error * 0.03, 0, 1));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double wrap180(double deg) {
        double d = (deg + 180) % 360;
        return (d < 0 ? d + 360 : d) - 180;
    }
}
