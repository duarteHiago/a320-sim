package br.a320sim.cockpit.visual;

import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import br.a320sim.flightmodel.world.Runway;
import br.a320sim.flightmodel.world.World;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;

import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Sistema visual: a vista pelo para-brisa, no estilo "Mode 7" dos consoles 16-bit, com alguns extras 3D.
 * <ol>
 *   <li>Para cada pixel lança um raio do olho do piloto: céu com serra no horizonte, camada de nuvens,
 *       ou o chão (pista, aeroporto, plantações com fileiras, cidade), com névoa pela distância.</li>
 *   <li>Por cima, objetos com altura (árvores, prédios, terminal, torre), com z-buffer.</li>
 *   <li>Por fim, as luzes: borda de pista, cabeceira, aproximação com "coelho" sequencial e PAPI.</li>
 * </ol>
 * As pistas vêm do {@link World} (SBKP, SBGR, SBSP). Cada uma ganha taxiway paralela e, nas principais,
 * pátio, terminal e torre; luzes de aproximação e PAPI nos dois sentidos.
 * Lê a verdade física (TRUE.*), não os sensores: lá fora continua visível mesmo com pane nos instrumentos.
 */
public final class OutsideView implements Disposable {

    public static final int WIDTH = 320;
    public static final int HEIGHT = 104;
    /** Distância focal em pixels para um campo de visão horizontal de 60°. */
    public static final double FOCAL = (WIDTH / 2.0) / Math.tan(Math.toRadians(30));
    /** Linha (a partir do topo) para onde aponta o nariz: o piloto olha um pouco para baixo, por cima do painel. */
    public static final double BORESIGHT_ROW = HEIGHT * 0.32;

    private static final double NM = 1852;
    private static final double FT = 0.3048;
    private static final double EYE_HEIGHT = 5;
    private static final double HAZE_START = 8_000;
    private static final double HAZE_FULL = 60_000;
    private static final float FAR = Float.MAX_VALUE;

    // Em volta de cada pista, em coordenadas da pista: "along" ao longo do eixo a partir da cabeceira,
    // "across" de lado, positivo para o lado da taxiway (que muda de pista para pista)
    private static final double TAXIWAY_ACROSS = 150;
    private static final double TAXIWAY_HALF_WIDTH = 11.5;
    private static final double CONNECTOR_SPACING = 750;
    private static final double APRON_HALF_LENGTH = 450;
    private static final double APRON_ACROSS_MIN = 175;
    private static final double APRON_ACROSS_MAX = 330;
    /** Área do aeroporto em volta da pista: grama além do asfalto. */
    private static final double AREA_BEHIND = 260;
    private static final double AREA_FRONT = 420;
    private static final double AREA_ENDS = 150;
    private static final double PAPI_ALONG = 420;
    /** Pistas com pátio e terminal; as outras só têm a taxiway (do lado de fora das paralelas). */
    private static final Set<String> MAIN_RUNWAYS = Set.of("SBKP 15", "SBGR 10R", "SBSP 17R");
    /** Pistas cuja taxiway fica à esquerda, para não cair entre as paralelas. */
    private static final Set<String> TAXIWAY_ON_LEFT = Set.of("SBGR 10L", "SBSP 17L");
    private static final double[] PAPI_ANGLES = {3.5, 3.17, 2.83, 2.5}; // do mais perto da pista para fora

    /**
     * Uma pista física pronta para desenhar, em metros. {@code side} = +1 se a taxiway fica à direita
     * (olhando da cabeceira), -1 se à esquerda: "across" já vem multiplicado por ele.
     */
    private record Strip(String id, double x0, double y0, double sin, double cos, double length, double halfWidth,
                         int side, boolean main, double[] connectors) {

        static Strip of(Runway r) {
            double h = Math.toRadians(r.headingDeg());
            double length = r.lengthM();
            int count = Math.max(2, (int) Math.round((length - 120) / CONNECTOR_SPACING) + 1);
            double[] connectors = new double[count];
            for (int i = 0; i < count; i++) {
                connectors[i] = 60 + (length - 120) * i / (count - 1);
            }
            return new Strip(r.id(), r.xNm() * NM, r.yNm() * NM, Math.sin(h), Math.cos(h), length, r.widthM() / 2,
                    TAXIWAY_ON_LEFT.contains(r.id()) ? -1 : 1, MAIN_RUNWAYS.contains(r.id()), connectors);
        }

        double along(double x, double y) {
            return (x - x0) * sin + (y - y0) * cos;
        }

        double across(double x, double y) {
            return ((x - x0) * cos - (y - y0) * sin) * side;
        }

        double worldX(double along, double across) {
            return x0 + along * sin + across * side * cos;
        }

        double worldY(double along, double across) {
            return y0 + along * cos - across * side * sin;
        }

        /** Dentro da área do aeroporto desta pista (grama, taxiway, pátio). */
        boolean inArea(double along, double across) {
            return along > -AREA_ENDS && along < length + AREA_ENDS && across > -AREA_BEHIND && across < AREA_FRONT;
        }

        double apronMin() {
            return length / 2 - APRON_HALF_LENGTH;
        }

        double apronMax() {
            return length / 2 + APRON_HALF_LENGTH;
        }
    }

