package br.a320sim.dados;

/** Erro de acesso ao banco (SQLException embrulhada, para não espalhar exceção checada pelas telas). */
public class DadosException extends RuntimeException {

    public DadosException(String mensagem, Throwable causa) {
        super(mensagem + ": " + causa.getMessage(), causa);
    }
}
