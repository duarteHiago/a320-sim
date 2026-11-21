package br.a320sim.dados.modelo;

import java.time.LocalDateTime;

/**
 * Uma sessão de treino, gravada sozinha pelo simulador. Os nomes (aluno, instrutor, posição) vêm do
 * JOIN na leitura, só para mostrar; quem vale são os ids. {@code fim}, {@code duracaoS} e {@code nota}
 * ficam nulos enquanto a sessão está aberta ou não foi avaliada.
 */
public record Sessao(Long id, long alunoId, String alunoNome, Long instrutorId, String instrutorNome, String modo,
                     Long posicaoId, String posicaoNome, String cartuchos, LocalDateTime inicio, LocalDateTime fim,
                     Double duracaoS, boolean crash, Double nota, String observacao) {

    public static final String SOLO = "SOLO";
    public static final String INSTRUTOR = "INSTRUTOR";

    /** Sessão nova, como o simulador abre depois do boot. */
    public static Sessao nova(long alunoId, Long instrutorId, String modo, Long posicaoId, String cartuchos,
                              LocalDateTime inicio) {
        return new Sessao(null, alunoId, null, instrutorId, null, modo, posicaoId, null, cartuchos, inicio, null,
                null, false, null, null);
    }

    public boolean aberta() {
        return fim == null;
    }
}
