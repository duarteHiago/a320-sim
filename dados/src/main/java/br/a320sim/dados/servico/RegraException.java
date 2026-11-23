package br.a320sim.dados.servico;

/** Operação proibida por uma regra do negócio (ex.: excluir posição usada em sessões). */
public class RegraException extends RuntimeException {

    public RegraException(String mensagem) {
        super(mensagem);
    }
}
