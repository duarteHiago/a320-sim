package br.a320sim.cockpit.display;

import br.a320sim.cockpit.gfx.Draw;
import br.a320sim.cockpit.gfx.Draw.Align;
import br.a320sim.cockpit.gfx.Theme;
import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/**
 * Primary Flight Display: horizonte artificial, fitas de velocidade e altitude,
 * proa e os modos do piloto automático (FMA). Sem dados de um sensor, mostra a bandeira vermelha.
 */
public final class PfdDisplay implements CockpitDisplay {

    private static final float PITCH_PX = 4f;
    private static final float SPEED_PX = 2f;
    private static final float ALT_PX = 0.15f;
    private static final float HDG_PX = 3f;
    /** Velocidade de estol aproximada por posição de flaps (kt). */
    private static final double[] STALL_KT = {145, 131, 126, 122, 117};

    @Override
    public void draw(Draw d, DataBus bus, Theme t, float x, float y, float size) {
        d.rect(x, y, size, size, t.background());
        float ax = x + 40;
        float ay = y + 30;
        float aw = 116;
        float ah = 150;
        float cx = ax + aw / 2;
        float cy = ay + ah / 2;

        attitude(d, bus, t, ax, ay, aw, ah, cx, cy);
        speedTape(d, bus, t, x + 2, ay, 36, ah, cy);
        altitudeTape(d, bus, t, ax + aw + 4, ay, size - aw - 44, ah, cy);
        headingTape(d, bus, t, ax, y + 4, aw, 22);
        fma(d, bus, t, x, y + size - 13, size);

        if (bus.has(Labels.ADR_VS)) {
            double vs = bus.getDouble(Labels.ADR_VS, 0);
            Color color = Math.abs(vs) > 6000 ? t.amber() : t.green();
            d.text(String.format("%+05d", Math.round(vs / 100) * 100), x + size - 4, y + 15, color, Align.RIGHT);
        }
    }

    private void attitude(Draw d, DataBus bus, Theme t, float ax, float ay, float aw, float ah, float cx, float cy) {
        if (!bus.has(Labels.IR_PITCH)) {
            d.text("ATT", cx, cy + 20, t.red(), Align.CENTER, 2);
            aircraftSymbol(d, t, cx, cy);
            return;
        }
        float pitch = (float) bus.getDouble(Labels.IR_PITCH, 0);
        float bank = (float) bus.getDouble(Labels.IR_ROLL, 0);
        float offset = pitch * PITCH_PX;

        d.clip(ax, ay, aw, ah);
        ShapeRenderer s = d.filled();
        s.setColor(t.sky());
        s.rect(cx - 200, cy - offset, 200, offset, 400, 400, 1, 1, bank);
        s.setColor(t.ground());
        s.rect(cx - 200, cy - offset - 400, 200, offset + 400, 400, 400, 1, 1, bank);

        for (int p = -40; p <= 40; p += 5) {
            float py = (p - pitch) * PITCH_PX;
            if (p != 0 && Math.abs(py) > 70) {
                continue;
            }
            float half = p == 0 ? 200 : p % 10 == 0 ? 22 : 10;
            float[] a = rotate(-half, py, bank);
            float[] b = rotate(half, py, bank);
            d.line(cx + a[0], cy + a[1], cx + b[0], cy + b[1], t.white());
            if (p != 0 && p % 10 == 0) {
                float[] label = rotate(half + 11, py, bank);
                d.text(String.valueOf(Math.abs(p)), cx + label[0], cy + label[1], t.white(), Align.CENTER);
            }
        }
        d.unclip();

        for (int mark : new int[]{-45, -30, -20, -10, 0, 10, 20, 30, 45}) {
            double a = Math.toRadians(90 + mark);
            float len = mark % 30 == 0 || Math.abs(mark) == 45 ? 7 : 4;
            d.line(cx + 62 * (float) Math.cos(a), cy + 62 * (float) Math.sin(a),
                    cx + (62 + len) * (float) Math.cos(a), cy + (62 + len) * (float) Math.sin(a), t.white());
        }
        double idx = Math.toRadians(90 + bank);
        float ix = cx + 61 * (float) Math.cos(idx);
        float iy = cy + 61 * (float) Math.sin(idx);
        float[] l = rotate(-4, -6, bank);
        float[] r = rotate(4, -6, bank);
        d.triangle(ix, iy, ix + l[0], iy + l[1], ix + r[0], iy + r[1], t.white());
        d.triangle(cx, cy + 69, cx - 4, cy + 75, cx + 4, cy + 75, t.yellow());

        String protection = bus.getString(Labels.FCTL_PROT, "");
        if (!protection.isEmpty()) {
            d.text(protection + " PROT", cx, ay + 10, t.amber(), Align.CENTER);
        }
        aircraftSymbol(d, t, cx, cy);
    }

    private void aircraftSymbol(Draw d, Theme t, float cx, float cy) {
        Color black = Color.BLACK;
        d.rect(cx - 47, cy - 2, 31, 5, black);
        d.rect(cx + 16, cy - 2, 31, 5, black);
        d.rect(cx - 46, cy - 1, 29, 3, t.yellow());
        d.rect(cx - 20, cy - 8, 3, 8, t.yellow());
        d.rect(cx + 17, cy - 1, 29, 3, t.yellow());
        d.rect(cx + 17, cy - 8, 3, 8, t.yellow());
        d.rect(cx - 2, cy - 2, 5, 5, black);
        d.rect(cx - 1, cy - 1, 3, 3, t.yellow());
    }

