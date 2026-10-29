package br.a320sim.cockpit.panel;

import br.a320sim.cockpit.audio.AlertSounds;
import br.a320sim.cockpit.gfx.Draw;
import br.a320sim.cockpit.gfx.Draw.Align;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

/** Faixa de cima: luzes MASTER WARN / MASTER CAUT (clicáveis para reconhecer) e o rack montado. */
public final class Glareshield {

    private static final Color RED = Color.valueOf("ff3a30");
    private static final Color DARK_RED = Color.valueOf("4a1210");
    private static final Color AMBER = Color.valueOf("ffb020");
    private static final Color DARK_AMBER = Color.valueOf("4a3408");
    private static final Color HINT = Color.valueOf("6c7380");

    private final Rectangle warn = new Rectangle(4, 342, 112, 16);
    private final Rectangle caut = new Rectangle(120, 342, 112, 16);

    public boolean contains(Vector2 mouse) {
        return warn.contains(mouse) || caut.contains(mouse);
    }

    public void draw(Draw d, AlertSounds alerts, float time, String rack) {
        boolean blink = (time % 0.6f) < 0.4f;
        light(d, warn, "MASTER WARN", alerts.warnLight() && blink, RED, DARK_RED);
        light(d, caut, "MASTER CAUT", alerts.cautLight(), AMBER, DARK_AMBER);
        d.text(rack, 636, 350, HINT, Align.RIGHT);
    }

    private void light(Draw d, Rectangle r, String label, boolean lit, Color on, Color off) {
        d.rect(r.x, r.y, r.width, r.height, lit ? on : off);
        d.text(label, r.x + r.width / 2, r.y + r.height / 2, lit ? Color.BLACK : on.cpy().mul(0.6f), Align.CENTER);
    }
}