    private static final List<Strip> STRIPS = World.strips().stream().map(Strip::of).toList();
    /** Para não fazer conta de pista longe de qualquer aeroporto: raio de cada um, em metros. */
    private static final double AIRPORT_REACH = 4_500;

    private static final double CITY_X = -9000;
    private static final double CITY_Y = 7000;
    private static final double CITY_RADIUS = 4000;
    private static final double BLOCK = 160;
    private static final double STREET = 22;
    private static final double FIELD = 600;
    private static final double ROAD_SPACING = 2400;

    // Nuvens: camada de cúmulos esparsos, uns 4900 ft acima do aeródromo, levada pelo vento
    private static final double CLOUD_ALT = 1500;
    private static final double CLOUD_FAR = 45_000;
    private static final double CLOUD_SCALE = 2200;
    private static final double CLOUD_THICKNESS = 120;
    private static final double WIND_X = 7;
    private static final double WIND_Y = 4;
    private static final double SUN_DX = 0.45;
    private static final double SUN_DY = 0.30;

    private static final int SKY_TOP = 0x2a58b8ff;
    private static final int HAZE = 0xb4cce4ff;
    private static final int MOUNTAIN = 0x6f84a8ff;
    private static final int MOUNTAIN_FAR = 0x93a6c4ff;
    private static final int CLOUD_TOP = 0xf6f8fcff;
    private static final int CLOUD_EDGE = 0xdfe4ecff;
    private static final int CLOUD_BASE = 0xaeb4c0ff;
    private static final int FOG = 0xe6eaf0ff;
    private static final int ASPHALT = 0x45494eff;
    private static final int RUBBER = 0x34373bff;
    private static final int TAXIWAY = 0x55585cff;
    private static final int TAXI_LINE = 0xe8c838ff;
    private static final int CONCRETE = 0x8e8e8aff;
    private static final int PAINT = 0xeeeeeeff;
    private static final int AIRPORT_GRASS = 0x6cb44aff;
    private static final int ROAD = 0x7a7a72ff;
    private static final int STREET_COLOR = 0x4a4a52ff;
    private static final int[] FIELDS = {0x4c9a2aff, 0x5aa832ff, 0x3f8a24ff, 0xb0a448ff, 0x8a6a3aff, 0x66b03aff};
    private static final int[] BUILDINGS = {0x9a9aa2ff, 0x8a8a90ff, 0xb0aaa0ff, 0x7c8088ff, 0xa89a88ff};
    private static final int[] TREES = {0x2f6a24ff, 0x3a7a2aff, 0x285c20ff};
    private static final int EDGE_LIGHT = 0xfff4d0ff;
    private static final int APPROACH_LIGHT = 0xfff0b0ff;
    private static final int STROBE = 0xffffffff;
    private static final int GREEN_LIGHT = 0x40ff60ff;
    private static final int RED_LIGHT = 0xff3030ff;

    private final Pixmap pixmap = new Pixmap(WIDTH, HEIGHT, Pixmap.Format.RGBA8888);
    private final Texture texture = new Texture(pixmap);
    private final int[] pixels = new int[WIDTH * HEIGHT];
    private final float[] depth = new float[WIDTH * HEIGHT];

    // Câmera do quadro atual
    private double camX, camY, camZ;
    private double fx, fy, fz, rx, ry, rz, ux, uy, uz;
    private double r0x, r0y;
    private double windX, windY;

    // Tremida da cabine: vibração na corrida e solavanco no toque
    private final java.util.Random random = new java.util.Random(7);
    private long lastNanos;
    private boolean wasOnGround = true;
    private double bumpAmplitude;
    private double bumpTime;

    public OutsideView() {
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
    }

    public Texture render(DataBus bus) {
        double delta = frameDelta(bus);
        camX = bus.getDouble(Labels.TRUE_POS_X, 0) * NM;
        camY = bus.getDouble(Labels.TRUE_POS_Y, 0) * NM;
        camZ = bus.getDouble(Labels.SIM_RADIO_ALT, 0) * FT + EYE_HEIGHT;
        double simTime = bus.getDouble(Labels.SIM_TIME, 0);
        windX = simTime * WIND_X;
        windY = simTime * WIND_Y;

        double hdg = Math.toRadians(bus.getDouble(Labels.TRUE_HDG, 0));
        double pitch = Math.toRadians(bus.getDouble(Labels.TRUE_PITCH, 0) + shake(bus, delta));
        double bank = Math.toRadians(bus.getDouble(Labels.TRUE_ROLL, 0));
        setupCamera(hdg, pitch, bank);

        Arrays.fill(depth, FAR);
        renderPlanes();
        if (camZ < 1200) {
            trees();
        }
        if (camZ < 4000) {
            airportBuildings();
            city();
        }
        lights(simTime);
        cloudFog();

        IntBuffer buffer = pixmap.getPixels().duplicate().order(ByteOrder.BIG_ENDIAN).asIntBuffer();
        buffer.put(pixels);
        texture.draw(pixmap, 0, 0);
        return texture;
    }

    // ------------------------------------------------------------------ câmera

