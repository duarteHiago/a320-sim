package br.a320sim.host.failure;

import br.a320sim.core.bus.DataBus;

/**
 * Uma pane que o instrutor injeta. A cada ciclo, depois que as caixas trabalham,
 * a falha "estraga" a parte do barramento que lhe cabe.
 */
public interface Failure {

    String name();

    void apply(DataBus bus);
}
