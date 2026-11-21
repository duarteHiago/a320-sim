package br.a320sim.flightmodel.world;

/**
 * Uma pista num sentido (ex.: SBGR 10L). A cabeceira fica em (xNm, yNm) no mundo e a pista segue
 * {@code lengthM} metros na proa {@code headingDeg}. O sentido contrário (28R) é outro Runway, com a
 * cabeceira na outra ponta.
 *
 * @param headingDeg proa no mundo do simulador (alinhado ao norte magnético, ver {@link Geo})
 */
public record Runway(String airport, String designator, double xNm, double yNm, double headingDeg,
                     double lengthM, double widthM, double elevationFt) {

    public static final double METERS_PER_NM = 1852;

    /** "SBGR 10L" */
    public String id() {
        return airport + " " + designator;
    }

    public double lengthNm() {
        return lengthM / METERS_PER_NM;
    }

    public double endXNm() {
        return xNm + Math.sin(Math.toRadians(headingDeg)) * lengthNm();
    }

    public double endYNm() {
        return yNm + Math.cos(Math.toRadians(headingDeg)) * lengthNm();
    }

    /** A mesma pista no outro sentido. */
    public Runway reversed(String designator) {
        return new Runway(airport, designator, endXNm(), endYNm(), Geo.wrap360(headingDeg + 180), lengthM, widthM,
                elevationFt);
    }

    /** Quanto um ponto está à frente da cabeceira, ao longo do eixo (NM; negativo = antes da pista). */
    public double alongNm(double x, double y) {
        double h = Math.toRadians(headingDeg);
        return (x - xNm) * Math.sin(h) + (y - yNm) * Math.cos(h);
    }

    /** Quanto um ponto está à direita do eixo (NM; negativo = à esquerda). */
    public double acrossNm(double x, double y) {
        double h = Math.toRadians(headingDeg);
        return (x - xNm) * Math.cos(h) - (y - yNm) * Math.sin(h);
    }

    /** Ponto no prolongamento do eixo, {@code distanceNm} antes da cabeceira: {x, y}. */
    public double[] pointBefore(double distanceNm) {
        double h = Math.toRadians(headingDeg);
        return new double[]{xNm - Math.sin(h) * distanceNm, yNm - Math.cos(h) * distanceNm};
    }
}
