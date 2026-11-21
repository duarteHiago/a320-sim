package br.a320sim.dados.servico;

import br.a320sim.dados.modelo.Perfil;
import br.a320sim.dados.modelo.Pessoa;
import br.a320sim.dados.repositorio.PessoaRepositorio;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Regras do cadastro de pessoas: nome de 3 a 40 caracteres, matrícula obrigatória e única,
 * e-mail válido se preenchido. Pessoa que já tem sessões não é excluída, só desativada
 * (o histórico de treino continua apontando para ela).
 */
public class PessoaServico {

    public static final String CAMPO_NOME = "nome";
    public static final String CAMPO_MATRICULA = "matricula";
    public static final String CAMPO_PERFIL = "perfil";
    public static final String CAMPO_EMAIL = "email";

    public static final int NOME_MIN = 3;
    public static final int NOME_MAX = 40;
    public static final int MATRICULA_MAX = 20;
    public static final int EMAIL_MAX = 80;

    /** Simples de propósito: algo@algo.algo, sem espaços. */
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    /** O que aconteceu ao pedir para excluir. */
    public enum Exclusao { EXCLUIDA, DESATIVADA }

    private final PessoaRepositorio repositorio;

    public PessoaServico(PessoaRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    public List<Pessoa> listar() {
        return repositorio.listar();
    }

    /** Para a tela NOVA SESSAO: só quem está ativo. */
    public List<Pessoa> ativos(Perfil perfil) {
        return repositorio.listarAtivos(perfil);
    }

    public Optional<Pessoa> buscar(long id) {
        return repositorio.buscar(id);
    }

    /**
     * Insere (id nulo) ou atualiza. Os textos são aparados e e-mail vazio vira nulo.
     *
     * @throws ValidacaoException com um erro por campo inválido
     */
    public Pessoa salvar(Pessoa pessoa) {
        Pessoa p = new Pessoa(pessoa.id(), Erros.limpo(pessoa.nome()), Erros.limpo(pessoa.matricula()),
                pessoa.perfil(), Erros.limpo(pessoa.email()), pessoa.ativo());
        validar(p);
        if (p.id() == null) {
            return repositorio.inserir(p);
        }
        repositorio.atualizar(p);
        return p;
    }

    private void validar(Pessoa p) {
        Erros erros = new Erros();
        int nome = p.nome() == null ? 0 : p.nome().length();
        erros.se(nome < NOME_MIN || nome > NOME_MAX, CAMPO_NOME, "de " + NOME_MIN + " a " + NOME_MAX + " caracteres");

        erros.se(p.matricula() == null, CAMPO_MATRICULA, "obrigatoria");
        erros.se(p.matricula() != null && p.matricula().length() > MATRICULA_MAX, CAMPO_MATRICULA,
                "no maximo " + MATRICULA_MAX + " caracteres");
        if (!erros.tem(CAMPO_MATRICULA)) {
            Optional<Pessoa> dono = repositorio.buscarPorMatricula(p.matricula());
            erros.se(dono.isPresent() && !dono.get().id().equals(p.id()), CAMPO_MATRICULA,
                    "ja usada por " + dono.map(Pessoa::nome).orElse(""));
        }

        erros.se(p.perfil() == null, CAMPO_PERFIL, "obrigatorio");
        erros.se(p.email() != null && (p.email().length() > EMAIL_MAX || !EMAIL.matcher(p.email()).matches()),
                CAMPO_EMAIL, "e-mail invalido (ex.: nome@escola.com)");
        erros.lancarSeHouver();
    }

    /** Sem sessões: apaga. Com sessões: só desativa, para não perder o histórico. */
    public Exclusao excluir(long id) {
        Pessoa pessoa = repositorio.buscar(id).orElseThrow(() -> new RegraException("pessoa nao existe mais"));
        if (repositorio.contarSessoes(id) > 0) {
            repositorio.atualizar(pessoa.desativada());
            return Exclusao.DESATIVADA;
        }
        repositorio.excluir(id);
        return Exclusao.EXCLUIDA;
    }
}
