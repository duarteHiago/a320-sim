package br.a320sim.dados.repositorio;

import br.a320sim.dados.Banco;
import br.a320sim.dados.BancoDeTeste;
import br.a320sim.dados.DadosException;
import br.a320sim.dados.modelo.Cartao;
import br.a320sim.dados.modelo.EventoSessao;
import br.a320sim.dados.modelo.Perfil;
import br.a320sim.dados.modelo.Pessoa;
import br.a320sim.dados.modelo.Posicao;
import br.a320sim.dados.modelo.Sessao;
import br.a320sim.dados.modelo.StatusCartao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** O SQL de cada repositório contra um H2 em memória (com o seed). */
class RepositoriosTest {

    private Banco banco;
    private PessoaRepositorio pessoas;
    private CartaoRepositorio cartoes;
    private PosicaoRepositorio posicoes;
    private SessaoRepositorio sessoes;

    @BeforeEach
    void abrir() {
        banco = BancoDeTeste.banco();
        pessoas = new PessoaRepositorio(banco.conexao());
        cartoes = new CartaoRepositorio(banco.conexao());
        posicoes = new PosicaoRepositorio(banco.conexao());
        sessoes = new SessaoRepositorio(banco.conexao());
    }

    @AfterEach
    void fechar() {
        banco.close();
    }

    @Test
    void pessoaInsereBuscaAtualizaEExclui() {
        Pessoa nova = pessoas.inserir(new Pessoa(null, "Eva Prado", "A900", Perfil.ALUNO, null, true));
        assertEquals(nova, pessoas.buscar(nova.id()).orElseThrow());
        assertEquals(nova, pessoas.buscarPorMatricula("a900").orElseThrow(), "matricula sem diferenciar maiusculas");

        pessoas.atualizar(new Pessoa(nova.id(), "Eva Prado Lima", "A900", Perfil.INSTRUTOR, "eva@x.com", false));
        Pessoa lida = pessoas.buscar(nova.id()).orElseThrow();
        assertEquals("Eva Prado Lima", lida.nome());
        assertEquals(Perfil.INSTRUTOR, lida.perfil());
        assertFalse(lida.ativo());

        pessoas.excluir(nova.id());
        assertTrue(pessoas.buscar(nova.id()).isEmpty());
    }

    @Test
    void pessoaListaAlunosAntesEFiltraAtivos() {
        List<Pessoa> todas = pessoas.listar();
        assertEquals(4, todas.size());
        assertEquals(Perfil.ALUNO, todas.get(0).perfil());
        assertEquals(Perfil.INSTRUTOR, todas.get(3).perfil());

        Pessoa bruno = pessoas.buscarPorMatricula("A002").orElseThrow();
        pessoas.atualizar(bruno.desativada());
        assertEquals(List.of("Ana Souza"), pessoas.listarAtivos(Perfil.ALUNO).stream().map(Pessoa::nome).toList());
    }

    @Test
    void bancoRecusaMatriculaRepetidaMesmoSemOServico() {
        assertThrows(DadosException.class,
                () -> pessoas.inserir(new Pessoa(null, "Outra Ana", "A001", Perfil.ALUNO, null, true)));
    }

    @Test
    void cartaoAceitaVariosSemEtiquetaMasNaoEtiquetaRepetida() {
        cartoes.inserir(new Cartao(null, null, "PLT-A320-CEO", "1111-2222", null, null, null, 1, StatusCartao.ATIVO, null));
        cartoes.inserir(new Cartao(null, null, "PLT-A320-CEO", "3333-4444", null, null, null, 1, StatusCartao.ATIVO, null));
        assertEquals(8, cartoes.listar().size());
        assertThrows(DadosException.class,
                () -> cartoes.inserir(Cartao.manual("SD-01", "PRP-A320-NEO", StatusCartao.ATIVO, null)));
    }

