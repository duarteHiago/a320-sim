package br.a320sim.dados.modelo;

/** Onde o avião começa: na cabeceira (velocidade e distância 0) ou em voo no prolongamento da pista. */
public record Posicao(Long id, String nome, int altitudeFt, int velocidadeKt, int proaGraus, double distanciaNm,
                      String destino) {

    public static boolean noChao(int velocidadeKt, double distanciaNm) {
        return velocidadeKt == 0 && distanciaNm == 0;
    }

    public boolean noChao() {
        return noChao(velocidadeKt, distanciaNm);
    }

    public Posicao comId(long novoId) {
        return new Posicao(novoId, nome, altitudeFt, velocidadeKt, proaGraus, distanciaNm, destino);
    }

    public Posicao comAltitude(int novaAltitude) {
        return new Posicao(id, nome, novaAltitude, velocidadeKt, proaGraus, distanciaNm, destino);
    }
}
