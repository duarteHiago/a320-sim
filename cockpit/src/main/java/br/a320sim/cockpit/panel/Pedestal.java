package br.a320sim.cockpit.panel;

import br.a320sim.cockpit.gfx.Draw;
import br.a320sim.cockpit.gfx.Draw.Align;
import br.a320sim.cockpit.input.ControlInputs;
import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import com.badlogic.gdx.graphics.Color;

/** Pedestal e rodapé: manete, flaps, trem, freio, estado da simulação e ajuda de teclas. */
public final class Pedestal {

    private static final Color PANEL = Color.valueOf("23262c");
    private static final Color LABEL = Color.valueOf("c8ccd4");
    private static final Color HINT = Color.valueOf("6c7380");
    private static final Color GREEN = Color.valueOf("38ff6a");
    private static final Color AMBER = Color.valueOf("ffb020");
    private static final Color RED = Color.valueOf("ff3a30");
    private static final Color CYAN = Color.valueOf("38e8ff");
    private static final Color YELLOW = Color.valueOf("ffff48");
    private static final String[] FLAP_NAMES = {"0", "1", "2", "3", "FULL"};
    private static final float BAR_X = 60;
    private static final float BAR_W = 150;

    public void draw(Draw d, DataBus bus, ControlInputs in, boolean paused) {
        d.rect(4, 34, 632, 56, PANEL);

        double lever = in.thrustLever();
        d.text("MANETE", 12, 74, LABEL, Align.LEFT);
        d.text(detentName(lever), 12, 54, GREEN, Align.LEFT);
        d.rect(BAR_X, 46, BAR_W, 10, Color.BLACK);
        d.rect(BAR_X, 46, (float) (BAR_W * lever), 10, GREEN);
        for (double detent : new double[]{0.85, 0.95, 1.0}) {
            float dx = BAR_X + (float) (BAR_W * detent);
            d.line(dx, 44, dx, 58, LABEL);
        }
        if (bus.getBoolean(Labels.AP_ATHR_ACTIVE)) {
            float ax = BAR_X + (float) (BAR_W * bus.getDouble(Labels.AP_ATHR_CMD, 0));
            d.triangle(ax, 58, ax - 4, 66, ax + 4, 66, CYAN);
        }
        d.text(Math.round(lever * 100) + "%", BAR_X + BAR_W, 74, LABEL, Align.RIGHT);

        d.text("FLAPS", 240, 74, LABEL, Align.LEFT);
        d.text(FLAP_NAMES[in.flaps()], 240, 54, GREEN, Align.LEFT);
        d.text("TREM", 310, 74, LABEL, Align.LEFT);
        d.text(in.gearDown() ? "DN" : "UP", 310, 54, in.gearDown() ? GREEN : LABEL, Align.LEFT);
        if (in.brakes() > 0) {
            d.text("FREIO", 370, 54, AMBER, Align.LEFT);
        }

        if (bus.getBoolean(Labels.SIM_CRASHED)) {
            d.text("CRASH!  R = voltar a pista", 628, 64, RED, Align.RIGHT);
        } else if (paused) {
            d.text("PAUSA", 628, 64, YELLOW, Align.RIGHT);
        }

        d.text("W/S A/D manche  Q/E leme  SHIFT/CTRL manete  1-4 IDLE/CL/FLX/TOGA  F/V flaps  G trem",
                8, 22, HINT, Align.LEFT);
        d.text("B freio  ESPACO desl.AP  M silencia  P pausa  R pista  T em voo  I alinhar  C vista  ESC rack",
                8, 8, HINT, Align.LEFT);
    }

    private static String detentName(double lever) {
        if (lever >= 0.999) {
            return "TOGA";
        }
        if (Math.abs(lever - 0.95) < 0.005) {
            return "FLX";
        }
        if (Math.abs(lever - 0.85) < 0.005) {
            return "CL";
        }
        return lever < 0.005 ? "IDLE" : "MAN";
    }
}
