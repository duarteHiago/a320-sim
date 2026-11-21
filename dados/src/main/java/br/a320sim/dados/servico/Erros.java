package br.a320sim.dados.servico;

import java.util.LinkedHashMap;
import java.util.Map;

/** Junta os erros de validação de um cadastro e lança todos de uma vez no fim. */
final class Erros {

    private final Map<String, String> erros = new LinkedHashMap<>();

    /** Guarda só o primeiro erro de cada campo. */
    void se(boolean condicao, String campo, String mensagem) {
        if (condicao) {
            erros.putIfAbsent(campo, mensagem);
        }
    }

    boolean tem(String campo) {
        return erros.containsKey(campo);
    }

    void lancarSeHouver() {
        if (!erros.isEmpty()) {
            throw new ValidacaoException(erros);
        }
    }

    /** Texto aparado; vazio vira nulo. */
    static String limpo(String texto) {
        if (texto == null) {
            return null;
        }
        String t = texto.strip();
        return t.isEmpty() ? null : t;
    }
}
