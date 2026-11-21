package br.a320sim.dados.modelo;

/**
 * Onde o avião começa: na cabeceira (velocidade e distância 0) ou em voo no prolongamento de uma pista
 * do aeroporto de destino ({@code destino} + {@code pista}, ex.: SBGR 10L).
 */
public record Posicao(Long id, String nome, int altitudeFt, int velocidadeKt, int proaGraus, double distanciaNm,
                      String destino, String pista) {

    /** Sem pista informada: a 15 (de Viracopos), como antes da Fase 3. */
    public Posicao(Long id, String nome, int altitudeFt, int velocidadeKt, int proaGraus, double distanciaNm,
                   String destino) {
        this(id, nome, altitudeFt, velocidadeKt, proaGraus, distanciaNm, destino, "15");
    }

    public static boolean noChao(int velocidadeKt, double distanciaNm) {
        return velocidadeKt == 0 && distanciaNm == 0;
    }

    public boolean noChao() {
        return noChao(velocidadeKt, distanciaNm);
    }

    public Posicao comId(long novoId) {
        return new Posicao(novoId, nome, altitudeFt, velocidadeKt, proaGraus, distanciaNm, destino, pista);
    }

    public Posicao comAltitude(int novaAltitude) {
        return new Posicao(id, nome, novaAltitude, velocidadeKt, proaGraus, distanciaNm, destino, pista);
    }
}
