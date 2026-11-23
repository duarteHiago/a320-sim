package br.a320sim.cockpit.session;

import br.a320sim.core.bus.Labels;
import br.a320sim.dados.Banco;
import br.a320sim.dados.BancoLocal;
import br.a320sim.dados.Dados;
import br.a320sim.dados.modelo.EventoSessao;
import br.a320sim.dados.modelo.Perfil;
import br.a320sim.dados.modelo.Pessoa;
import br.a320sim.dados.modelo.Posicao;
import br.a320sim.dados.modelo.Sessao;
import br.a320sim.host.Host;
import br.a320sim.host.command.SendMessageCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Depois do boot: a posição escolhida é aplicada e a sessão começa a ser gravada (SOLO e INSTRUTOR). */
class SessionKeeperTest {

    private final Host host = new Host();
    private final BancoLocal banco = new BancoLocal(() -> new Dados(Banco.memoria("keeper-" + UUID.randomUUID())));
    private final Dados dados = banco.abrir().orElseThrow();
    private final SessionKeeper keeper = new SessionKeeper(host, banco);

    @AfterEach
    void fechar() {
        keeper.finish();
        banco.close();
    }

    private Pessoa pessoa(Perfil perfil) {
        return dados.pessoas().ativos(perfil).get(0);
    }

    private Posicao posicao(String nome) {
        return dados.posicoes().listar().stream().filter(p -> p.nome().equals(nome)).findFirst().orElseThrow();
    }

    @Test
    void soloAplicaAPosicaoEGravaASessao() {
        Posicao emVoo = posicao("EM VOO 8000");
        keeper.start(new SessionPlan(pessoa(Perfil.ALUNO), pessoa(Perfil.INSTRUTOR), emVoo), Sessao.SOLO);

        assertEquals(8000, host.bus().getDouble(Labels.TRUE_ALT, 0), 1);
        assertTrue(keeper.recording());
        Sessao aberta = dados.sessoes().buscar(keeper.sessionId()).orElseThrow();
        assertEquals("Ana Souza", aberta.alunoNome());
        assertNull(aberta.instrutorId(), "no SOLO o instrutor fica vazio");
        assertEquals(emVoo.id(), aberta.posicaoId());

        long id = keeper.sessionId();
        host.advance(5);
        host.execute(new SendMessageCommand("ok"));
        keeper.update(1.0);
        keeper.finish();

        assertFalse(keeper.recording());
        Sessao fechada = dados.sessoes().buscar(id).orElseThrow();
        assertFalse(fechada.aberta());
        assertEquals(5, fechada.duracaoS(), Host.TICK_SECONDS);
        assertEquals(List.of("Posicao: EM VOO 8000", "Mensagem: ok"),
                dados.sessoes().eventos(id).stream().map(EventoSessao::descricao).toList());
    }

    @Test
    void instrutorFicaGravadoNoModoInstrutor() {
        Pessoa carla = pessoa(Perfil.INSTRUTOR);
        keeper.start(new SessionPlan(pessoa(Perfil.ALUNO), carla, posicao("NA FINAL 5NM")), Sessao.INSTRUTOR);
        Sessao s = dados.sessoes().buscar(keeper.sessionId()).orElseThrow();
        assertEquals(carla.id(), s.instrutorId());
        assertEquals("INSTRUTOR", s.modo());
        assertFalse(host.flightModel().isOnGround());
    }

    @Test
    void semAlunoAplicaAPosicaoMasNaoGrava() {
        keeper.start(new SessionPlan(null, null, posicao("NA FINAL 5NM")), Sessao.SOLO);
        assertFalse(keeper.recording());
        assertEquals(3770, host.bus().getDouble(Labels.TRUE_ALT, 0), 1);
        assertTrue(dados.sessoes().listar().isEmpty());
    }

    @Test
    void semBancoAplicaAPosicaoMasNaoGrava() {
        BancoLocal semBanco = new BancoLocal(() -> {
            throw new SQLException("Database may be already in use");
        });
        semBanco.abrir();
        SessionKeeper semGravar = new SessionKeeper(host, semBanco);
        semGravar.start(new SessionPlan(pessoa(Perfil.ALUNO), null, posicao("EM VOO 8000")), Sessao.SOLO);
        assertFalse(semGravar.recording());
        assertFalse(host.flightModel().isOnGround(), "a posicao vale mesmo sem banco");
        semGravar.finish();
    }

    @Test
    void novaSessaoFechaAAnterior() {
        keeper.start(new SessionPlan(pessoa(Perfil.ALUNO), null, null), Sessao.SOLO);
        long primeira = keeper.sessionId();
        keeper.start(new SessionPlan(pessoa(Perfil.ALUNO), null, null), Sessao.SOLO);
        assertFalse(dados.sessoes().buscar(primeira).orElseThrow().aberta());
        assertTrue(host.flightModel().isOnGround(), "sem posicao fica na cabeceira");
    }

    @Test
    void posicaoDeGuarulhosLevaOAviaoParaLa() {
        keeper.start(new SessionPlan(pessoa(Perfil.ALUNO), null, posicao("FINAL GRU 10L")), Sessao.SOLO);
        assertEquals(4059, host.bus().getDouble(Labels.TRUE_ALT, 0), 1);
        assertEquals(1600, host.bus().getDouble(Labels.SIM_RADIO_ALT, 0), 1);
        assertEquals("SBGR 10L", host.bus().getString(Labels.SIM_DEST_RUNWAY, ""));
    }
}
