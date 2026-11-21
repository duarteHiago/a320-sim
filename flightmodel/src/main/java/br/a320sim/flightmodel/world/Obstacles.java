package br.a320sim.flightmodel.world;

import java.util.List;
import java.util.Optional;

/**
 * Prédios das cidades e árvores das plantações, gerados de forma determinística (o mesmo "sorteio" sempre):
 * a vista pela janela desenha a partir daqui e o modelo de voo testa a colisão com os mesmos objetos.
 * <p>
 * Tudo em metros, no mesmo referencial do {@link World} (origem na cabeceira 15 de SBKP).
 * Perto das pistas, a altura dos prédios é limitada por uma rampa de 2° antes de cada cabeceira
 * (como as zonas de proteção de aeródromo), para a aproximação ficar livre.
 */
public final class Obstacles {

    public enum Kind { BUILDING, TREE }

    /** Uma cidade: quarteirões quadrados em volta de um centro, mais altos no meio. */
    public record City(String name, double x, double y, double radius, double block, double street, double tallest) {

        public boolean contains(double px, double py, double margin) {
            return Math.hypot(px - x, py - y) < radius + margin;
        }
    }

    /** Prédio alinhado aos eixos do mundo, de (x0, y0) a (x1, y1), com altura em metros. */
    public record Building(double x0, double y0, double x1, double y1, double height) {

        boolean contains(double x, double y) {
            return x >= x0 && x <= x1 && y >= y0 && y <= y1;
        }
    }

    /** Árvore: tronco em (x, y); {@code variant} escolhe a cor da copa. */
    public record Tree(double x, double y, double height, double halfWidth, int variant) {
    }

    private static final double NM = Runway.METERS_PER_NM;

    /** Malha das árvores e tamanho das plantações (as cercas vivas seguem as divisas). */
    public static final double TREE_CELL = 55;
    public static final double FIELD = 600;
    public static final double ROAD_SPACING = 2400;
    public static final double TALLEST_TREE = 16;

    private static final double[] SBGR_CENTER = World.SBGR.center();
    private static final double[] SBSP_CENTER = World.SBSP.center();

    public static final List<City> CITIES = List.of(
            new City("Campinas", -9000, 7000, 4000, 160, 22, 90),
            // São Paulo: o centro fica ao norte de Congonhas, que fica dentro da cidade
            new City("Sao Paulo", SBSP_CENTER[0] * NM, SBSP_CENTER[1] * NM + 7000, 11000, 200, 26, 120),
            new City("Guarulhos", SBGR_CENTER[0] * NM - 4000, SBGR_CENTER[1] * NM - 3500, 3500, 180, 24, 50));

    /** Prédios abaixo disso não valem a pena (o limite da rampa cortou demais). */
    private static final double MIN_BUILDING = 6;
    /** Rampa de proteção antes de cada cabeceira: inclinação e alcance. */
    private static final double APPROACH_SLOPE = Math.tan(Math.toRadians(2));
    private static final double APPROACH_LENGTH = 6000;
    /** Faixa em volta das pistas onde não há prédio nenhum. */
    private static final double RUNWAY_CLEAR = 650;

    private Obstacles() {
    }

    /** A cidade que contém o ponto, se houver. */
    public static Optional<City> cityAt(double x, double y) {
        return CITIES.stream().filter(c -> c.contains(x, y, 0)).findFirst();
    }

    /** O prédio do quarteirão (bx, by) da cidade, se houver (praças e a área dos aeroportos não têm). */
    public static Optional<Building> building(City city, long bx, long by) {
        double x0 = bx * city.block() + city.street();
        double y0 = by * city.block() + city.street();
        double size = city.block() - city.street();
        double cx = x0 + size / 2;
        double cy = y0 + size / 2;
        double fromCenter = Math.hypot(cx - city.x(), cy - city.y());
        if (fromCenter > city.radius() - 80) {
            return Optional.empty();
        }
        int h = hash(bx, by);
        if (h % 10 == 0) {
            return Optional.empty(); // praça
        }
        double centrality = 1 - fromCenter / city.radius();
        double height = 8 + (h % 20) + centrality * centrality * (h % 90) * city.tallest() / 90;
        height = Math.min(height, heightLimit(cx, cy));
        if (height < MIN_BUILDING) {
            return Optional.empty();
        }
        double inset = 6 + (h % 25);
        return Optional.of(new Building(x0 + inset, y0 + inset, x0 + size - inset, y0 + size - inset, height));
    }

