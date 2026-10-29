package br.a320sim.cockpit.visual;

import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import br.a320sim.flightmodel.FlightModel;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;

import java.nio.ByteOrder;
import java.nio.IntBuffer;

/**
 * Sistema visual: a vista pelo para-brisa, no estilo "Mode 7" dos consoles 16-bit.
 * Para cada pixel lança um raio a partir do olho do piloto; se ele desce, encontra o chão
 * e pinta a pista, a grama, as plantações e a cidade; se sobe, pinta o céu.
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
    private static final double HAZE_START = 10_000;
    private static final double HAZE_FULL = 60_000;

    private static final double RUNWAY_LENGTH = 3240;
    private static final double RUNWAY_HALF_WIDTH = 22.5;
    private static final double RWY_SIN = Math.sin(Math.toRadians(FlightModel.RUNWAY_HEADING));
    private static final double RWY_COS = Math.cos(Math.toRadians(FlightModel.RUNWAY_HEADING));
    private static final double CITY_X = -9000;
    private static final double CITY_Y = 7000;
    private static final double CITY_RADIUS = 4000;

    private static final int SKY_TOP = 0x2a58b8ff;
    private static final int HAZE = 0xb4cce4ff;
    private static final int ASPHALT = 0x45494eff;
    private static final int PAINT = 0xeeeeeeff;
    private static final int AIRPORT_GRASS = 0x6cb44aff;
    private static final int ROAD = 0x7a7a72ff;
    private static final int STREET = 0x4a4a52ff;
    private static final int[] FIELDS = {0x4c9a2aff, 0x5aa832ff, 0x3f8a24ff, 0xb0a448ff, 0x8a6a3aff, 0x66b03aff};
    private static final int[] BUILDINGS = {0x9a9aa2ff, 0x8a8a90ff, 0xb0aaa0ff, 0x7c8088ff};

    private final Pixmap pixmap = new Pixmap(WIDTH, HEIGHT, Pixmap.Format.RGBA8888);
    private final Texture texture = new Texture(pixmap);
    private final int[] pixels = new int[WIDTH * HEIGHT];

    public OutsideView() {
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
    }

    public Texture render(DataBus bus) {
        double camX = bus.getDouble(Labels.TRUE_POS_X, 0) * NM;
        double camY = bus.getDouble(Labels.TRUE_POS_Y, 0) * NM;
        double height = bus.getDouble(Labels.SIM_RADIO_ALT, 0) * FT + EYE_HEIGHT;
        double hdg = Math.toRadians(bus.getDouble(Labels.TRUE_HDG, 0));
        double pitch = Math.toRadians(bus.getDouble(Labels.TRUE_PITCH, 0));
        double bank = Math.toRadians(bus.getDouble(Labels.TRUE_ROLL, 0));

        // Eixos da câmera no mundo (x = leste, y = norte, z = para cima)
        double sh = Math.sin(hdg), ch = Math.cos(hdg), sp = Math.sin(pitch), cp = Math.cos(pitch);
        double sb = Math.sin(bank), cb = Math.cos(bank);
        double fx = sh * cp, fy = ch * cp, fz = sp;
        double r0x = ch, r0y = -sh;
        double u0x = -sh * sp, u0y = -ch * sp, u0z = cp;
        double rx = r0x * cb - u0x * sb, ry = r0y * cb - u0y * sb, rz = -u0z * sb;
        double ux = u0x * cb + r0x * sb, uy = u0y * cb + r0y * sb, uz = u0z * cb;

        for (int row = 0; row < HEIGHT; row++) {
            double v = BORESIGHT_ROW - row - 0.5;
            for (int col = 0; col < WIDTH; col++) {
                double u = col - WIDTH / 2.0 + 0.5;
                double dx = fx * FOCAL + rx * u + ux * v;
                double dy = fy * FOCAL + ry * u + uy * v;
                double dz = fz * FOCAL + rz * u + uz * v;
                double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                int color;
                if (dz >= 0) {
                    color = mix(HAZE, SKY_TOP, Math.min(1, dz / length * 3));
                } else {
                    double t = height / -dz;
                    double distance = t * length;
                    color = distance > HAZE_FULL ? HAZE
                            : mix(ground(camX + dx * t, camY + dy * t), HAZE,
                            clamp((distance - HAZE_START) / (HAZE_FULL - HAZE_START)));
                }
                pixels[row * WIDTH + col] = color;
            }
        }

        IntBuffer buffer = pixmap.getPixels().duplicate().order(ByteOrder.BIG_ENDIAN).asIntBuffer();
        buffer.put(pixels);
        texture.draw(pixmap, 0, 0);
        return texture;
    }

    private static int ground(double x, double y) {
        double along = x * RWY_SIN + y * RWY_COS;
        double across = x * RWY_COS - y * RWY_SIN;
        if (along > -120 && along < RUNWAY_LENGTH + 120 && Math.abs(across) < 160) {
            if (along >= 0 && along <= RUNWAY_LENGTH && Math.abs(across) <= RUNWAY_HALF_WIDTH) {
                return runway(along, Math.abs(across));
            }
            return AIRPORT_GRASS;
        }
        if (Math.hypot(x - CITY_X, y - CITY_Y) < CITY_RADIUS) {
            if (Math.floorMod((long) x, 160) < 22 || Math.floorMod((long) y, 160) < 22) {
                return STREET;
            }
            return BUILDINGS[hash(Math.floorDiv((long) x, 160), Math.floorDiv((long) y, 160)) % BUILDINGS.length];
        }
        if (Math.floorMod((long) x, 2400) < 14 || Math.floorMod((long) y, 2400) < 14) {
            return ROAD;
        }
        return FIELDS[hash(Math.floorDiv((long) x, 600), Math.floorDiv((long) y, 600)) % FIELDS.length];
    }

    /** Marcações da pista: bordas, faixas de cabeceira, ponto de toque e linha central tracejada. */
    private static int runway(double along, double across) {
        boolean edge = across > RUNWAY_HALF_WIDTH - 1.2;
        boolean threshold = (along < 45 || along > RUNWAY_LENGTH - 45)
                && across < RUNWAY_HALF_WIDTH - 3 && Math.floorMod((long) (across * 10), 36) < 18;
        boolean aimingPoint = along > 400 && along < 460 && across > 5 && across < 10;
        boolean centerline = across < 0.9 && along > 80 && along < RUNWAY_LENGTH - 80
                && Math.floorMod((long) along, 50) < 30;
        return edge || threshold || aimingPoint || centerline ? PAINT : ASPHALT;
    }

    private static int hash(long a, long b) {
        long h = a * 73_856_093L ^ b * 19_349_663L;
        h ^= h >>> 13;
        h *= 0x5bd1e995L;
        h ^= h >>> 15;
        return (int) Math.floorMod(h, 1_000_003L);
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
