package br.a320sim.host.session;

import br.a320sim.core.bus.Labels;
import br.a320sim.host.Host;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Grava uma sessão enquanto ela acontece: a cada {@link #FLUSH_SECONDS} manda para o
 * {@link SessionStore} as entradas novas do {@code Host.log()}. Assim, se o programa travar,
 * só se perde o último segundo.
 * <p>
 * Um erro do store (banco fora do ar) vai para o console e é tentado de novo depois; o voo continua.
 */
public class SessionRecorder {

    public static final double FLUSH_SECONDS = 1;

    private final Host host;
    private final SessionStore store;
    private final Set<String> cartridges = new LinkedHashSet<>();
    private int written;
    private double sinceFlush;
    private boolean crashed;
    private boolean closed;

    public SessionRecorder(Host host, SessionStore store) {
        this.host = host;
        this.store = store;
        observe();
    }

    /** Chamado a cada quadro com o tempo real que passou. */
    public void update(double delta) {
        if (closed) {
            return;
        }
        observe();
        sinceFlush += delta;
        if (sinceFlush >= FLUSH_SECONDS) {
            sinceFlush = 0;
            flush();
        }
    }

    /** Grava o que falta do log e fecha; chamar de novo não faz nada. */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        observe();
        flush();
        try {
            store.close(host.simTime(), crashed, List.copyOf(cartridges));
        } catch (RuntimeException e) {
            System.err.println("[SESSAO] nao consegui fechar a sessao: " + e.getMessage());
        }
    }

    public boolean isClosed() {
        return closed;
    }

    /** Crash e cartuchos usados são acumulados: um reset ou um cartão arrancado não apagam o histórico. */
    private void observe() {
        crashed |= host.bus().getBoolean(Labels.SIM_CRASHED);
        host.rack().slots().values().forEach(c -> cartridges.add(c.manifest().partNumber()));
    }

    private void flush() {
        List<Host.LoggedCommand> log = host.log();
        if (log.size() < written) {
            written = 0; // o log foi zerado (avião religado): recomeça do início
        }
        if (log.size() == written) {
            return;
        }
        List<Host.LoggedCommand> fresh = List.copyOf(log.subList(written, log.size()));
        try {
            store.appendEvents(fresh);
            written += fresh.size();
            crashed |= fresh.stream().anyMatch(e -> e.description().equals(FlightEvents.CRASH));
        } catch (RuntimeException e) {
            System.err.println("[SESSAO] eventos nao gravados (tento de novo): " + e.getMessage());
        }
    }
}