    @Test
    void cartaoBuscaPorVolumeEOManualSemVolume() {
        Cartao manual = cartoes.buscarManualSemVolume("CKP-A320-NEO").orElseThrow();
        assertEquals("SD-06", manual.etiqueta());

        Cartao lido = new Cartao(manual.id(), manual.etiqueta(), manual.partNumber(), "ABCD-1234", "ab".repeat(32),
                LocalDate.of(2026, 11, 1), BancoDeTeste.AGORA, 1, StatusCartao.ATIVO, "lido");
        cartoes.atualizar(lido);
        assertEquals(lido, cartoes.buscarPorVolume("ABCD-1234").orElseThrow());
        assertTrue(cartoes.buscarManualSemVolume("CKP-A320-NEO").isEmpty(), "ja foi adotado");

        cartoes.excluir(manual.id());
        assertTrue(cartoes.buscar(manual.id()).isEmpty());
    }

    @Test
    void posicaoInsereAtualizaEContaSessoes() {
        Posicao nova = posicoes.inserir(new Posicao(null, "TESTE", 5000, 200, 90, 12.5, "SBKP"));
        assertEquals(nova, posicoes.buscarPorNome("teste").orElseThrow());
        posicoes.atualizar(new Posicao(nova.id(), "TESTE 2", 6000, 210, 91, 13, "SBKP"));
        assertEquals(6000, posicoes.buscar(nova.id()).orElseThrow().altitudeFt());
        assertEquals(List.of("PISTA 15", "EM VOO 8000", "NA FINAL 5NM", "TESTE 2"),
                posicoes.listar().stream().map(Posicao::nome).toList());

        assertEquals(0, posicoes.contarSessoes(nova.id()));
        long aluno = pessoas.buscarPorMatricula("A001").orElseThrow().id();
        sessoes.inserir(Sessao.nova(aluno, null, Sessao.SOLO, nova.id(), "", BancoDeTeste.AGORA));
        assertEquals(1, posicoes.contarSessoes(nova.id()));
        assertThrows(DadosException.class, () -> posicoes.excluir(nova.id()), "a FK protege a sessao");
    }

    @Test
    void sessaoAbreFechaAvaliaComNomesDoJoin() {
        Pessoa ana = pessoas.buscarPorMatricula("A001").orElseThrow();
        Pessoa carla = pessoas.buscarPorMatricula("I001").orElseThrow();
        Posicao pista = posicoes.buscarPorNome("PISTA 15").orElseThrow();
        long id = sessoes.inserir(Sessao.nova(ana.id(), carla.id(), Sessao.INSTRUTOR, pista.id(),
                "PLT-A320-NEO", BancoDeTeste.AGORA));

        Sessao aberta = sessoes.buscar(id).orElseThrow();
        assertTrue(aberta.aberta());
        assertEquals("Ana Souza", aberta.alunoNome());
        assertEquals("Carla Mendes", aberta.instrutorNome());
        assertEquals("PISTA 15", aberta.posicaoNome());
        assertNull(aberta.nota());

        sessoes.fechar(id, BancoDeTeste.AGORA.plusMinutes(20), 1180.5, true, "PLT-A320-NEO  PRP-A320-NEO");
        sessoes.avaliar(id, 7.5, "bom pouso");
        Sessao fechada = sessoes.buscar(id).orElseThrow();
        assertFalse(fechada.aberta());
        assertEquals(1180.5, fechada.duracaoS());
        assertTrue(fechada.crash());
        assertEquals(7.5, fechada.nota());
        assertEquals("bom pouso", fechada.observacao());
        assertEquals(1, pessoas.contarSessoes(carla.id()), "conta tambem como instrutor");
    }

    @Test
    void eventosSaoGravadosEmLotesEApagadosComASessao() {
        long aluno = pessoas.buscarPorMatricula("A002").orElseThrow().id();
        long id = sessoes.inserir(Sessao.nova(aluno, null, Sessao.SOLO, null, null, BancoDeTeste.AGORA));
        sessoes.inserirEventos(id, List.of(new EventoSessao(1.5, "DECOLAGEM"), new EventoSessao(0.2, "Posicao: PISTA 15")));
        sessoes.inserirEventos(id, List.of(new EventoSessao(300, "POUSO V/S -420 FT/MIN")));
        assertEquals(List.of("Posicao: PISTA 15", "DECOLAGEM", "POUSO V/S -420 FT/MIN"),
                sessoes.eventos(id).stream().map(EventoSessao::descricao).toList(), "em ordem de tempo");

        sessoes.excluir(id);
        assertTrue(sessoes.buscar(id).isEmpty());
        assertTrue(sessoes.eventos(id).isEmpty(), "ON DELETE CASCADE");
    }
}
