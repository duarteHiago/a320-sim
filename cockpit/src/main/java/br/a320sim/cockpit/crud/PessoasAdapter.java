package br.a320sim.cockpit.crud;

import br.a320sim.dados.modelo.Perfil;
import br.a320sim.dados.modelo.Pessoa;
import br.a320sim.dados.servico.PessoaServico;

import java.util.List;
import java.util.Map;

/** Cadastro de alunos e instrutores. */
public final class PessoasAdapter implements CrudAdapter<Pessoa> {

    private static final String ATIVO = "ativo";

    private final PessoaServico pessoas;

    public PessoasAdapter(PessoaServico pessoas) {
        this.pessoas = pessoas;
    }

    @Override
    public String titulo() {
        return "PESSOAS";
    }

    @Override
    public List<Pessoa> listar() {
        return pessoas.listar();
    }

    @Override
    public String linha(Pessoa p) {
        return String.format("%-6s %-9s %s", p.matricula(), p.perfil(), p.nome());
    }

    @Override
    public boolean apagado(Pessoa p) {
        return !p.ativo();
    }

    @Override
    public List<String> detalhes(Pessoa p) {
        return List.of(
                "NOME|" + p.nome(),
                "MATRICULA|" + p.matricula(),
                "PERFIL|" + p.perfil(),
                "E-MAIL|" + (p.email() == null ? "-" : p.email()),
                "ATIVO|" + (p.ativo() ? "SIM" : "NAO"),
                "SESSOES|" + pessoas.contarSessoes(p.id()));
    }

    @Override
    public List<Campo> campos() {
        return List.of(
                Campo.texto(PessoaServico.CAMPO_NOME, "NOME", PessoaServico.NOME_MAX),
                Campo.texto(PessoaServico.CAMPO_MATRICULA, "MATRICULA", PessoaServico.MATRICULA_MAX),
                Campo.opcao(PessoaServico.CAMPO_PERFIL, "PERFIL", List.of(Perfil.ALUNO.name(), Perfil.INSTRUTOR.name())),
                Campo.texto(PessoaServico.CAMPO_EMAIL, "E-MAIL", 44),
                Campo.opcao(ATIVO, "ATIVO", List.of("SIM", "NAO")));
    }

    @Override
    public Map<String, String> valoresNovos() {
        return Map.of(PessoaServico.CAMPO_PERFIL, Perfil.ALUNO.name(), ATIVO, "SIM");
    }

    @Override
    public Map<String, String> valores(Pessoa p) {
        return Map.of(
                PessoaServico.CAMPO_NOME, p.nome(),
                PessoaServico.CAMPO_MATRICULA, p.matricula(),
                PessoaServico.CAMPO_PERFIL, p.perfil().name(),
                PessoaServico.CAMPO_EMAIL, p.email() == null ? "" : p.email(),
                ATIVO, p.ativo() ? "SIM" : "NAO");
    }

    @Override
    public Pessoa salvar(Pessoa item, Map<String, String> v) {
        return pessoas.salvar(new Pessoa(item == null ? null : item.id(), v.get(PessoaServico.CAMPO_NOME),
                v.get(PessoaServico.CAMPO_MATRICULA), Perfil.valueOf(v.get(PessoaServico.CAMPO_PERFIL)),
                v.get(PessoaServico.CAMPO_EMAIL), "SIM".equals(v.get(ATIVO))));
    }

    @Override
    public String perguntaExclusao(Pessoa p) {
        return "EXCLUIR " + p.nome().toUpperCase() + "?";
    }

    @Override
    public String excluir(Pessoa p) {
        return switch (pessoas.excluir(p.id())) {
            case EXCLUIDA -> "PESSOA EXCLUIDA";
            case DESATIVADA -> "TEM SESSOES: SO FOI DESATIVADA";
        };
    }
}