    private void setupCamera(double hdg, double pitch, double bank) {
        // Eixos da câmera no mundo (x = leste, y = norte, z = para cima)
        double sh = Math.sin(hdg), ch = Math.cos(hdg), sp = Math.sin(pitch), cp = Math.cos(pitch);
        double sb = Math.sin(bank), cb = Math.cos(bank);
        fx = sh * cp;
        fy = ch * cp;
        fz = sp;
        r0x = ch;
        r0y = -sh;
        double u0x = -sh * sp, u0y = -ch * sp, u0z = cp;
        rx = r0x * cb - u0x * sb;
        ry = r0y * cb - u0y * sb;
        rz = -u0z * sb;
        ux = u0x * cb + r0x * sb;
        uy = u0y * cb + r0y * sb;
        uz = u0z * cb;
    }

    /** Projeta um ponto do mundo na tela: {coluna, linha, distância}, ou null se está atrás do olho. */
    private final double[] projected = new double[3];

    private boolean project(double x, double y, double z) {
        double dx = x - camX, dy = y - camY, dz = z - camZ;
        double forward = dx * fx + dy * fy + dz * fz;
        if (forward < 1) {
            return false;
        }
        projected[0] = WIDTH / 2.0 + (dx * rx + dy * ry + dz * rz) / forward * FOCAL;
        projected[1] = BORESIGHT_ROW - (dx * ux + dy * uy + dz * uz) / forward * FOCAL;
        projected[2] = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return true;
    }

    private double frameDelta(DataBus bus) {
        long now = System.nanoTime();
        double delta = lastNanos == 0 ? 0 : Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        return bus.getBoolean(Labels.SIM_FREEZE) ? 0 : delta;
    }

    /** Graus de arfagem extra: trepidação proporcional à velocidade no chão e um solavanco amortecido no toque. */
    private double shake(DataBus bus, double delta) {
        boolean onGround = bus.getBoolean(Labels.SIM_ON_GROUND);
        double gs = bus.getDouble(Labels.TRUE_GS, 0);
        if (onGround && !wasOnGround) {
            double sink = Math.abs(bus.getDouble(Labels.TRUE_VS, 0));
            bumpAmplitude = Math.max(0.25, Math.min(1.5, sink / 400));
            bumpTime = 0;
        }
        wasOnGround = onGround;
        bumpTime += delta;
        bumpAmplitude *= Math.exp(-delta * 5);
        double bump = bumpAmplitude * Math.sin(bumpTime * 28);
        double rumble = onGround && delta > 0 ? (random.nextDouble() - 0.5) * 0.18 * Math.min(1, gs / 120) : 0;
        return bump + rumble;
    }

    // ------------------------------------------------------------------ céu, nuvens e chão

    private void renderPlanes() {
        boolean belowClouds = camZ < CLOUD_ALT;
        for (int row = 0; row < HEIGHT; row++) {
            double v = BORESIGHT_ROW - row - 0.5;
            for (int col = 0; col < WIDTH; col++) {
                double u = col - WIDTH / 2.0 + 0.5;
                double dx = fx * FOCAL + rx * u + ux * v;
                double dy = fy * FOCAL + ry * u + uy * v;
                double dz = fz * FOCAL + rz * u + uz * v;
                double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                int index = row * WIDTH + col;
                int color;
                if (dz >= 0) {
                    color = sky(dx, dy, dz, length);
                    if (belowClouds && dz > 0) {
                        double t = (CLOUD_ALT - camZ) / dz;
                        color = cloudLayer(color, camX + dx * t, camY + dy * t, t * length, false);
                    }
                } else {
                    double t = camZ / -dz;
                    double distance = t * length;
                    double gx = camX + dx * t, gy = camY + dy * t;
                    if (distance > HAZE_FULL) {
                        color = HAZE;
                    } else {
                        // Tamanho do pixel no chão: decide quanto detalhe dá para mostrar sem cintilar
                        double grazing = Math.max(0.03, -dz / length);
                        double footprint = distance / FOCAL / grazing;
                        color = ground(gx, gy, footprint);
                        if (distance < 20_000) {
                            color = shade(color, 1 - 0.3 * cloudCover(gx + CLOUD_ALT * SUN_DX, gy + CLOUD_ALT * SUN_DY));
                        }
                        color = mix(color, HAZE, clamp((distance - HAZE_START) / (HAZE_FULL - HAZE_START)));
                        depth[index] = (float) distance;
                    }
                    if (!belowClouds) {
                        double tc = (camZ - CLOUD_ALT) / -dz;
                        int withClouds = cloudLayer(color, camX + dx * tc, camY + dy * tc, tc * length, true);
                        if (withClouds != color) {
                            color = withClouds;
                            depth[index] = (float) Math.min(depth[index], tc * length);
                        }
                    }
                }
                pixels[index] = color;
            }
        }
    }

    /** Degradê do céu e, perto do horizonte, uma serra distante que só gira com a proa: referência de direção. */
    private static int sky(double dx, double dy, double dz, double length) {
        double elevation = dz / length;
        double azimuth = Math.atan2(dx, dy);
        double ridge = mountainHeight(azimuth);
        if (elevation < ridge) {
            // Duas camadas: a serra mais distante e mais clara atrás, a mais próxima e escura na frente
            double near = mountainHeight(azimuth * 2 + 2.1) * 0.55;
            return elevation < near ? MOUNTAIN : MOUNTAIN_FAR;
        }
        return mix(HAZE, SKY_TOP, Math.min(1, elevation * 3));
    }

