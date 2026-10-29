package br.a320sim.cockpit.display;

import br.a320sim.cockpit.gfx.Draw;
import br.a320sim.cockpit.gfx.Draw.Align;
import br.a320sim.cockpit.gfx.Theme;
import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import br.a320sim.flightmodel.FlightModel;
import com.badlogic.gdx.graphics.Color;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Navigation Display em modo ROSE: bússola com a proa para cima, a pista de partida
 * e o rastro do voo. Enquanto os sensores alinham, mostra o progresso do alinhamento.
 */
public final class NdDisplay implements CockpitDisplay {

    private static final float RANGE_NM = 10;
    private static final float RADIUS = 78;
    private static final double RUNWAY_LENGTH_NM = 1.75;
    private static final int TRAIL_POINTS = 80;
    private static final double TRAIL_SECONDS = 3;

    private final Deque<double[]> trail = new ArrayDeque<>();
    private double trailTimer;

    @Override
    public void update(DataBus bus, double dt) {
        if (!bus.has(Labels.IR_POS_X)) {
            return;
        }
        double[] here = {bus.getDouble(Labels.IR_POS_X, 0), bus.getDouble(Labels.IR_POS_Y, 0)};
        double[] last = trail.peekLast();
        if (last != null && Math.hypot(here[0] - last[0], here[1] - last[1]) > 3) {
            trail.clear();
        }
        trailTimer -= dt;
        if (trailTimer <= 0) {
            trailTimer = TRAIL_SECONDS;
            trail.addLast(here);
            if (trail.size() > TRAIL_POINTS) {
                trail.removeFirst();
            }
        }
    }

    @Override
    public void draw(Draw d, DataBus bus, Theme t, float x, float y, float size) {
        d.rect(x, y, size, size, t.background());
        float cx = x + size / 2;
        float cy = y + 90;

        if (!bus.has(Labels.IR_HDG)) {
            if ("ALIGNING".equals(bus.getString(Labels.IR_ALIGN_STATUS, ""))) {
                double progress = bus.getDouble(Labels.IR_ALIGN_PROGRESS, 0);
                d.text("IR ALIGN", cx, cy + 20, t.amber(), Align.CENTER);
                d.frame(cx - 50, cy - 6, 100, 12, t.white());
                d.rect(cx - 48, cy - 4, (float) (96 * progress), 8, t.green());
                d.text(Math.round(progress * 100) + "%", cx, cy - 22, t.white(), Align.CENTER);
            } else {
                d.text("HDG", cx, cy, t.red(), Align.CENTER, 2);
            }
            return;
        }

        double hdg = bus.getDouble(Labels.IR_HDG, 0);
        double posX = bus.getDouble(Labels.IR_POS_X, 0);
        double posY = bus.getDouble(Labels.IR_POS_Y, 0);

        d.clip(x, y, size, size);
        d.circle(cx, cy, RADIUS / 2, t.tape());
        for (double[] p : trail) {
            float[] s = toScreen(p[0] - posX, p[1] - posY, hdg, cx, cy);
            d.rect(s[0] - 1, s[1] - 1, 2, 2, t.magenta());
        }
        double rwy = Math.toRadians(FlightModel.RUNWAY_HEADING);
        float[] start = toScreen(-posX, -posY, hdg, cx, cy);
        float[] end = toScreen(Math.sin(rwy) * RUNWAY_LENGTH_NM - posX, Math.cos(rwy) * RUNWAY_LENGTH_NM - posY,
                hdg, cx, cy);
        d.filled().setColor(t.white());
        d.filled().rectLine(start[0], start[1], end[0], end[1], 3);
        d.unclip();

        d.circle(cx, cy, RADIUS, t.white());
        for (int deg = 0; deg < 360; deg += 5) {
            double a = Math.toRadians(90 - (deg - hdg));
            float cos = (float) Math.cos(a);
            float sin = (float) Math.sin(a);
            float len = deg % 10 == 0 ? 6 : 3;
            d.line(cx + RADIUS * cos, cy + RADIUS * sin, cx + (RADIUS - len) * cos, cy + (RADIUS - len) * sin,
                    t.white());
            if (deg % 30 == 0) {
                d.text(label(deg), cx + (RADIUS - 14) * cos, cy + (RADIUS - 14) * sin, t.white(), Align.CENTER);
            }
        }
        if (bus.has(Labels.PANEL_HDG)) {
            double a = Math.toRadians(90 - (bus.getDouble(Labels.PANEL_HDG, 0) - hdg));
            float bx = cx + (RADIUS + 3) * (float) Math.cos(a);
            float by = cy + (RADIUS + 3) * (float) Math.sin(a);
            d.rect(bx - 3, by - 3, 6, 6, t.cyan());
        }

        d.line(cx, cy + RADIUS - 8, cx, cy + RADIUS + 6, t.yellow());
        d.rect(cx - 16, y + size - 22, 32, 15, Color.BLACK);
        d.frame(cx - 16, y + size - 22, 32, 15, t.white());
        d.text(String.format("%03d", Math.round(hdg) % 360), cx, y + size - 14, t.white(), Align.CENTER);

        d.rect(cx - 14, cy - 1, 28, 3, t.yellow());
        d.rect(cx - 1, cy - 12, 3, 20, t.yellow());
        d.rect(cx - 5, cy - 12, 11, 2, t.yellow());

        d.text("GS", x + 6, y + size - 12, t.white(), Align.LEFT);
        d.text(String.valueOf(Math.round(bus.getDouble(Labels.IR_GS, 0))), x + 28, y + size - 12, t.green(),
                Align.LEFT);
        d.text(Math.round(RANGE_NM) + " NM", x + size - 6, y + 10, t.cyan(), Align.RIGHT);
    }

    private static float[] toScreen(double dxNm, double dyNm, double hdg, float cx, float cy) {
        double distance = Math.hypot(dxNm, dyNm) * RADIUS / RANGE_NM;
        double relative = Math.toRadians(Math.toDegrees(Math.atan2(dxNm, dyNm)) - hdg);
        return new float[]{cx + (float) (Math.sin(relative) * distance), cy + (float) (Math.cos(relative) * distance)};
    }

    private static String label(int deg) {
        return switch (deg) {
            case 0 -> "N";
            case 90 -> "E";
            case 180 -> "S";
            case 270 -> "W";
            default -> String.valueOf(deg / 10);
        };
    }
}
