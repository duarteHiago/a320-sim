package br.a320sim.cockpit.crud;

import br.a320sim.dados.servico.ValidacaoException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converte o texto dos campos em números, juntando os erros por campo. Se algum campo não é número,
 * {@link #lancarSeHouver()} lança antes de chegar no serviço (que valida as faixas).
 */
public final class Conversao {

    private final Map<String, String> valores;
    private final Map<String, String> erros = new LinkedHashMap<>();

    public Conversao(Map<String, String> valores) {
        this.valores = valores;
    }

    public String texto(String chave) {
        return valores.getOrDefault(chave, "");
    }

    public int inteiro(String chave) {
        String t = texto(chave).strip();
        if (t.isEmpty()) {
            erros.put(chave, "obrigatorio");
            return 0;
        }
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            erros.put(chave, "numero inteiro (ex.: 250)");
            return 0;
        }
    }

    public double decimal(String chave) {
        Double d = decimalOpcional(chave);
        if (d == null && !erros.containsKey(chave)) {
            erros.put(chave, "obrigatorio");
        }
        return d == null ? 0 : d;
    }

    /** Vazio = nulo. Aceita vírgula ou ponto (7,5 ou 7.5). */
    public Double decimalOpcional(String chave) {
        String t = texto(chave).strip().replace(',', '.');
        if (t.isEmpty()) {
            return null;
        }
        try {
            double d = Double.parseDouble(t);
            if (Double.isFinite(d)) {
                return d;
            }
        } catch (NumberFormatException e) {
            // cai no erro abaixo
        }
        erros.put(chave, "numero invalido (ex.: 7.5)");
        return null;
    }

    public void lancarSeHouver() {
        if (!erros.isEmpty()) {
            throw new ValidacaoException(erros);
        }
    }
}
