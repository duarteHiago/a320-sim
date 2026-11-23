package br.a320sim.dados.servico;

import br.a320sim.dados.BancoDeTeste;
import br.a320sim.dados.Dados;
import br.a320sim.dados.modelo.Perfil;
import br.a320sim.dados.modelo.Pessoa;
import br.a320sim.dados.modelo.Sessao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PessoaServicoTest {

    private final Dados dados = BancoDeTeste.dados();
    private final PessoaServico servico = dados.pessoas();

    @AfterEach
    void fechar() {
        dados.close();
    }

    private static Pessoa aluno(String nome, String matricula, String email) {
        return new Pessoa(null, nome, matricula, Perfil.ALUNO, email, true);
    }

    private Map<String, String> erros(Pessoa p) {
        return assertThrows(ValidacaoException.class, () -> servico.salvar(p)).erros();
    }

    @Test
    void salvaComTextosAparadosEEmailVazioNulo() {
        Pessoa salva = servico.salvar(aluno("  Eva Prado ", " A900 ", "  "));
        assertEquals("Eva Prado", salva.nome());
        assertEquals("A900", salva.matricula());
        assertNull(salva.email());
        assertEquals(salva, servico.buscar(salva.id()).orElseThrow());
    }

    @Test
    void nomeDe3a40Caracteres() {
        assertTrue(erros(aluno("Ed", "A901", null)).containsKey(PessoaServico.CAMPO_NOME));
        assertTrue(erros(aluno("x".repeat(41), "A901", null)).containsKey(PessoaServico.CAMPO_NOME));
        servico.salvar(aluno("Edu", "A901", null));
        servico.salvar(aluno("x".repeat(40), "A902", null));
    }

    @Test
    void matriculaObrigatoriaEUnica() {
        assertEquals("obrigatoria", erros(aluno("Eva Prado", " ", null)).get(PessoaServico.CAMPO_MATRICULA));
        assertEquals("ja usada por Ana Souza", erros(aluno("Eva Prado", "a001", null)).get(PessoaServico.CAMPO_MATRICULA));
    }

    @Test
    void editarAPropriaPessoaNaoConflitaComAPropriaMatricula() {
        Pessoa ana = servico.listar().get(0);
        Pessoa editada = servico.salvar(new Pessoa(ana.id(), "Ana Souza Lima", ana.matricula(), ana.perfil(),
                ana.email(), true));
        assertEquals("Ana Souza Lima", servico.buscar(ana.id()).orElseThrow().nome());
        assertEquals(editada, servico.buscar(ana.id()).orElseThrow());
    }

    @Test
    void emailValidoSePreenchido() {
        assertTrue(erros(aluno("Eva Prado", "A903", "eva.com")).containsKey(PessoaServico.CAMPO_EMAIL));
        assertTrue(erros(aluno("Eva Prado", "A903", "eva @x.com")).containsKey(PessoaServico.CAMPO_EMAIL));
        assertTrue(erros(aluno("Eva Prado", "A903", "eva@x")).containsKey(PessoaServico.CAMPO_EMAIL));
        servico.salvar(aluno("Eva Prado", "A903", "eva@escola.com.br"));
    }

    @Test
    void mostraTodosOsErrosDeUmaVez() {
        Map<String, String> erros = erros(aluno("", "", "x"));
        assertEquals(3, erros.size(), erros.toString());
    }

    @Test
    void semSessoesExcluiComSessoesSoDesativa() {
        Pessoa eva = servico.salvar(aluno("Eva Prado", "A904", null));
        assertEquals(PessoaServico.Exclusao.EXCLUIDA, servico.excluir(eva.id()));
        assertTrue(servico.buscar(eva.id()).isEmpty());

        Pessoa ana = servico.listar().get(0);
        dados.sessoes().abrir(ana.id(), null, Sessao.SOLO, null, "");
        assertEquals(PessoaServico.Exclusao.DESATIVADA, servico.excluir(ana.id()));
        assertFalse(servico.buscar(ana.id()).orElseThrow().ativo());
        assertFalse(servico.ativos(Perfil.ALUNO).contains(ana));
    }
}
