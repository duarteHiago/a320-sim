package br.a320sim.cockpit.crud;

import java.util.List;
import java.util.Map;

/**
 * O que a {@link br.a320sim.cockpit.screen.CrudScreen} precisa saber de uma entidade (padrão Adapter):
 * a mesma tela de lista + formulário serve para os 4 cadastros, cada um com o seu adaptador.
 *
 * @param <T> o record da entidade (Pessoa, Cartao...)
 */
public interface CrudAdapter<T> {

    /** Nome do cadastro no topo da tela, ex.: "PESSOAS". */
    String titulo();

    List<T> listar();

    /** Uma linha da lista à esquerda (cabe ~36 caracteres). */
    String linha(T item);

    /** Itens "apagados" na lista (pessoa inativa, cartão bloqueado...). */
    default boolean apagado(T item) {
        return false;
    }

    /** Linhas do painel de detalhes; "ROTULO|valor" vira rótulo cinza e valor branco. */
    List<String> detalhes(T item);

    /** Sessões não são criadas à mão. */
    default boolean podeCriar() {
        return true;
    }

    List<Campo> campos();

    /** Valores do formulário de um item novo. */
    Map<String, String> valoresNovos();

    /** Valores do formulário para editar um item. */
    Map<String, String> valores(T item);

    /**
     * Salva (item nulo = novo) e devolve como ficou gravado.
     *
     * @throws br.a320sim.dados.servico.ValidacaoException erros por campo
     * @throws br.a320sim.dados.servico.RegraException     regra de negócio
     */
    T salvar(T item, Map<String, String> valores);

    /** Pergunta de confirmação, ex.: "EXCLUIR ANA SOUZA?" */
    String perguntaExclusao(T item);

    /**
     * Exclui e diz o que aconteceu (ex.: "PESSOA DESATIVADA: TEM SESSOES").
     *
     * @throws br.a320sim.dados.servico.RegraException se não pode excluir
     */
    String excluir(T item);
}
