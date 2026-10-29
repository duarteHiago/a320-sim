package br.a320sim.cockpit.visual;

import br.a320sim.cockpit.gfx.Draw;
import br.a320sim.cockpit.gfx.Draw.Align;
import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Disposable;

/**
 * Vista pela janela: a imagem do sistema visual ampliada 2x, a moldura da cabine
 * e um HUD retrô com os dados essenciais. O HUD usa os sensores, então some se eles falharem.
 */
public final class Windshield implements Disposable {

    private static final int SCALE = 2;
    private static final Color FRAME = Color.valueOf("1c1f24");
    private static final Color HUD = Color.valueOf("5cff7a");
    private static final Color HUD_SHADOW = new Color(0, 0, 0, 0.55f);

    private final OutsideView view = new OutsideView();

    public void draw(Draw d, DataBus bus, float x, float y) {
        float w = OutsideView.WIDTH * SCALE;
        float h = OutsideView.HEIGHT * SCALE;
        d.image(view.render(bus), x, y, w, h);
        frame(d, x, y, w, h);
        hud(d, bus, x + w / 2, y + h - (float) OutsideView.BORESIGHT_ROW * SCALE, x, w);
    }

    private void frame(Draw d, float x, float y, float w, float h) {
        d.triangle(x, y + h, x, y + h - 60, x + 40, y + h, FRAME);
        d.triangle(x + w, y + h, x + w, y + h - 60, x + w - 40, y + h, FRAME);
        d.rect(x, y, w, 8, FRAME);
        d.triangle(x, y + 8, x + 70, y + 8, x, y + 40, FRAME);
        d.triangle(x + w, y + 8, x + w - 70, y + 8, x + w, y + 40, FRAME);
    }

    private void hud(Draw d, DataBus bus, float cx, float cy, float left, float width) {
        if (bus.has(Labels.ADR_IAS)) {
            box(d, left + 80, cy, String.valueOf(Math.round(bus.getDouble(Labels.ADR_IAS, 0))), "KT");
            box(d, left + width - 80, cy, String.valueOf(Math.round(bus.getDouble(Labels.ADR_ALT, 0))), "FT");
        }
        if (bus.has(Labels.IR_HDG)) {
            d.text(String.format("%03d", Math.round(bus.getDouble(Labels.IR_HDG, 0)) % 360), cx, cy + 56, HUD,
                    Align.CENTER);
            double aoa = bus.getDouble(Labels.IR_PITCH, 0) - bus.getDouble(Labels.IR_FPA, 0);
            float fpmY = cy - (float) (Math.tan(Math.toRadians(aoa)) * OutsideView.FOCAL * SCALE);
            flightPathMarker(d, cx, fpmY);
        }
        d.line(cx - 6, cy, cx + 6, cy, HUD);
        d.line(cx, cy - 6, cx, cy + 6, HUD);
    }

    /** O "passarinho": aponta para onde o avião está realmente indo. */
    private void flightPathMarker(Draw d, float x, float y) {
        d.circle(x, y, 5, HUD);
        d.line(x - 16, y, x - 5, y, HUD);
        d.line(x + 5, y, x + 16, y, HUD);
        d.line(x, y + 5, x, y + 11, HUD);
    }

    private void box(Draw d, float x, float y, String value, String unit) {
        d.rect(x - 30, y - 9, 60, 18, HUD_SHADOW);
        d.frame(x - 30, y - 9, 60, 18, HUD);
        d.text(value, x, y, HUD, Align.CENTER);
        d.text(unit, x, y - 18, HUD, Align.CENTER);
    }

    @Override
    public void dispose() {
        view.dispose();
    }
}
