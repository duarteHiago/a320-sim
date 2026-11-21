package br.a320sim.dados.modelo;

/** Aluno ou instrutor. {@code id} é nulo antes de salvar; {@code email} pode ser nulo. */
public record Pessoa(Long id, String nome, String matricula, Perfil perfil, String email, boolean ativo) {

    public Pessoa comId(long novoId) {
        return new Pessoa(novoId, nome, matricula, perfil, email, ativo);
    }

    public Pessoa desativada() {
        return new Pessoa(id, nome, matricula, perfil, email, false);
    }
}
