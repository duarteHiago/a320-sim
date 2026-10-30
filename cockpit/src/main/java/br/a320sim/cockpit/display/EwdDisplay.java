package br.a320sim.cockpit.display;

import br.a320sim.cockpit.gfx.Draw;
import br.a320sim.cockpit.gfx.Draw.Align;
import br.a320sim.cockpit.gfx.Theme;
import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import com.badlogic.gdx.graphics.Color;

/**
 * Engine/Warning Display (ECAM superior): mostradores dos motores (N1 ou EPR, conforme o motor),
 * posição dos flaps e as mensagens do FWC — warnings em vermelho, cautions em âmbar.
 */
public final class EwdDisplay implements CockpitDisplay {

    private static final String[] FLAP_NAMES = {"0", "1", "2", "3", "FULL"};
    private static final float GAUGE_START = 210;
    private static final float GAUGE_SWEEP = 240;
    private static final int MAX_MESSAGES = 5;

    @Override
    public void draw(Draw d, DataBus bus, Theme t, float x, float y, float size) {
        d.rect(x, y, size, size, t.background());
        float mid = x + size / 2;
        float gaugeY = y + 145;

        if (bus.has(Labels.ENG1_N1)) {
            boolean epr = "EPR".equals(bus.getString(Labels.ENG_PRIMARY_PARAM, "N1"));
            engine(d, bus, t, x + 50, gaugeY, epr, Labels.ENG1_N1, Labels.ENG1_EPR);
            engine(d, bus, t, x + size - 50, gaugeY, epr, Labels.ENG2_N1, Labels.ENG2_EPR);
            d.text(epr ? "EPR" : "N1", mid, gaugeY + 4, t.white(), Align.CENTER);
            d.text(epr ? "" : "%", mid, gaugeY - 12, t.cyan(), Align.CENTER);
            d.text(bus.getString(Labels.ENG_TYPE, ""), mid, y + size - 10, t.white(), Align.CENTER);
        } else {
            d.text("ENG XX", mid, gaugeY, t.amber(), Align.CENTER);
        }

        int flaps = (int) Math.max(0, Math.min(FLAP_NAMES.length - 1, bus.getDouble(Labels.CTL_FLAPS, 0)));
        d.text("FLAP", mid, y + 104, t.white(), Align.CENTER);
        d.text(FLAP_NAMES[flaps], mid, y + 90, t.green(), Align.CENTER);
        d.line(x + 6, y + 80, x + size - 6, y + 80, t.white());

        float lineY = y + 68;
        int shown = 0;
        for (String warning : bus.getList(Labels.FWC_WARNINGS)) {
            if (shown++ < MAX_MESSAGES) {
                d.text(warning, x + 8, lineY, t.red(), Align.LEFT);
                lineY -= 14;
            }
        }
        for (String caution : bus.getList(Labels.FWC_CAUTIONS)) {
            if (shown++ < MAX_MESSAGES) {
                d.text(caution, x + 8, lineY, t.amber(), Align.LEFT);
                lineY -= 14;
            }
        }
    }

    private void engine(Draw d, DataBus bus, Theme t, float cx, float cy, boolean epr, String n1Label,
                        String eprLabel) {
        double value = epr ? bus.getDouble(eprLabel, 1) : bus.getDouble(n1Label, 0);
        double fraction = epr ? (value - 1) / 0.6 : value / 100;
        gauge(d, t, cx, cy, fraction, epr ? String.format("%.3f", value) : String.format("%.1f", value));
    }

    private void gauge(Draw d, Theme t, float cx, float cy, double fraction, String text) {
        float radius = 30;
        d.arc(cx, cy, radius, GAUGE_START, GAUGE_START - GAUGE_SWEEP * 0.95f, t.white());
        d.arc(cx, cy, radius, GAUGE_START - GAUGE_SWEEP * 0.95f, GAUGE_START - GAUGE_SWEEP, t.red());
        double a = Math.toRadians(GAUGE_START - GAUGE_SWEEP * Math.max(0, Math.min(1.05, fraction)));
        d.filled().setColor(t.green());
        d.filled().rectLine(cx, cy, cx + radius * (float) Math.cos(a), cy + radius * (float) Math.sin(a), 2);
        d.rect(cx - 2, cy - 2, 4, 4, Color.BLACK);
        d.frame(cx - 4, cy - 30, 38, 14, t.tape());
        d.text(text, cx + 32, cy - 23, t.green(), Align.RIGHT);
    }
}
