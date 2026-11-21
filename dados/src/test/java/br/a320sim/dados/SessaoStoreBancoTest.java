package br.a320sim.dados;

import br.a320sim.dados.modelo.EventoSessao;
import br.a320sim.dados.modelo.Sessao;
import br.a320sim.host.Host;
import br.a320sim.host.command.ApplyPositionCommand;
import br.a320sim.host.command.SendMessageCommand;
import br.a320sim.host.session.SessionRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** O gravador do host escrevendo no banco de verdade. */
class SessaoStoreBancoTest {

    private final Dados dados = BancoDeTeste.dados();

    @AfterEach
    void fechar() {
        dados.close();
    }

    @Test
    void sessaoGravadaDuranteOVooEFechadaNoFim() {
        Host host = new Host();
        long aluno = dados.pessoas().listar().get(0).id();
        long id = dados.sessoes().abrir(aluno, null, Sessao.SOLO, null, "");
        SessionRecorder gravador = new SessionRecorder(host, new SessaoStoreBanco(dados.sessoes(), id));

        host.execute(new ApplyPositionCommand("EM VOO 8000", 8000, 250, 148, 10));
        gravador.update(1.0);
        assertEquals(List.of("Posicao: EM VOO 8000"),
                dados.sessoes().eventos(id).stream().map(EventoSessao::descricao).toList(),
                "ja gravado no meio do voo");

        host.advance(30);
        host.execute(new SendMessageCommand("reduza"));
        gravador.close();

        Sessao s = dados.sessoes().buscar(id).orElseThrow();
        assertFalse(s.aberta());
        assertEquals(30, s.duracaoS(), Host.TICK_SECONDS);
        List<EventoSessao> eventos = dados.sessoes().eventos(id);
        assertEquals(2, eventos.size());
        assertEquals(30, eventos.get(1).tempoS(), Host.TICK_SECONDS);
    }
}
