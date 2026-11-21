package br.a320sim.host.session;

import br.a320sim.host.Host;

import java.util.List;

/**
 * Onde uma sessão de treino aberta é gravada. O host não sabe que existe banco:
 * a implementação de verdade fica no módulo {@code dados}.
 */
public interface SessionStore {

    /** Mais entradas do log desde a última chamada (chamado aos poucos durante o voo). */
    void appendEvents(List<Host.LoggedCommand> events);

    /** Fim da sessão: duração em tempo de simulação, se houve crash e os cartuchos que passaram pelo rack. */
    void close(double simSeconds, boolean crashed, List<String> cartridges);
}