    private void speedTape(Draw d, DataBus bus, Theme t, float tx, float ty, float tw, float th, float cy) {
        d.rect(tx, ty, tw, th, t.tape());
        if (!bus.has(Labels.ADR_IAS)) {
            d.text("SPD", tx + tw / 2, cy, t.red(), Align.CENTER);
            return;
        }
        double ias = bus.getDouble(Labels.ADR_IAS, 0);
        double center = Math.max(30, ias);
        int flaps = (int) Math.max(0, Math.min(STALL_KT.length - 1, bus.getDouble(Labels.CTL_FLAPS, 0)));

        d.clip(tx, ty, tw, th);
        float stallY = cy + (float) (STALL_KT[flaps] - center) * SPEED_PX;
        if (stallY > ty) {
            d.rect(tx + tw - 5, ty, 5, stallY - ty, t.red());
        }
        int first = (int) Math.floor((center - 40) / 10) * 10;
        for (int s = Math.max(30, first); s <= center + 40; s += 10) {
            float sy = cy + (float) (s - center) * SPEED_PX;
            d.line(tx + tw - 7, sy, tx + tw, sy, t.white());
            if (s % 20 == 0) {
                d.text(String.valueOf(s), tx + tw - 9, sy, t.white(), Align.RIGHT);
            }
        }
        d.unclip();

        d.rect(tx, cy - 8, tw - 2, 16, Color.BLACK);
        d.frame(tx, cy - 8, tw - 2, 16, t.yellow());
        d.text(String.valueOf(Math.round(ias)), tx + tw - 6, cy, t.green(), Align.RIGHT);
    }

    private void altitudeTape(Draw d, DataBus bus, Theme t, float tx, float ty, float tw, float th, float cy) {
        d.rect(tx, ty, tw, th, t.tape());
        if (!bus.has(Labels.ADR_ALT)) {
            d.text("ALT", tx + tw / 2, cy, t.red(), Align.CENTER);
            return;
        }
        double alt = bus.getDouble(Labels.ADR_ALT, 0);

        d.clip(tx, ty, tw, th);
        int first = (int) Math.floor((alt - 600) / 100) * 100;
        for (int a = first; a <= alt + 600; a += 100) {
            float ay = cy + (float) (a - alt) * ALT_PX;
            d.line(tx, ay, tx + 5, ay, t.white());
            if (a % 500 == 0) {
                d.text(String.valueOf(a / 100), tx + 8, ay, t.white(), Align.LEFT);
            }
        }
        d.unclip();

        d.rect(tx + 1, cy - 8, tw - 1, 16, Color.BLACK);
        d.frame(tx + 1, cy - 8, tw - 1, 16, t.yellow());
        d.text(String.valueOf(Math.round(alt)), tx + tw - 3, cy, t.green(), Align.RIGHT);

        if (bus.has(Labels.PANEL_ALT)) {
            d.rect(tx, ty + th - 14, tw, 14, Color.BLACK);
            d.text(String.valueOf(Math.round(bus.getDouble(Labels.PANEL_ALT, 0))), tx + tw / 2, ty + th - 7,
                    t.cyan(), Align.CENTER);
        }
    }

    private void headingTape(Draw d, DataBus bus, Theme t, float hx, float hy, float hw, float hh) {
        d.rect(hx, hy, hw, hh, t.tape());
        if (!bus.has(Labels.IR_HDG)) {
            d.text("HDG", hx + hw / 2, hy + hh / 2, t.red(), Align.CENTER);
            return;
        }
        double hdg = bus.getDouble(Labels.IR_HDG, 0);
        float center = hx + hw / 2;

        d.clip(hx, hy, hw, hh);
        int first = (int) Math.floor((hdg - 25) / 5) * 5;
        for (int h = first; h <= hdg + 25; h += 5) {
            float px = center + (float) (h - hdg) * HDG_PX;
            boolean major = h % 10 == 0;
            d.line(px, hy + hh, px, hy + hh - (major ? 6 : 3), t.white());
            if (major) {
                d.text(String.format("%02d", Math.floorMod(h, 360) / 10), px, hy + 8, t.white(), Align.CENTER);
            }
        }
        if (bus.has(Labels.PANEL_HDG)) {
            double sel = wrap180(bus.getDouble(Labels.PANEL_HDG, 0) - hdg);
            float sx = center + (float) Math.max(-19, Math.min(19, sel)) * HDG_PX;
            d.triangle(sx, hy + hh - 7, sx - 4, hy + hh, sx + 4, hy + hh, t.cyan());
        }
        d.unclip();
        d.line(center, hy + 2, center, hy + hh, t.yellow());
    }

    private void fma(Draw d, DataBus bus, Theme t, float x, float y, float size) {
        float col = size / 5;
        for (int i = 1; i < 5; i++) {
            d.line(x + col * i, y - 6, x + col * i, y + 6, t.tape());
        }
        if (bus.getBoolean(Labels.AP_ATHR_ACTIVE)) {
            d.text("SPEED", x + col / 2, y, t.green(), Align.CENTER);
            d.text("A/THR", x + col * 3.5f, y, t.white(), Align.CENTER);
        }
        d.text(bus.getString(Labels.AP_VERT_MODE, ""), x + col * 1.5f, y, t.green(), Align.CENTER);
        d.text(bus.getString(Labels.AP_LAT_MODE, ""), x + col * 2.5f, y, t.green(), Align.CENTER);
        if (bus.getBoolean(Labels.AP_ENGAGED)) {
            d.text("AP1", x + col * 4.5f, y, t.white(), Align.CENTER);
        }
    }

    private static float[] rotate(float px, float py, float degrees) {
        double r = Math.toRadians(degrees);
        float cos = (float) Math.cos(r);
        float sin = (float) Math.sin(r);
        return new float[]{px * cos - py * sin, px * sin + py * cos};
    }

    private static double wrap180(double deg) {
        return Math.floorMod((long) Math.round(deg) + 180, 360) - 180;
    }
}
