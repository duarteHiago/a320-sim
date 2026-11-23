package br.a320sim.dados.modelo;

/** Algo que aconteceu na sessão (comando do instrutor, decolagem, pouso...), no tempo da simulação. */
public record EventoSessao(double tempoS, String descricao) {
}