    /** Altura angular da serra (em seno do ângulo), uma função contínua da direção. */
    private static double mountainHeight(double azimuth) {
        double h = 0.55 + 0.45 * Math.sin(azimuth * 3 + 1)
                + 0.30 * Math.sin(azimuth * 7 + 2)
                + 0.18 * Math.sin(azimuth * 17 + 0.5)
                + 0.08 * Math.sin(azimuth * 41 + 4);
        double range = 0.5 + 0.5 * Math.sin(azimuth + 0.8); // mais alta para um lado, quase plana do outro
        return Math.max(0, h) * range * 0.022;
    }

    /** Mistura a nuvem (se houver) sobre a cor de fundo. {@code fromAbove}: vista de cima, topo branco. */
    private int cloudLayer(int background, double x, double y, double distance, boolean fromAbove) {
        if (distance > CLOUD_FAR) {
            return background;
        }
        double cover = cloudCover(x, y);
        if (cover <= 0) {
            return background;
        }
        int cloud = fromAbove ? mix(CLOUD_EDGE, CLOUD_TOP, cover) : mix(CLOUD_EDGE, CLOUD_BASE, cover);
        cloud = mix(cloud, HAZE, clamp(distance / CLOUD_FAR) * 0.7);
        double alpha = Math.min(1, cover * 1.6) * (1 - clamp((distance - CLOUD_FAR * 0.6) / (CLOUD_FAR * 0.4)));
        return mix(background, cloud, alpha);
    }

    /** 0 = céu limpo, 1 = miolo da nuvem. */
    private double cloudCover(double x, double y) {
        double n = fbm((x - windX) / CLOUD_SCALE, (y - windY) / CLOUD_SCALE);
        return clamp((n - 0.56) / 0.14);
    }