    /** A árvore da célula (cx, cy) da malha, se houver: muitas nas cercas vivas, poucas no meio das plantações. */
    public static Optional<Tree> tree(long cx, long cy) {
        int h = hash(cx * 31 + 7, cy * 17 + 3);
        double x = cx * TREE_CELL + (h % 37);
        double y = cy * TREE_CELL + ((h / 37) % 37);
        boolean hedge = Math.floorMod((long) x, (long) FIELD) < TREE_CELL
                || Math.floorMod((long) y, (long) FIELD) < TREE_CELL;
        if (h % 100 >= (hedge ? 70 : 6) || !isFarmland(x, y)) {
            return Optional.empty();
        }
        return Optional.of(new Tree(x, y, 7 + (h % 9), 2.5 + (h % 4), (h / 7) % 3));
    }

    /** Plantação: fora das cidades, dos aeroportos, das áreas de aproximação e das estradas. */
    public static boolean isFarmland(double x, double y) {
        for (Airport airport : World.AIRPORTS) {
            for (Runway r : airport.strips()) {
                double along = r.alongNm(x / NM, y / NM) * NM;
                double across = Math.abs(r.acrossNm(x / NM, y / NM) * NM);
                double length = r.lengthM();
                if (along > -400 && along < length + 400 && across < RUNWAY_CLEAR) {
                    return false;
                }
                boolean beforeStart = along < 0 && along > -1200;
                boolean afterEnd = along > length && along < length + 1200;
                if ((beforeStart || afterEnd) && across < 120) {
                    return false; // área livre das luzes de aproximação, nos dois sentidos
                }
            }
        }
        for (City city : CITIES) {
            if (city.contains(x, y, 100)) {
                return false;
            }
        }
        return Math.floorMod((long) x, (long) ROAD_SPACING) > 30 && Math.floorMod((long) y, (long) ROAD_SPACING) > 30;
    }

    /**
     * Altura máxima de um prédio num ponto: zero perto das pistas e, antes de cada cabeceira, uma rampa de 2°
     * que vai abrindo para os lados.
     */
    public static double heightLimit(double x, double y) {
        double limit = Double.MAX_VALUE;
        for (Airport airport : World.AIRPORTS) {
            for (Runway r : airport.runways()) {
                double along = r.alongNm(x / NM, y / NM) * NM;
                double across = Math.abs(r.acrossNm(x / NM, y / NM) * NM);
                if (along >= -200 && along <= r.lengthM() + 200 && across < RUNWAY_CLEAR) {
                    return 0;
                }
                double before = -along;
                if (before > 0 && before < APPROACH_LENGTH && across < 150 + before * 0.15) {
                    limit = Math.min(limit, Math.max(0, before * APPROACH_SLOPE - 5));
                }
            }
        }
        return limit;
    }

    /**
     * O avião (um ponto) bateu em algo? {@code heightAboveGround} em metros.
     *
     * @return o tipo de obstáculo atingido, ou vazio
     */
    public static Optional<Kind> collision(double x, double y, double heightAboveGround) {
        if (heightAboveGround < 0) {
            heightAboveGround = 0;
        }
        for (City city : CITIES) {
            if (heightAboveGround < 8 + 20 + city.tallest() && city.contains(x, y, 0)) {
                long bx = Math.floorDiv((long) Math.floor(x), (long) city.block());
                long by = Math.floorDiv((long) Math.floor(y), (long) city.block());
                Optional<Building> b = building(city, bx, by);
                if (b.isPresent() && b.get().contains(x, y) && heightAboveGround < b.get().height()) {
                    return Optional.of(Kind.BUILDING);
                }
            }
        }
        if (heightAboveGround < TALLEST_TREE) {
            long cx = Math.floorDiv((long) Math.floor(x), (long) TREE_CELL);
            long cy = Math.floorDiv((long) Math.floor(y), (long) TREE_CELL);
            for (long i = cx - 1; i <= cx + 1; i++) {
                for (long j = cy - 1; j <= cy + 1; j++) {
                    Optional<Tree> t = tree(i, j);
                    if (t.isPresent() && heightAboveGround < t.get().height()
                            && Math.hypot(t.get().x() - x, t.get().y() - y) < t.get().halfWidth() + 6) {
                        return Optional.of(Kind.TREE);
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** O mesmo "sorteio" em todo lugar: um número de 0 a 1.000.002 para cada par de inteiros. */
    public static int hash(long a, long b) {
        long h = a * 73_856_093L ^ b * 19_349_663L;
        h ^= h >>> 13;
        h *= 0x5bd1e995L;
        h ^= h >>> 15;
        return (int) Math.floorMod(h, 1_000_003L);
    }
}
