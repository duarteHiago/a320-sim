package br.a320sim.cockpit.panel;

import br.a320sim.cockpit.audio.Sfx;
import br.a320sim.cockpit.audio.SoundFx;
import br.a320sim.cockpit.gfx.Draw;
import br.a320sim.cockpit.gfx.Draw.Align;
import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import br.a320sim.flightmodel.FlightModel;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

import java.util.List;

/**
 * Flight Control Unit: o painel do piloto automático. Rodinha do mouse sobre uma janela
 * muda o valor (com SHIFT, de 10 em 10); clique liga AP1 e A/THR.
 * O painel é hardware: sempre funciona, mas sem o cartucho do piloto automático
 * as janelas ficam tracejadas e os botões não fazem nada.
 */
public final class FcuPanel {

    private static final Color PANEL = Color.valueOf("2b2f36");
    private static final Color WINDOW = Color.valueOf("0c0d0f");
    private static final Color DIGITS = Color.valueOf("ff9a2e");
    private static final Color LABEL = Color.valueOf("c8ccd4");
    private static final Color LIGHT = Color.valueOf("38ff6a");
    private static final Color HINT = Color.valueOf("6c7380");
    private static final float Y = 96;
    private static final float H = 32;

    private final Rectangle spdWindow = new Rectangle(10, Y, 66, H);
    private final Rectangle hdgWindow = new Rectangle(84, Y, 66, H);
    private final Rectangle altWindow = new Rectangle(158, Y, 84, H);
    private final Rectangle vsWindow = new Rectangle(250, Y, 72, H);
    private final Rectangle apButton = new Rectangle(334, Y + 4, 52, H - 8);
    private final Rectangle athrButton = new Rectangle(394, Y + 4, 60, H - 8);

    private double spd = 250;
    private double hdg = FlightModel.RUNWAY_HEADING;
    private double alt = 5000;
    private double vs;
    private int apPushes;
    private int athrPushes;

    public void handle(Vector2 mouse, float scroll, boolean clicked, boolean coarse, SoundFx fx) {
        int notches = Math.round(-scroll);
        if (notches != 0) {
            int step = coarse ? 10 : 1;
            if (spdWindow.contains(mouse)) {
                spd = clamp(spd + notches * step, 100, 350);
            } else if (hdgWindow.contains(mouse)) {
                hdg = Math.floorMod((int) hdg + notches * step, 360);
            } else if (altWindow.contains(mouse)) {
                alt = clamp(alt + notches * 100 * step, 0, 39000);
            } else if (vsWindow.contains(mouse)) {
                vs = clamp(vs + notches * 100, -6000, 6000);
            } else {
                return;
            }
            fx.play(Sfx.CLICK);
        }
        if (clicked && apButton.contains(mouse)) {
            apPushes++;
            fx.play(Sfx.CLICK);
        }
        if (clicked && athrButton.contains(mouse)) {
            athrPushes++;
            fx.play(Sfx.CLICK);
        }
    }

    public void publish(DataBus bus) {
        bus.publish(Labels.PANEL_SPD, spd);
        bus.publish(Labels.PANEL_HDG, hdg);
        bus.publish(Labels.PANEL_ALT, alt);
        bus.publish(Labels.PANEL_VS, vs);
        bus.publish(Labels.PANEL_AP_PUSH, (double) apPushes);
        bus.publish(Labels.PANEL_ATHR_PUSH, (double) athrPushes);
    }

    public void draw(Draw d, DataBus bus) {
        d.rect(4, Y - 2, 632, H + 4, PANEL);
        boolean apBox = bus.has(Labels.AP_MODES);
        List<String> modes = bus.getList(Labels.AP_MODES);

        window(d, spdWindow, "SPD", apBox && modes.contains("SPD") ? String.format("%03d", (int) spd) : "---");
        window(d, hdgWindow, "HDG", apBox ? String.format("%03d", (int) hdg) : "---");
        window(d, altWindow, "ALT", apBox ? String.format("%05d", (int) alt) : "-----");
        String vsText = vs == 0 ? "-----" : String.format("%+05d", (int) vs);
        window(d, vsWindow, "V/S", apBox && modes.contains("V/S") ? vsText : "-----");
        button(d, apButton, "AP1", bus.getBoolean(Labels.AP_ENGAGED));
        button(d, athrButton, "A/THR", bus.getBoolean(Labels.AP_ATHR_ACTIVE));
        d.text("rodinha ajusta", 468, Y + H / 2 + 7, HINT, Align.LEFT);
        d.text("SHIFT = x10", 468, Y + H / 2 - 7, HINT, Align.LEFT);
    }

    private void window(Draw d, Rectangle r, String label, String value) {
        d.rect(r.x, r.y, r.width, r.height, WINDOW);
        d.text(label, r.x + 4, r.y + r.height - 7, LABEL, Align.LEFT);
        d.text(value, r.x + r.width - 5, r.y + 9, DIGITS, Align.RIGHT);
    }

    private void button(Draw d, Rectangle r, String label, boolean lit) {
        d.rect(r.x, r.y, r.width, r.height, Color.valueOf("4a505a"));
        d.frame(r.x, r.y, r.width, r.height, Color.BLACK);
        d.rect(r.x + 8, r.y + r.height - 6, r.width - 16, 3, lit ? LIGHT : WINDOW);
        d.text(label, r.x + r.width / 2, r.y + 9, LABEL, Align.CENTER);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
