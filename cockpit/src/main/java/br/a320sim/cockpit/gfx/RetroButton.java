package br.a320sim.cockpit.gfx;

import br.a320sim.cockpit.gfx.Draw.Align;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Botão clicável com luz: acende quando {@code lit} é verdadeiro (ex.: falha ativa, pausado). */
public final class RetroButton {

    private static final Color BODY = Color.valueOf("3a404a");
    private static final Color HOVER = Color.valueOf("4c5462");
    private static final Color TEXT = Color.valueOf("d8dce4");

    private final Rectangle area;
    private final Supplier<String> label;
    private final BooleanSupplier lit;
    private final Color light;
    private final Runnable action;

    public RetroButton(float x, float y, float w, float h, Supplier<String> label, BooleanSupplier lit,
                       Color light, Runnable action) {
        this.area = new Rectangle(x, y, w, h);
        this.label = label;
        this.lit = lit;
        this.light = light;
        this.action = action;
    }

    public RetroButton(float x, float y, float w, float h, String label, Runnable action) {
        this(x, y, w, h, () -> label, () -> false, Color.WHITE, action);
    }

    /** @return true se o clique foi neste botão */
    public boolean click(Vector2 mouse) {
        if (area.contains(mouse)) {
            action.run();
            return true;
        }
        return false;
    }

    public void draw(Draw d, Vector2 mouse) {
        boolean on = lit.getAsBoolean();
        d.rect(area.x, area.y, area.width, area.height, on ? light : area.contains(mouse) ? HOVER : BODY);
        d.frame(area.x, area.y, area.width, area.height, Color.BLACK);
        d.text(label.get(), area.x + area.width / 2, area.y + area.height / 2, on ? Color.BLACK : TEXT,
                Align.CENTER);
    }
}
