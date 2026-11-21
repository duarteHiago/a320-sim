package br.a320sim.dados.servico;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dados inválidos num cadastro. Cada erro é ligado a um campo, para o formulário mostrar
 * a mensagem embaixo dele. As chaves são as constantes {@code CAMPO_*} de cada serviço.
 */
public class ValidacaoException extends RuntimeException {

    private final Map<String, String> erros;

    public ValidacaoException(Map<String, String> erros) {
        super(erros.toString());
        this.erros = Collections.unmodifiableMap(new LinkedHashMap<>(erros));
    }

    public static ValidacaoException de(String campo, String mensagem) {
        return new ValidacaoException(Map.of(campo, mensagem));
    }

    public Map<String, String> erros() {
        return erros;
    }
}
