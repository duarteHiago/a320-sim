package br.a320sim.dados.modelo;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Um cartão SD físico. A etiqueta (ex.: "SD-01") é o que está escrito no cartão; o {@code volumeId}
 * (número de série do FAT32) é como o simulador o reconhece. Os dois podem estar vazios:
 * cadastro manual ainda não lido, ou cartão lido que ainda não foi etiquetado.
 */
public record Cartao(Long id, String etiqueta, String partNumber, String volumeId, String sha256,
                     LocalDate gravadoEm, LocalDateTime ultimaLeitura, int leituras, StatusCartao status,
                     String observacao) {

    /** Cadastro manual: só o que se digita no formulário. */
    public static Cartao manual(String etiqueta, String partNumber, StatusCartao status, String observacao) {
        return new Cartao(null, etiqueta, partNumber, null, null, null, null, 0, status, observacao);
    }

    public Cartao comId(long novoId) {
        return new Cartao(novoId, etiqueta, partNumber, volumeId, sha256, gravadoEm, ultimaLeitura, leituras,
                status, observacao);
    }

    /** Nome para mensagens: a etiqueta, ou o part number se ainda não tem etiqueta. */
    public String nomeCurto() {
        return etiqueta != null ? etiqueta : partNumber;
    }
}
