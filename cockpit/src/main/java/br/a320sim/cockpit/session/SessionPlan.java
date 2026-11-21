package br.a320sim.cockpit.session;

import br.a320sim.dados.modelo.Pessoa;
import br.a320sim.dados.modelo.Posicao;

/**
 * O que foi escolhido na tela NOVA SESSAO. Qualquer parte pode faltar: sem aluno a sessão não é
 * gravada; sem posição o avião fica na cabeceira (como sempre foi); instrutor só no modo INSTRUTOR.
 */
public record SessionPlan(Pessoa aluno, Pessoa instrutor, Posicao posicao) {

    public static final SessionPlan NONE = new SessionPlan(null, null, null);
}