    /** Dentro da camada de nuvens a vista inteira some no branco. */
    private void cloudFog() {
        double gap = Math.abs(camZ - CLOUD_ALT);
        if (gap >= CLOUD_THICKNESS) {
            return;
        }
        double amount = cloudCover(camX, camY) * (1 - gap / CLOUD_THICKNESS) * 1.4;
        if (amount <= 0) {
            return;
        }
        amount = Math.min(0.97, amount);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = mix(pixels[i], FOG, amount);
        }
    }

    private static int ground(double x, double y, double footprint) {
        int airport = airport(x, y, footprint);
        if (airport != 0) {
            return airport;
        }
        if (Math.hypot(x - CITY_X, y - CITY_Y) < CITY_RADIUS) {
            if (Math.floorMod((long) x, (long) BLOCK) < STREET || Math.floorMod((long) y, (long) BLOCK) < STREET) {
                return STREET_COLOR;
            }
            return BUILDINGS[hash(Math.floorDiv((long) x, (long) BLOCK), Math.floorDiv((long) y, (long) BLOCK))
                    % BUILDINGS.length];
        }
        if (Math.floorMod((long) x, (long) ROAD_SPACING) < 14 || Math.floorMod((long) y, (long) ROAD_SPACING) < 14) {
            return ROAD;
        }
        return farmland(x, y, footprint);
    }

    /**
     * Plantações em três escalas. Cada detalhe só aparece quando o pixel é pequeno o bastante para mostrá-lo:
     * de longe, manchas de 600 m; mais perto, variação de 60 m; rente ao chão, fileiras de plantio e
     * granulado de poucos metros, que passam correndo e dão a sensação de velocidade.
     */
    private static int farmland(double x, double y, double footprint) {
        long fieldX = Math.floorDiv((long) x, (long) FIELD);
        long fieldY = Math.floorDiv((long) y, (long) FIELD);
        int field = hash(fieldX, fieldY);
        int color = FIELDS[field % FIELDS.length];
        if (footprint < 30) {
            color = shade(color, 0.95 + 0.1 * (hash(Math.floorDiv((long) x, 60), Math.floorDiv((long) y, 60)) % 100) / 100.0);
        }
        if (footprint < 2.5 && (field & 8) == 0) {
            double rows = (field & 16) == 0 ? x : y;
            if (Math.floorMod((long) Math.floor(rows / 3), 2) == 0) {
                color = shade(color, 0.86);
            }
        }
        if (footprint < 6) {
            color = shade(color, 0.92 + 0.14 * (hash(Math.floorDiv((long) x, 4), Math.floorDiv((long) y, 4)) % 100) / 100.0);
        }
        return color;
    }

    /**
     * Chão de aeroporto num ponto, ou 0 se não é aeroporto. Primeiro o asfalto de qualquer pista
     * (paralelas ficam perto uma da outra), depois taxiway e pátio, e por fim a grama.
     */
    private static int airport(double x, double y, double footprint) {
        boolean near = false;
        for (Strip s : STRIPS) {
            double dx = x - (s.x0 + s.sin * s.length / 2);
            double dy = y - (s.y0 + s.cos * s.length / 2);
            if (dx * dx + dy * dy < AIRPORT_REACH * AIRPORT_REACH) {
                near = true;
                double along = s.along(x, y);
                double side = Math.abs(s.across(x, y));
                if (along >= 0 && along <= s.length && side <= s.halfWidth) {
                    return runway(s, along, side, footprint);
                }
            }
        }
        if (!near) {
            return 0;
        }
        boolean grass = false;
        double grassAlong = 0;
        double grassAcross = 0;
        for (Strip s : STRIPS) {
            double along = s.along(x, y);
            double across = s.across(x, y);
            if (!s.inArea(along, across)) {
                continue;
            }
            int surface = paved(s, along, across, footprint);
            if (surface != 0) {
                return surface;
            }
            grass = true;
            grassAlong = along;
            grassAcross = across;
        }
        if (!grass) {
            return 0;
        }
        int color = AIRPORT_GRASS;
        if (footprint < 6) {
            color = shade(color, 0.93 + 0.12 * (hash((long) Math.floor(grassAlong / 5),
                    (long) Math.floor(grassAcross / 5)) % 100) / 100.0);
        }
        return color;
    }

    /** Taxiway, ligações com a pista e pátio desta pista; 0 se o ponto não é pavimento. */
    private static int paved(Strip s, double along, double across, double footprint) {
        boolean taxiway = Math.abs(across - TAXIWAY_ACROSS) <= TAXIWAY_HALF_WIDTH && along > 30 && along < s.length - 30;
        double nearest = s.connectors[0];
        for (double c : s.connectors) {
            if (Math.abs(along - c) < Math.abs(along - nearest)) {
                nearest = c;
            }
        }
        boolean connector = Math.abs(along - nearest) <= TAXIWAY_HALF_WIDTH && across > s.halfWidth
                && across < TAXIWAY_ACROSS;
        if (taxiway || connector) {
            boolean centerline = taxiway ? Math.abs(across - TAXIWAY_ACROSS) < 0.6 : Math.abs(along - nearest) < 0.6;
            return centerline && footprint < 4 ? TAXI_LINE : TAXIWAY;
        }
        if (s.main && along > s.apronMin() && along < s.apronMax() && across > APRON_ACROSS_MIN
                && across < APRON_ACROSS_MAX) {
            boolean joint = footprint < 3 && (Math.floorMod((long) along, 25) < 1 || Math.floorMod((long) across, 25) < 1);
            return joint ? shade(CONCRETE, 0.85) : CONCRETE;
        }
        return 0;
    }

    /** Marcações da pista: bordas, faixas de cabeceira, zona de toque, ponto de visada, linha central e marcas de pneu. */
    private static int runway(Strip s, double along, double across, double footprint) {
        double fromEnd = Math.min(along, s.length - along);
        boolean edge = across > s.halfWidth - 1.2;
        boolean threshold = fromEnd < 45 && across < s.halfWidth - 3 && Math.floorMod((long) (across * 10), 36) < 18;
        boolean aimingPoint = fromEnd > 400 && fromEnd < 460 && across > 5 && across < 10;
        boolean touchdown = fromEnd > 150 && fromEnd < 900 && Math.floorMod((long) fromEnd, 150) < 22
                && across > 3.5 && across < 3.5 + 1.8 * (1 + (fromEnd < 300 ? 2 : fromEnd < 600 ? 1 : 0));
        boolean centerline = across < 0.9 && fromEnd > 80 && Math.floorMod((long) along, 50) < 30;
        if (edge || threshold || aimingPoint || touchdown || centerline) {
            return PAINT;
        }
        // Borracha dos pneus escurece a zona de toque, mais no meio
        if (fromEnd > 250 && fromEnd < 1000 && across < 9) {
            double wear = (1 - across / 9) * (1 - Math.abs(fromEnd - 600) / 400);
            if (footprint < 4) {
                wear *= 0.6 + 0.8 * (hash((long) Math.floor(along / 3), (long) Math.floor(across)) % 100) / 100.0;
            }
            return mix(ASPHALT, RUBBER, clamp(wear));
        }
        return ASPHALT;
    }

    // ------------------------------------------------------------------ objetos com altura

    /** Árvores perto do avião: muitas nas divisas das plantações (cercas vivas), algumas soltas no meio. */
    private void trees() {
        double radius = 1800;
        double cell = 55;
        long minX = Math.floorDiv((long) (camX - radius), (long) cell);
        long maxX = Math.floorDiv((long) (camX + radius), (long) cell);
        long minY = Math.floorDiv((long) (camY - radius), (long) cell);
        long maxY = Math.floorDiv((long) (camY + radius), (long) cell);
        for (long cx = minX; cx <= maxX; cx++) {
            for (long cy = minY; cy <= maxY; cy++) {
                int h = hash(cx * 31 + 7, cy * 17 + 3);
                double x = cx * cell + (h % 37);
                double y = cy * cell + ((h / 37) % 37);
                // Frente da câmera apenas (barato descartar antes de qualquer outra conta)
                if ((x - camX) * fx + (y - camY) * fy < 0) {
                    continue;
                }
                boolean hedge = Math.floorMod((long) x, (long) FIELD) < cell || Math.floorMod((long) y, (long) FIELD) < cell;
                if (h % 100 >= (hedge ? 70 : 6) || !isFarmland(x, y)) {
                    continue;
                }
                double height = 7 + (h % 9);
                double halfWidth = 2.5 + (h % 4);
                billboard(x, y, 0, height, halfWidth, TREES[(h / 7) % TREES.length]);
            }
        }
    }

    private static boolean isFarmland(double x, double y) {
        for (Strip s : STRIPS) {
            double along = s.along(x, y);
            double across = s.across(x, y);
            if (along > -400 && along < s.length + 400 && across > -AREA_BEHIND - 130 && across < AREA_FRONT + 130) {
                return false;
            }
            boolean beforeStart = along < 0 && along > -1200;
            boolean afterEnd = along > s.length && along < s.length + 1200;
            if ((beforeStart || afterEnd) && Math.abs(across) < 120) {
                return false; // área livre das luzes de aproximação, nos dois sentidos
            }
        }
        if (Math.hypot(x - CITY_X, y - CITY_Y) < CITY_RADIUS + 100) {
            return false;
        }
        return Math.floorMod((long) x, (long) ROAD_SPACING) > 30 && Math.floorMod((long) y, (long) ROAD_SPACING) > 30;
    }

    /** Terminal, hangar e torre ao lado do pátio de cada pista principal (só os que estão perto). */
    private void airportBuildings() {
        for (Strip s : STRIPS) {
            if (!s.main || Math.hypot(s.x0 - camX, s.y0 - camY) > 12_000) {
                continue;
            }
            double min = s.apronMin();
            double max = s.apronMax();
            box(s, min + 80, APRON_ACROSS_MAX + 15, max - 80, APRON_ACROSS_MAX + 55, 16, 0xb8b4aaff, 0);
            box(s, max + 60, APRON_ACROSS_MIN + 20, max + 160, APRON_ACROSS_MIN + 90, 22, 0x9aa2acff, 0);
            double towerAlong = min - 60;
            double towerAcross = APRON_ACROSS_MAX + 20;
            box(s, towerAlong - 4, towerAcross - 4, towerAlong + 4, towerAcross + 4, 38, 0xc8c4bcff, 0);
            box(s, towerAlong - 8, towerAcross - 8, towerAlong + 8, towerAcross + 8, 46, 0x30404cff, 38);
        }
    }

    /** Prédios da cidade, mais altos no centro. Só os que estão na frente e a menos de 9 km. */
    private void city() {
        double reach = 9000;
        double toCity = Math.hypot(CITY_X - camX, CITY_Y - camY);
        if (toCity - CITY_RADIUS > reach) {
            return;
        }
        long min = Math.floorDiv((long) -CITY_RADIUS, (long) BLOCK);
        long max = Math.floorDiv((long) CITY_RADIUS, (long) BLOCK);
        long baseX = Math.floorDiv((long) CITY_X, (long) BLOCK);
        long baseY = Math.floorDiv((long) CITY_Y, (long) BLOCK);
        for (long bx = baseX + min; bx <= baseX + max; bx++) {
            for (long by = baseY + min; by <= baseY + max; by++) {
                double x0 = bx * BLOCK + STREET;
                double y0 = by * BLOCK + STREET;
                double cx = x0 + (BLOCK - STREET) / 2, cy = y0 + (BLOCK - STREET) / 2;
                double fromCenter = Math.hypot(cx - CITY_X, cy - CITY_Y);
                if (fromCenter > CITY_RADIUS - 80) {
                    continue;
                }
                double dx = cx - camX, dy = cy - camY;
                if (dx * fx + dy * fy < -200 || Math.hypot(dx, dy) > reach) {
                    continue;
                }
                int h = hash(bx, by);
                if (h % 10 == 0) {
                    continue; // praça
                }
                double centrality = 1 - fromCenter / CITY_RADIUS;
                double height = 8 + (h % 20) + centrality * centrality * (h % 90);
                double inset = 6 + (h % 25);
                double size = BLOCK - STREET;
                worldBox(x0 + inset, y0 + inset, x0 + size - inset, y0 + size - inset, height,
                        BUILDINGS[h % BUILDINGS.length], 0);
            }
        }
    }

    /** Caixa em coordenadas de uma pista (along, across). */
    private void box(Strip s, double a0, double c0, double a1, double c1, double top, int color, double bottom) {
        double[][] corners = {{a0, c0}, {a1, c0}, {a1, c1}, {a0, c1}};
        double[] xs = new double[4];
        double[] ys = new double[4];
        for (int i = 0; i < 4; i++) {
            xs[i] = s.worldX(corners[i][0], corners[i][1]);
            ys[i] = s.worldY(corners[i][0], corners[i][1]);
        }
        prism(xs, ys, bottom, top, color);
    }

    /** Caixa alinhada aos eixos do mundo (quarteirões da cidade). */
    private void worldBox(double x0, double y0, double x1, double y1, double top, int color, double bottom) {
        prism(new double[]{x0, x1, x1, x0}, new double[]{y0, y0, y1, y1}, bottom, top, color);
    }

    /** Quatro paredes (sombreadas conforme a face) e o telhado. */
    private void prism(double[] xs, double[] ys, double bottom, double top, int color) {
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            // Face voltada para o olho? (normal para fora vs. vetor até a câmera)
            double ex = xs[j] - xs[i], ey = ys[j] - ys[i];
            double nx = ey, ny = -ex;
            double mx = (xs[i] + xs[j]) / 2 - camX, my = (ys[i] + ys[j]) / 2 - camY;
            double facing = nx * mx + ny * my;
            double centerX = (xs[0] + xs[2]) / 2, centerY = (ys[0] + ys[2]) / 2;
            double outward = nx * ((xs[i] + xs[j]) / 2 - centerX) + ny * ((ys[i] + ys[j]) / 2 - centerY);
            if (facing * outward >= 0) {
                continue;
            }
            double light = 0.72 + 0.28 * Math.abs(nx * SUN_DX + ny * SUN_DY) / Math.hypot(nx, ny) / Math.hypot(SUN_DX, SUN_DY);
            quad(xs[i], ys[i], bottom, xs[j], ys[j], bottom, xs[j], ys[j], top, xs[i], ys[i], top, shade(color, light));
        }
        if (camZ > top) {
            quad(xs[0], ys[0], top, xs[1], ys[1], top, xs[2], ys[2], top, xs[3], ys[3], top, shade(color, 1.12));
        }
    }

    /** Retângulo vertical sempre de frente para o olho (árvore). */
    private void billboard(double x, double y, double bottom, double top, double halfWidth, int color) {
        double ox = r0x * halfWidth, oy = r0y * halfWidth;
        double crown = bottom + (top - bottom) * 0.25;
        quad(x - ox * 0.25, y - oy * 0.25, bottom, x + ox * 0.25, y + oy * 0.25, bottom,
                x + ox * 0.25, y + oy * 0.25, crown, x - ox * 0.25, y - oy * 0.25, crown, 0x4a3a2aff);
        quad(x - ox, y - oy, crown, x + ox, y + oy, crown, x + ox * 0.55, y + oy * 0.55, top,
                x - ox * 0.55, y - oy * 0.55, top, color);
    }

    private final double[] qx = new double[4], qy = new double[4];

    /** Preenche um quadrilátero convexo do mundo, com teste de profundidade (uma profundidade por quad). */
    private void quad(double x1, double y1, double z1, double x2, double y2, double z2,
                      double x3, double y3, double z3, double x4, double y4, double z4, int color) {
        double[][] p = {{x1, y1, z1}, {x2, y2, z2}, {x3, y3, z3}, {x4, y4, z4}};
        double dist = Double.MAX_VALUE;
        double minC = Double.MAX_VALUE, maxC = -Double.MAX_VALUE, minR = Double.MAX_VALUE, maxR = -Double.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            if (!project(p[i][0], p[i][1], p[i][2])) {
                return;
            }
            qx[i] = projected[0];
            qy[i] = projected[1];
            dist = Math.min(dist, projected[2]); // o canto mais perto: o chão logo atrás da base não cobre a parede
            minC = Math.min(minC, qx[i]);
            maxC = Math.max(maxC, qx[i]);
            minR = Math.min(minR, qy[i]);
            maxR = Math.max(maxR, qy[i]);
        }
        int c0 = Math.max(0, (int) Math.floor(minC)), c1 = Math.min(WIDTH - 1, (int) Math.ceil(maxC));
        int r0 = Math.max(0, (int) Math.floor(minR)), r1 = Math.min(HEIGHT - 1, (int) Math.ceil(maxR));
        if (c0 > c1 || r0 > r1) {
            return;
        }
        float d = (float) dist;
        int fogged = mix(color, HAZE, clamp((dist - HAZE_START) / (HAZE_FULL - HAZE_START)));
        // Sinal da área decide a orientação dos lados
        double area = 0;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            area += qx[i] * qy[j] - qx[j] * qy[i];
        }
        double sign = Math.signum(area);
        if (sign == 0) {
            return;
        }
        boolean tiny = c1 - c0 <= 1 && r1 - r0 <= 1;
        for (int row = r0; row <= r1; row++) {
            double py = row + 0.5;
            for (int col = c0; col <= c1; col++) {
                double px = col + 0.5;
                if (!tiny && !inside(px, py, sign)) {
                    continue;
                }
                int index = row * WIDTH + col;
                if (d < depth[index]) {
                    depth[index] = d;
                    pixels[index] = fogged;
                }
            }
        }
    }

    private boolean inside(double px, double py, double sign) {
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            double cross = (qx[j] - qx[i]) * (py - qy[i]) - (qy[j] - qy[i]) * (px - qx[i]);
            if (cross * sign < 0) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ luzes

    private void lights(double simTime) {
        for (Strip s : STRIPS) {
            if (Math.hypot(s.x0 - camX, s.y0 - camY) > 30_000) {
                continue;
            }
            // Borda da pista, a cada 60 m dos dois lados
            for (double a = 0; a <= s.length; a += 60) {
                light(s, a, -s.halfWidth - 1.5, EDGE_LIGHT, false);
                light(s, a, s.halfWidth + 1.5, EDGE_LIGHT, false);
            }
            // Cabeceira, aproximação e PAPI nos dois sentidos
            endLights(s, false, simTime);
            endLights(s, true, simTime);
        }
    }

    /**
     * Uma cabeceira: barra verde (quem pousa vê) e vermelha no fim da pista; luzes de aproximação a cada 30 m
     * até 900 m antes, barra transversal a 300 m e o "coelho", um strobe que corre de fora para dentro duas
     * vezes por segundo; PAPI à esquerda de quem pousa, 4 luzes que ficam brancas acima do seu ângulo
     * (na rampa certa, 2 brancas e 2 vermelhas). {@code reverse}: a cabeceira do outro sentido.
     */
    private void endLights(Strip s, boolean reverse, double simTime) {
        double dir = reverse ? -1 : 1;
        double origin = reverse ? s.length : 0;
        // "across" da pista para quem pousa por esta cabeceira: no outro sentido, direita e esquerda trocam
        double rightToAcross = dir * s.side;
        for (double c = -s.halfWidth; c <= s.halfWidth; c += 3) {
            light(s, origin - dir * 2, c, GREEN_LIGHT, false);
            light(s, origin + dir * (s.length + 2), c, RED_LIGHT, false);
        }
        int bars = 30;
        int rabbit = (int) ((simTime * 2 % 1) * bars);
        for (int i = 0; i < bars; i++) {
            double a = origin - dir * (900 - i * 30);
            for (int k = -2; k <= 2; k++) {
                light(s, a, k * 2.2, APPROACH_LIGHT, false);
            }
            if (i == rabbit) {
                light(s, a, 0, STROBE, true);
            }
        }
        for (double c = -15; c <= 15; c += 2.5) {
            light(s, origin - dir * 300, c, APPROACH_LIGHT, false);
        }
        for (int i = 0; i < PAPI_ANGLES.length; i++) {
            double along = origin + dir * PAPI_ALONG;
            double across = -(s.halfWidth + 15 + i * 9) * rightToAcross;
            double x = s.worldX(along, across);
            double y = s.worldY(along, across);
            double angle = Math.toDegrees(Math.atan2(camZ - 1, Math.hypot(camX - x, camY - y)));
            light(s, along, across, angle > PAPI_ANGLES[i] ? STROBE : RED_LIGHT, true);
        }
    }

    private void light(Strip s, double along, double across, int color, boolean bright) {
        double x = s.worldX(along, across);
        double y = s.worldY(along, across);
        if (!project(x, y, 0.8)) {
            return;
        }
        double distance = projected[2];
        if (distance > 25_000) {
            return;
        }
        int col = (int) Math.floor(projected[0]);
        int row = (int) Math.floor(projected[1]);
        int size = bright || distance < 350 ? 2 : 1;
        int fogged = mix(color, HAZE, clamp((distance - 12_000) / 13_000));
        for (int r = row; r < row + size; r++) {
            for (int c = col; c < col + size; c++) {
                if (c < 0 || c >= WIDTH || r < 0 || r >= HEIGHT) {
                    continue;
                }
                int index = r * WIDTH + c;
                if (distance < depth[index] + 2) {
                    pixels[index] = fogged;
                }
            }
        }
    }

    // ------------------------------------------------------------------ utilitários

    /** Ruído de valor 2D em 3 oitavas, de 0 a 1. */
    private static double fbm(double x, double y) {
        return valueNoise(x, y) * 0.55 + valueNoise(x * 2.1 + 17, y * 2.1 + 9) * 0.3
                + valueNoise(x * 4.3 + 41, y * 4.3 + 23) * 0.15;
    }

    private static double valueNoise(double x, double y) {
        long x0 = (long) Math.floor(x), y0 = (long) Math.floor(y);
        double tx = x - x0, ty = y - y0;
        tx = tx * tx * (3 - 2 * tx);
        ty = ty * ty * (3 - 2 * ty);
        double a = hash(x0, y0) / 1_000_003.0, b = hash(x0 + 1, y0) / 1_000_003.0;
        double c = hash(x0, y0 + 1) / 1_000_003.0, d = hash(x0 + 1, y0 + 1) / 1_000_003.0;
        return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * ty;
    }

    private static int hash(long a, long b) {
        long h = a * 73_856_093L ^ b * 19_349_663L;
        h ^= h >>> 13;
        h *= 0x5bd1e995L;
        h ^= h >>> 15;
        return (int) Math.floorMod(h, 1_000_003L);
    }

    private static int shade(int color, double factor) {
        int r = (int) Math.min(255, ((color >>> 24) & 0xff) * factor);
        int g = (int) Math.min(255, ((color >>> 16) & 0xff) * factor);
        int b = (int) Math.min(255, ((color >>> 8) & 0xff) * factor);
        return r << 24 | g << 16 | b << 8 | 0xff;
    }

    private static int mix(int a, int b, double t) {
        int r = (int) (((a >>> 24) & 0xff) * (1 - t) + ((b >>> 24) & 0xff) * t);
        int g = (int) (((a >>> 16) & 0xff) * (1 - t) + ((b >>> 16) & 0xff) * t);
        int bl = (int) (((a >>> 8) & 0xff) * (1 - t) + ((b >>> 8) & 0xff) * t);
        return r << 24 | g << 16 | bl << 8 | 0xff;
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    @Override
    public void dispose() {
        pixmap.dispose();
        texture.dispose();
    }
}
