package br.a320sim.flightmodel;

import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import br.a320sim.flightmodel.world.Airport;
import br.a320sim.flightmodel.world.Obstacles;
import br.a320sim.flightmodel.world.Obstacles.Building;
import br.a320sim.flightmodel.world.Obstacles.City;
import br.a320sim.flightmodel.world.Obstacles.Tree;
import br.a320sim.flightmodel.world.Runway;
import br.a320sim.flightmodel.world.World;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObstaclesTest {

    private static final double NM = Runway.METERS_PER_NM;

    private static City city(String name) {
        return Obstacles.CITIES.stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }

    /** O prédio mais alto de uma cidade (procura em toda a malha). */
    private static Building tallest(City city) {
        Building best = null;
        long min = Math.floorDiv((long) (-city.radius()), (long) city.block());
        long max = Math.floorDiv((long) city.radius(), (long) city.block());
        long baseX = Math.floorDiv((long) city.x(), (long) city.block());
        long baseY = Math.floorDiv((long) city.y(), (long) city.block());
        for (long bx = baseX + min; bx <= baseX + max; bx++) {
            for (long by = baseY + min; by <= baseY + max; by++) {
                Optional<Building> b = Obstacles.building(city, bx, by);
                if (b.isPresent() && (best == null || b.get().height() > best.height())) {
                    best = b.get();
                }
            }
        }
        return best;
    }

    /** Uma árvore qualquer da malha, perto de um ponto de plantação. */
    private static Tree someTree(double x, double y) {
        long cx = Math.floorDiv((long) x, (long) Obstacles.TREE_CELL);
        long cy = Math.floorDiv((long) y, (long) Obstacles.TREE_CELL);
        for (long i = cx; i < cx + 200; i++) {
            Optional<Tree> t = Obstacles.tree(i, cy);
            if (t.isPresent()) {
                return t.get();
            }
        }
        throw new AssertionError("nenhuma arvore perto de " + x + ", " + y);
    }

    @Test
    void saoPauloTemPrediosAltosEGuarulhosMaisBaixos() {
        assertTrue(tallest(city("Sao Paulo")).height() > 80);
        assertTrue(tallest(city("Campinas")).height() > 60);
        assertTrue(tallest(city("Guarulhos")).height() <= 8 + 20 + 50);
        assertTrue(city("Sao Paulo").contains(World.SBSP.center()[0] * NM, World.SBSP.center()[1] * NM, 0),
                "Congonhas fica dentro da cidade");
    }

    @Test
    void naoHaPredioNemArvoreNasPistas() {
        for (Airport airport : World.AIRPORTS) {
            for (Runway r : airport.strips()) {
                for (double a = 0; a <= r.lengthM(); a += 50) {
                    double x = (r.xNm() + Math.sin(Math.toRadians(r.headingDeg())) * a / NM) * NM;
                    double y = (r.yNm() + Math.cos(Math.toRadians(r.headingDeg())) * a / NM) * NM;
                    assertEquals(0, Obstacles.heightLimit(x, y), r.id());
                    assertTrue(Obstacles.collision(x, y, 0).isEmpty(), r.id() + " a " + a + " m");
                }
            }
        }
    }

    @Test
    void rampaDe3GrausDeTodasAsCabeceirasEstaLivre() {
        for (Airport airport : World.AIRPORTS) {
            for (Runway r : airport.runways()) {
                for (double d = 0; d <= 6000; d += 10) {
                    double[] p = r.pointBefore(d / NM);
                    double height = 15 + d * Math.tan(Math.toRadians(3));
                    assertTrue(Obstacles.collision(p[0] * NM, p[1] * NM, height).isEmpty(),
                            r.id() + ": bateu a " + d + " m da cabeceira");
                }
            }
        }
    }

    @Test
    void bateNoPredioAbaixoDoTelhadoENaoPorCima() {
        Building b = tallest(city("Sao Paulo"));
        double x = (b.x0() + b.x1()) / 2;
        double y = (b.y0() + b.y1()) / 2;
        assertEquals(Optional.of(Obstacles.Kind.BUILDING), Obstacles.collision(x, y, b.height() - 1));
        assertTrue(Obstacles.collision(x, y, b.height() + 1).isEmpty());
    }

    @Test
    void bateNaArvoreSoRenteAoChao() {
        Tree t = someTree(-20_000, -20_000);
        assertEquals(Optional.of(Obstacles.Kind.TREE), Obstacles.collision(t.x(), t.y(), t.height() - 1));
        assertTrue(Obstacles.collision(t.x(), t.y(), t.height() + 1).isEmpty());
        assertTrue(Obstacles.collision(t.x(), t.y(), 100).isEmpty());
    }

    @Test
    void arvoresSoNasPlantacoes() {
        Runway home = World.HOME;
        assertFalse(Obstacles.isFarmland(home.xNm() * NM, home.yNm() * NM), "pista");
        City sp = city("Sao Paulo");
        assertFalse(Obstacles.isFarmland(sp.x(), sp.y()), "cidade");
        assertTrue(Obstacles.isFarmland(-20_000 + 300, -20_000 + 300), "campo");
    }

    // --- no modelo de voo ---

    @Test
    void voarBaixoContraUmPredioDaCrashComOQueFoiAtingido() {
        Building b = tallest(city("Sao Paulo"));
        double x = (b.x0() + b.x1()) / 2;
        double y = (b.y0() + b.y1()) / 2;
        double terrain = World.terrainFt(x / NM, y / NM);
        DataBus bus = new DataBus();
        FlightModel model = new FlightModel(bus);
        // 300 m antes do prédio, voando para leste em direção a ele, abaixo do telhado
        model.resetAirborne(terrain + b.height() / 2 / 0.3048, 200, 90, (x - 300) / NM, y / NM);
        bus.publish(Labels.ENG1_N1, 62.0);
        bus.publish(Labels.ENG2_N1, 62.0);
        for (int i = 0; i < 60 * 5 && !model.isCrashed(); i++) {
            model.step(1.0 / 60);
        }
        assertTrue(model.isCrashed());
        assertEquals("PREDIO", bus.getString(Labels.SIM_COLLISION, ""));
        assertTrue(model.isOnGround(), "cai ali mesmo");

        model.resetOnRunway();
        assertEquals("", bus.getString(Labels.SIM_COLLISION, "x"), "reset limpa a colisao");
        assertFalse(model.isCrashed());
    }

    /** Prédios de Congonhas cujo centro fica ao lado da 17R, entre {@code min} e {@code max} metros do eixo. */
    private static java.util.List<Building> besideCgh(double min, double max) {
        Runway r = World.runway("SBSP", "17R").orElseThrow();
        City sp = city("Sao Paulo");
        java.util.List<Building> found = new java.util.ArrayList<>();
        double[] c = World.SBSP.center();
        long cx = Math.floorDiv((long) (c[0] * NM), (long) sp.block());
        long cy = Math.floorDiv((long) (c[1] * NM), (long) sp.block());
        for (long bx = cx - 10; bx <= cx + 10; bx++) {
            for (long by = cy - 10; by <= cy + 10; by++) {
                Obstacles.building(sp, bx, by).ifPresent(b -> {
                    double x = (b.x0() + b.x1()) / 2 / NM;
                    double y = (b.y0() + b.y1()) / 2 / NM;
                    double along = r.alongNm(x, y) * NM;
                    double across = Math.abs(r.acrossNm(x, y) * NM);
                    if (along > 0 && along < r.lengthM() && across >= min && across <= max) {
                        found.add(b);
                    }
                });
            }
        }
        return found;
    }

    @Test
    void congonhasTemCasasPertoDaPistaComoNaCidadeDeVerdade() {
        assertTrue(besideCgh(250, 600).size() >= 5, "a cidade chega perto da cerca");
        assertTrue(besideCgh(0, 250).isEmpty(), "mas nao em cima da pista");
    }

    @Test
    void pertoDaPistaSoCasasBaixasSubindoAosPoucos() {
        for (Building b : besideCgh(250, 450)) {
            assertTrue(b.height() <= 8 + 200 / 7.0 + 1, "superficie de transicao: " + b.height());
        }
    }
}
