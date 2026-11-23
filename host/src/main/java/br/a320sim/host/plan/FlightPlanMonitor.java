package br.a320sim.host.plan;

import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Acompanha o plano de voo no barramento (vindo do MCDU do aluno):
 * <ul>
 *   <li>plano novo ou alterado vira uma linha no log (debriefing);</li>
 *   <li>no chão, o peso de decolagem do plano vira o peso do avião ({@code SIM.GROSS_WEIGHT});
 *       em voo o peso não muda (o combustível não é consumido);</li>
 *   <li>a pista de destino do plano vira o destino da sessão (HUD);</li>
 *   <li>decolar sem plano não é bloqueado, mas fica registrado.</li>
 * </ul>
 */
public class FlightPlanMonitor {

    public static final String NO_PLAN = "SEM PLANO DE VOO";

    private FlightPlan current;

    /** Começo de sessão: sem plano. */
    public void reset() {
        current = null;
    }

    public Optional<FlightPlan> plan() {
        return Optional.ofNullable(current);
    }

    /** @return eventos para o log (plano novo/alterado) */
    public List<String> update(DataBus bus) {
        List<String> events = new ArrayList<>();
        Optional<FlightPlan> onBus = FlightPlan.fromBus(bus).filter(p -> p.validate().isEmpty());
        if (onBus.isPresent() && !onBus.get().equals(current)) {
            current = onBus.get();
            events.add(current.description());
        }
        if (current != null) {
            if (bus.getBoolean(Labels.SIM_ON_GROUND)) {
                bus.publish(Labels.SIM_GROSS_WEIGHT, (double) current.towKg());
            }
            bus.publish(Labels.SIM_DEST_RUNWAY, current.destinationRunway());
        }
        return events;
    }

    /** Chamado na decolagem: o evento a registrar se não havia plano. */
    public Optional<String> onTakeoff() {
        return current == null ? Optional.of(NO_PLAN) : Optional.empty();
    }
}
