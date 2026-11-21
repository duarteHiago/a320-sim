package br.a320sim.flightmodel.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Um aeroporto: elevação, as pistas físicas ({@link #strips()}, um sentido de cada, para desenhar)
 * e todos os sentidos utilizáveis ({@link #runways()}).
 */
public record Airport(String icao, String name, double elevationFt, List<Runway> strips) {

    public Airport {
        strips = List.copyOf(strips);
    }

    /**
     * Pista física a partir das coordenadas das duas cabeceiras (lat/lon, graus decimais).
     * A proa sai das coordenadas; o comprimento é o publicado.
     */
    static Runway strip(String icao, String designator, double lat1, double lon1, double lat2, double lon2,
                        double lengthM, double widthM, double elevationFt) {
        double[] start = Geo.toWorld(lat1, lon1);
        double heading = Geo.wrap360(Geo.trueBearing(lat1, lon1, lat2, lon2) + Geo.ROTATION_DEG);
        return new Runway(icao, designator, start[0], start[1], heading, lengthM, widthM, elevationFt);
    }

    /** Os dois sentidos de cada pista: 10L, 28R, 10R, 28L... */
    public List<Runway> runways() {
        List<Runway> all = new ArrayList<>();
        for (Runway strip : strips) {
            all.add(strip);
            all.add(strip.reversed(reciprocal(strip.designator())));
        }
        return all;
    }

    public Optional<Runway> runway(String designator) {
        return runways().stream().filter(r -> r.designator().equalsIgnoreCase(designator)).findFirst();
    }

    public List<String> designators() {
        return runways().stream().map(Runway::designator).toList();
    }

    /** Centro do aeroporto: média dos meios das pistas, {x, y} em NM. */
    public double[] center() {
        double x = 0, y = 0;
        for (Runway r : strips) {
            x += (r.xNm() + r.endXNm()) / 2;
            y += (r.yNm() + r.endYNm()) / 2;
        }
        return new double[]{x / strips.size(), y / strips.size()};
    }

    /** 15 -> 33, 10L -> 28R, 17R -> 35L. */
    static String reciprocal(String designator) {
        int number = Integer.parseInt(designator.replaceAll("\\D", ""));
        String side = designator.replaceAll("\\d", "");
        int opposite = number > 18 ? number - 18 : number + 18;
        String mirrored = switch (side) {
            case "L" -> "R";
            case "R" -> "L";
            default -> side;
        };
        return String.format("%02d", opposite) + mirrored;
    }
}
