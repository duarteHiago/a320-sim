package br.a320sim.flightmodel.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Os aeroportos do simulador e o terreno entre eles.
 * <p>
 * Coordenadas das cabeceiras, comprimentos e elevações vêm de fontes públicas (SkyVector, PilotNav,
 * airportguide, sprinkle; ver o README). São aproximados: servem para simulação, não para navegação.
 * Cabeceiras deslocadas são ignoradas.
 */
public final class World {

    /** Raio (NM) em volta de cada aeroporto onde o chão é plano, na elevação dele. */
    public static final double FLAT_RADIUS_NM = 6.5;

    public static final Airport SBKP = new Airport("SBKP", "Viracopos", 2170, List.of(
            Airport.strip("SBKP", "15", -22.9985, -47.1470, -23.0164, -47.1220, 3240, 45, 2170)));

    /** Desde 2022 as pistas são 10L/28R e 10R/28L (eram 09L/27R e 09R/27L): mudança do campo magnético. */
    public static final Airport SBGR = new Airport("SBGR", "Guarulhos", 2459, List.of(
            Airport.strip("SBGR", "10L", -23.4343, -46.4834, -23.4249, -46.4486, 3700, 45, 2459),
            Airport.strip("SBGR", "10R", -23.4388, -46.4870, -23.4312, -46.4589, 3000, 45, 2459)));

    public static final Airport SBSP = new Airport("SBSP", "Congonhas", 2631, List.of(
            Airport.strip("SBSP", "17R", -23.6210, -46.6604, -23.6343, -46.6512, 1940, 45, 2631),
            Airport.strip("SBSP", "17L", -23.6208, -46.6580, -23.6317, -46.6505, 1435, 45, 2631)));

    public static final List<Airport> AIRPORTS = List.of(SBKP, SBGR, SBSP);

    /** Pista onde o avião começa quando nada é escolhido: SBKP 15. */
    public static final Runway HOME = SBKP.strips().get(0);

    private World() {
    }

    public static Optional<Airport> airport(String icao) {
        return AIRPORTS.stream().filter(a -> a.icao().equalsIgnoreCase(icao)).findFirst();
    }

    /** Ex.: runway("SBGR", "28R"). */
    public static Optional<Runway> runway(String icao, String designator) {
        return airport(icao).flatMap(a -> a.runway(designator));
    }

    /** Uma entrada por pista física (para desenhar). */
    public static List<Runway> strips() {
        List<Runway> all = new ArrayList<>();
        AIRPORTS.forEach(a -> all.addAll(a.strips()));
        return all;
    }

    /**
     * Altitude do terreno (ft) num ponto. Plano na elevação do aeroporto até {@link #FLAT_RADIUS_NM};
     * entre os aeroportos, uma média ponderada pelo inverso do quadrado da distância, que sobe e desce
     * suavemente (centenas de pés em dezenas de milhas).
     */
    public static double terrainFt(double xNm, double yNm) {
        double weighted = 0;
        double weights = 0;
        for (Airport a : AIRPORTS) {
            double[] c = a.center();
            double beyond = Math.hypot(xNm - c[0], yNm - c[1]) - FLAT_RADIUS_NM;
            if (beyond <= 0) {
                return a.elevationFt();
            }
            double w = 1 / (beyond * beyond);
            weighted += w * a.elevationFt();
            weights += w;
        }
        return weighted / weights;
    }

    /** O aeroporto mais perto de um ponto. */
    public static Airport nearest(double xNm, double yNm) {
        Airport best = SBKP;
        double bestDistance = Double.MAX_VALUE;
        for (Airport a : AIRPORTS) {
            double[] c = a.center();
            double d = Math.hypot(xNm - c[0], yNm - c[1]);
            if (d < bestDistance) {
                bestDistance = d;
                best = a;
            }
        }
        return best;
    }
}
