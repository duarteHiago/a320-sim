package br.a320sim.cockpit.input;

import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;

/**
 * O "copiloto" do modo simplificado: cuida do trem e dos flaps para o piloto só acelerar e dar direção.
 * <ul>
 *   <li>Trem: sobe depois da decolagem (subindo, acima de 150 ft); desce na descida abaixo de 1500 ft
 *       ou quando o avião fica lento perto do chão.</li>
 *   <li>Flaps: 1 para decolar; recolhem conforme a velocidade aumenta na subida e baixam conforme ela cai
 *       na aproximação. Nunca baixam subindo, nem recolhem descendo para pousar.</li>
 * </ul>
 * Usa a verdade física (TRUE.*): a ajuda continua funcionando mesmo com pane nos sensores.
 */
public final class FlightAssist {

    private static final double GEAR_UP_FT = 150;
    private static final double GEAR_DOWN_FT = 1500;
    private static final double APPROACH_FT = 3000;

    private boolean gearDown = true;
    private int flaps = 1;

    /** Começa do estado atual do avião (controle plugado no meio do voo). */
    public void sync(ControlInputs current) {
        gearDown = current.gearDown();
        flaps = current.flaps();
    }

    public ControlInputs apply(ControlInputs in, DataBus bus) {
        boolean onGround = bus.getBoolean(Labels.SIM_ON_GROUND);
        double ias = bus.getDouble(Labels.TRUE_IAS, 0);
        double vs = bus.getDouble(Labels.TRUE_VS, 0);
        double height = bus.getDouble(Labels.SIM_RADIO_ALT, 0);

        if (onGround) {
            gearDown = true;
            if (ias < 40) {
                flaps = 1; // parado ou taxiando: pronto para decolar de novo
            }
        } else {
            if (gearDown && vs > 300 && height > GEAR_UP_FT) {
                gearDown = false;
            } else if (!gearDown && height < GEAR_DOWN_FT && (vs < -300 || ias < 170)) {
                gearDown = true;
            }
            int target = scheduledFlaps(ias);
            boolean climbing = vs > 200;
            boolean approaching = vs < -100 && height < APPROACH_FT;
            if (climbing) {
                flaps = Math.min(flaps, target);
            } else if (approaching) {
                flaps = Math.max(flaps, target);
            }
        }
        return new ControlInputs(in.pitch(), in.roll(), in.yaw(), in.thrustLever(), flaps, gearDown, in.brakes(),
                in.apDisconnect());
    }

    /** Flaps que cabem nesta velocidade (kt). */
    private static int scheduledFlaps(double ias) {
        if (ias > 205) {
            return 0;
        }
        if (ias > 185) {
            return 1;
        }
        if (ias > 170) {
            return 2;
        }
        return ias > 155 ? 3 : 4;
    }
}
