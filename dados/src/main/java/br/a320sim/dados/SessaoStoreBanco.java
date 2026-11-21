package br.a320sim.dados;

import br.a320sim.dados.modelo.EventoSessao;
import br.a320sim.dados.servico.SessaoServico;
import br.a320sim.host.Host;
import br.a320sim.host.session.SessionStore;

import java.util.List;

/** O {@link SessionStore} do host gravando numa sessão já aberta no banco. */
public class SessaoStoreBanco implements SessionStore {

    private final SessaoServico sessoes;
    private final long sessaoId;

    public SessaoStoreBanco(SessaoServico sessoes, long sessaoId) {
        this.sessoes = sessoes;
        this.sessaoId = sessaoId;
    }

    public long sessaoId() {
        return sessaoId;
    }

    @Override
    public void appendEvents(List<Host.LoggedCommand> events) {
        sessoes.registrarEventos(sessaoId, events.stream()
                .map(e -> new EventoSessao(e.simTime(), e.description()))
                .toList());
    }

    @Override
    public void close(double simSeconds, boolean crashed, List<String> cartridges) {
        sessoes.fechar(sessaoId, simSeconds, crashed, String.join("  ", cartridges));
    }
}
