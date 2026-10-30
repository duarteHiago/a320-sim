package br.a320sim.cartridges.propulsao;

import br.a320sim.core.bus.Labels;
import br.a320sim.core.module.BaseModule;
import br.a320sim.core.module.ModuleInfo;

/**
 * Motores (FADEC). Lê a posição da manete e leva o N1 até o alvo
 * com um atraso (spool), como turbina de verdade.
 * Com o autothrust ligado, a manete vira o limite máximo e quem decide é o piloto automático.
 */
public abstract class Fadec extends BaseModule {

    private static final double IDLE_N1 = 20.0;
    private static final double MAX_N1 = 100.0;

    private final String primaryParam;
    private final double spoolRate;
    private double n1;

    protected Fadec(String engine, String primaryParam, double spoolRate) {
        super(new ModuleInfo("ENG", "Motores", engine));
        this.primaryParam = primaryParam;
        this.spoolRate = spoolRate;
    }

    @Override
    protected void onPowerUp() {
        n1 = IDLE_N1;
        bus.publish(Labels.ENG_TYPE, info().variant());
        bus.publish(Labels.ENG_PRIMARY_PARAM, primaryParam);
    }

    @Override
    protected void onTick(double dt) {
        double lever = bus.getDouble(Labels.SIM_THRUST_LEVER, 0);
        if (bus.getBoolean(Labels.AP_ATHR_ACTIVE)) {
            lever = Math.min(lever, bus.getDouble(Labels.AP_ATHR_CMD, lever));
        }
        double target = IDLE_N1 + lever * (MAX_N1 - IDLE_N1);
        n1 += (target - n1) * Math.min(1, spoolRate * dt);
        double epr = 1.0 + 0.55 * (n1 - IDLE_N1) / (MAX_N1 - IDLE_N1);
        bus.publish(Labels.ENG1_N1, n1);
        bus.publish(Labels.ENG2_N1, n1);
        bus.publish(Labels.ENG1_EPR, epr);
        bus.publish(Labels.ENG2_EPR, epr);
    }
}
