package br.a320sim.host.birds;

import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import br.a320sim.flightmodel.world.Airport;
import br.a320sim.flightmodel.world.World;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Bandos de aves (um por vez). O instrutor solta um à frente do avião ({@link #release}); com o modo
 * automático, eles aparecem ao acaso perto dos aeroportos, abaixo de 3000 ft. Atravessar o bando para
 * o motor do lado atingido; pelo meio, os dois. Os sorteios usam semente fixa (testes repetíveis).
 * <p>
 * O bando vai para o barramento (SIM.BIRDS*), para a vista pela janela desenhar, inclusive no PC do aluno.
 */
public class Birds {

    /** Raio do bando e a parte "do meio", que pega os dois motores (NM). */
    public static final double RADIUS_NM = 0.08;
    public static final double CENTER_NM = 0.025;
    /** Meia espessura do bando (bandos migratórios grandes): acima ou abaixo disso o avião passa (ft). */
    public static final double HALF_THICKNESS_FT = 150;
    public static final double SPEED_KT = 30;
    /** O bando solto pelo instrutor cruza o caminho do avião a esta distância à frente. */
    public static final double AHEAD_NM = 1.5;
    /** Modo automático: perto de que distância de um aeroporto e abaixo de que altura. */
    public static final double AUTO_RANGE_NM = 8;
    public static final double AUTO_MAX_HEIGHT_FT = 3000;
    /** Modo automático: em média um bando a cada este tanto de segundos nas condições acima. */
    public static final double AUTO_MEAN_SECONDS = 60;
    private static final double LIFETIME_SECONDS = 180;
    private static final double COOLDOWN_SECONDS = 30;

    /** Motores atingidos: 1, 2 ou os dois. */
    public record Strike(List<Integer> engines) {

        public String description() {
            return engines.size() == 2 ? "COLISAO COM AVES (MOTORES 1 E 2)"
                    : "COLISAO COM AVES (MOTOR " + engines.get(0) + ")";
        }
    }

    private final Random random;
    private BirdFlock flock;
    private double age;
    private double cooldown;
    private boolean auto;

    public Birds(long seed) {
        this.random = new Random(seed);
    }

    /** Começo de sessão: sem bando e com o modo automático desligado. */
    public void reset(DataBus bus) {
        flock = null;
        auto = false;
        cooldown = 0;
        publish(bus);
    }

    public Optional<BirdFlock> flock() {
        return Optional.ofNullable(flock);
    }

    public boolean isAuto() {
        return auto;
    }

    public void setAuto(boolean on, DataBus bus) {
        auto = on;
        publish(bus);
    }

    /** Põe um bando pronto (testes e cenários). */
    public void place(BirdFlock bando, DataBus bus) {
        flock = bando;
        age = 0;
        publish(bus);
    }

    /**
     * Solta um bando que cruza o caminho do avião {@link #AHEAD_NM} à frente, na altitude em que ele vai
     * passar. Um desvio sorteado decide onde ele é atingido (às vezes pelo meio).
     */
    public void release(DataBus bus) {
        double x = bus.getDouble(Labels.TRUE_POS_X, 0);
        double y = bus.getDouble(Labels.TRUE_POS_Y, 0);
        double heading = bus.getDouble(Labels.TRUE_HDG, 0);
        double gs = Math.max(60, bus.getDouble(Labels.TRUE_GS, 0));
        double hours = AHEAD_NM / gs;
        double h = Math.toRadians(heading);
        double aheadX = x + Math.sin(h) * AHEAD_NM;
        double aheadY = y + Math.cos(h) * AHEAD_NM;
        double alt = bus.getDouble(Labels.TRUE_ALT, 0) + bus.getDouble(Labels.TRUE_VS, 0) * hours * 60;
        // Migra atravessando o caminho, de um lado ou do outro; o desvio é onde ele estará quando o avião chegar
        double migration = heading + (random.nextBoolean() ? 90 : -90);
        double miss = (random.nextDouble() * 2 - 1) * RADIUS_NM * 0.9;
        double travel = SPEED_KT * hours - miss;
        double m = Math.toRadians(migration);
        place(new BirdFlock(aheadX - Math.sin(m) * travel, aheadY - Math.cos(m) * travel, alt, migration, SPEED_KT),
                bus);
    }

    /**
     * Um passo: move o bando, solta um ao acaso (modo automático) e confere a colisão.
     *
     * @return os motores atingidos, se o avião atravessou o bando neste passo
     */
    public Optional<Strike> update(DataBus bus, double dt) {
        cooldown = Math.max(0, cooldown - dt);
        if (flock != null) {
            flock = flock.moved(dt);
            age += dt;
            if (age > LIFETIME_SECONDS) {
                flock = null;
                cooldown = COOLDOWN_SECONDS;
            }
        } else if (auto && cooldown == 0 && inBirdCountry(bus) && random.nextDouble() < dt / AUTO_MEAN_SECONDS) {
            release(bus);
        }
        Optional<Strike> strike = flock == null ? Optional.empty() : strike(bus);
        if (strike.isPresent()) {
            flock = null; // o bando se espalha
            cooldown = COOLDOWN_SECONDS;
        }
        publish(bus);
        return strike;
    }

    /** No ar, abaixo de 3000 ft do chão e perto de algum aeroporto. */
    private static boolean inBirdCountry(DataBus bus) {
        if (bus.getBoolean(Labels.SIM_ON_GROUND) || bus.getDouble(Labels.SIM_RADIO_ALT, 0) > AUTO_MAX_HEIGHT_FT) {
            return false;
        }
        double x = bus.getDouble(Labels.TRUE_POS_X, 0);
        double y = bus.getDouble(Labels.TRUE_POS_Y, 0);
        for (Airport a : World.AIRPORTS) {
            double[] c = a.center();
            if (Math.hypot(x - c[0], y - c[1]) < AUTO_RANGE_NM) {
                return true;
            }
        }
        return false;
    }

    private Optional<Strike> strike(DataBus bus) {
        double dx = flock.xNm() - bus.getDouble(Labels.TRUE_POS_X, 0);
        double dy = flock.yNm() - bus.getDouble(Labels.TRUE_POS_Y, 0);
        double dz = flock.altFt() - bus.getDouble(Labels.TRUE_ALT, 0);
        if (Math.hypot(dx, dy) > RADIUS_NM || Math.abs(dz) > HALF_THICKNESS_FT) {
            return Optional.empty();
        }
        // Lado do bando em relação ao nariz: positivo = à direita (motor 2)
        double h = Math.toRadians(bus.getDouble(Labels.TRUE_HDG, 0));
        double across = dx * Math.cos(h) - dy * Math.sin(h);
        if (Math.abs(across) < CENTER_NM) {
            return Optional.of(new Strike(List.of(1, 2)));
        }
        return Optional.of(new Strike(List.of(across > 0 ? 2 : 1)));
    }

    private void publish(DataBus bus) {
        bus.publish(Labels.SIM_BIRDS_AUTO, auto);
        bus.publish(Labels.SIM_BIRDS, flock != null);
        if (flock != null) {
            bus.publish(Labels.SIM_BIRDS_X, flock.xNm());
            bus.publish(Labels.SIM_BIRDS_Y, flock.yNm());
            bus.publish(Labels.SIM_BIRDS_ALT, flock.altFt());
            bus.publish(Labels.SIM_BIRDS_HDG, flock.headingDeg());
        }
    }
}
