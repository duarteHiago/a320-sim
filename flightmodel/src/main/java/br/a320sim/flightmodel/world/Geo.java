package br.a320sim.flightmodel.world;

/**
 * Converte latitude/longitude em posições do "mundo" do simulador, em milhas náuticas:
 * x para leste, y para norte, com a cabeceira 15 de Viracopos na origem.
 * <p>
 * O mundo é alinhado ao norte <b>magnético</b>, que é o que o piloto lê nos instrumentos: o mapa geográfico
 * é girado para que a pista 15 de SBKP fique com proa 148 (o valor que o simulador sempre usou).
 * Com isso, as outras pistas também ficam com proas próximas das magnéticas publicadas.
 * <p>
 * Usa a aproximação de Terra plana (projeção equiretangular): para os ~50 NM entre os três aeroportos,
 * o erro é de décimos de milha, invisível no simulador.
 */
public final class Geo {

    /** Cabeceira 15 de SBKP (origem do mundo) e a 33, que dá a direção da pista. */
    static final double ORIGIN_LAT = -22.9985;
    static final double ORIGIN_LON = -47.1470;
    static final double SBKP_33_LAT = -23.0164;
    static final double SBKP_33_LON = -47.1220;
    /** Proa da pista 15 no simulador. */
    public static final double HOME_HEADING = 148;
    /** Quanto o mapa é girado (graus, sentido horário): proa no mundo = proa verdadeira + ROTATION_DEG. */
    public static final double ROTATION_DEG = HOME_HEADING
            - trueBearing(ORIGIN_LAT, ORIGIN_LON, SBKP_33_LAT, SBKP_33_LON);

    private static final double NM_PER_DEGREE = 60;

    private Geo() {
    }

    /** Rumo verdadeiro de um ponto a outro, em graus de 0 a 360. */
    public static double trueBearing(double lat1, double lon1, double lat2, double lon2) {
        double[] d = eastNorth(lat1, lon1, lat2, lon2);
        return wrap360(Math.toDegrees(Math.atan2(d[0], d[1])));
    }

    /** {leste, norte} em NM de um ponto até outro, sem girar. */
    static double[] eastNorth(double lat1, double lon1, double lat2, double lon2) {
        double meanLat = Math.toRadians((lat1 + lat2) / 2);
        return new double[]{(lon2 - lon1) * NM_PER_DEGREE * Math.cos(meanLat), (lat2 - lat1) * NM_PER_DEGREE};
    }

    /** Posição no mundo {x, y} em NM. */
    public static double[] toWorld(double lat, double lon) {
        double[] d = eastNorth(ORIGIN_LAT, ORIGIN_LON, lat, lon);
        // Gira no sentido horário: um vetor com rumo verdadeiro R passa a ter rumo R + ROTATION_DEG
        double r = Math.toRadians(ROTATION_DEG);
        return new double[]{d[0] * Math.cos(r) + d[1] * Math.sin(r), -d[0] * Math.sin(r) + d[1] * Math.cos(r)};
    }

    public static double wrap360(double degrees) {
        return ((degrees % 360) + 360) % 360;
    }
}
