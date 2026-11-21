package br.a320sim.host.session;

import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;

import java.util.ArrayList;
import java.util.List;

/**
 * Percebe pelo barramento os momentos que interessam ao debriefing: DECOLAGEM, POUSO (com a razão de
 * descida no toque), CRASH e COLISAO COM PREDIO / ARVORE.
 * O host chama {@link #update} a cada passo e escreve o que vier no log.
 * <p>
 * Depois de um reposicionamento (comando do instrutor) chame {@link #resync}: o avião "pular" do chão
 * para 8000 ft não é uma decolagem.
 */
public class FlightEvents {

    public static final String TAKEOFF = "DECOLAGEM";
    public static final String CRASH = "CRASH";
    public static final String LANDING = "POUSO";
    /** Seguido do que foi atingido: "COLISAO COM PREDIO", "COLISAO COM ARVORE". */
    public static final String COLLISION = "COLISAO COM ";

    private boolean onGround = true;
    private boolean crashed;
    /** V/S do último passo ainda no ar: no passo do toque o modelo já zerou a trajetória. */
    private double lastAirborneVs;

    /** Aceita o estado atual sem gerar eventos. */
    public void resync(DataBus bus) {
        onGround = !bus.has(Labels.SIM_ON_GROUND) || bus.getBoolean(Labels.SIM_ON_GROUND);
        crashed = bus.getBoolean(Labels.SIM_CRASHED);
        lastAirborneVs = bus.getDouble(Labels.TRUE_VS, 0);
    }

    /** @return os eventos deste passo (quase sempre nenhum) */
    public List<String> update(DataBus bus) {
        boolean ground = !bus.has(Labels.SIM_ON_GROUND) || bus.getBoolean(Labels.SIM_ON_GROUND);
        boolean crash = bus.getBoolean(Labels.SIM_CRASHED);
        List<String> events = new ArrayList<>();
        if (crash && !crashed) {
            String hit = bus.getString(Labels.SIM_COLLISION, "");
            events.add(hit.isEmpty() ? CRASH : COLLISION + hit);
        } else if (ground && !onGround) {
            events.add(landing(lastAirborneVs));
        } else if (!ground && onGround) {
            events.add(TAKEOFF);
        }
        if (!ground) {
            lastAirborneVs = bus.getDouble(Labels.TRUE_VS, 0);
        }
        onGround = ground;
        crashed = crash;
        return events;
    }

    static String landing(double vsFpm) {
        return LANDING + " V/S " + Math.round(vsFpm) + " FT/MIN";
    }
}
