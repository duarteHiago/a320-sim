package br.a320sim.host.birds;

/**
 * Um bando de aves migrando em linha reta: centro (NM), altitude (ft acima do nível do mar),
 * rumo da migração e velocidade.
 */
public record BirdFlock(double xNm, double yNm, double altFt, double headingDeg, double speedKt) {

    /** O bando depois de {@code seconds} segundos de voo. */
    public BirdFlock moved(double seconds) {
        double nm = speedKt * seconds / 3600;
        double h = Math.toRadians(headingDeg);
        return new BirdFlock(xNm + Math.sin(h) * nm, yNm + Math.cos(h) * nm, altFt, headingDeg, speedKt);
    }
}
