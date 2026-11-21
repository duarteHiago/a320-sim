package br.a320sim.cockpit.visual;

import br.a320sim.cockpit.gfx.Draw;
import br.a320sim.cockpit.gfx.Draw.Align;
import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import br.a320sim.flightmodel.world.Runway;
import br.a320sim.flightmodel.world.World;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Disposable;

/**
 * Vista pela janela: a imagem do sistema visual ampliada 2x, a moldura da cabine e um HUD retrô.
 * O HUD é "conformal": a linha do horizonte e a fita de proa batem com o mundo lá fora.
 * Ele usa os sensores (ADR/IR), então cada parte some quando a caixa que a alimenta falha.
 */
public final class Windshield implements Disposable {

    private static final int SCALE = 2;
    private static final double PX = OutsideView.FOCAL * SCALE;
    /** Pixels de tela por grau perto do centro. */
    private static final double PX_PER_DEG = PX * Math.PI / 180;
    private static final Color FRAME = Color.valueOf("1c1f24");
    private static final Color FRAME_EDGE = Color.valueOf("2c3038");
    private static final Color WIPER = Color.valueOf("0e1012");
    private static final Color HUD = Color.valueOf("5cff7a");
    private static final Color HUD_DIM = new Color(0.36f, 1f, 0.48f, 0.55f);
    private static final Color HUD_SHADOW = new Color(0, 0, 0, 0.55f);

    private final OutsideView view = new OutsideView();

    public void draw(Draw d, DataBus bus, float x, float y) {
        float w = OutsideView.WIDTH * SCALE;
        float h = OutsideView.HEIGHT * SCALE;
        d.image(view.render(bus), x, y, w, h);
        frame(d, x, y, w, h);
        float cx = x + w / 2;
        float cy = y + h - (float) OutsideView.BORESIGHT_ROW * SCALE;
        hud(d, bus, cx, cy, x, y, w, h);
    }

    // ------------------------------------------------------------------ moldura

    private void frame(Draw d, float x, float y, float w, float h) {
        // Cantos superiores e o painel embaixo
        d.triangle(x, y + h, x, y + h - 60, x + 40, y + h, FRAME);
        d.triangle(x + w, y + h, x + w, y + h - 60, x + w - 40, y + h, FRAME);
        d.rect(x, y, w, 8, FRAME);
        d.triangle(x, y + 8, x + 70, y + 8, x, y + 40, FRAME);
        d.triangle(x + w, y + 8, x + w - 70, y + 8, x + w, y + 40, FRAME);
        // Coluna central entre os dois para-brisas: o comandante senta à esquerda, então ela fica à direita
        float postBottom = x + w * 0.74f;
        float postTop = x + w * 0.765f;
        d.triangle(postBottom - 9, y + 8, postBottom + 9, y + 8, postTop + 5, y + h, FRAME);
        d.triangle(postBottom - 9, y + 8, postTop + 5, y + h, postTop - 5, y + h, FRAME);
        d.line(postBottom - 9, y + 8, postTop - 5, y + h, FRAME_EDGE);
        // Limpadores em repouso
        wiper(d, x + w * 0.20f, y + 9, x + w * 0.40f, y + 22);
        wiper(d, x + w * 0.62f, y + 9, x + w * 0.77f, y + 20);
    }

    private void wiper(Draw d, float x1, float y1, float x2, float y2) {
        d.line(x1, y1, x2, y2, WIPER);
        d.line(x1, y1 + 1, x2, y2 + 1, WIPER);
        d.line(x1 + 2, y1, x1 + 2, y1 - 2, WIPER);
    }

    // ------------------------------------------------------------------ HUD

    private void hud(Draw d, DataBus bus, float cx, float cy, float left, float bottom, float width, float height) {
        boolean attitude = bus.has(Labels.IR_PITCH) && bus.has(Labels.IR_ROLL);
        if (attitude) {
            pitchLadder(d, cx, cy, bus.getDouble(Labels.IR_PITCH, 0), bus.getDouble(Labels.IR_ROLL, 0));
            double aoa = bus.getDouble(Labels.IR_PITCH, 0) - bus.getDouble(Labels.IR_FPA, 0);
            flightPathMarker(d, cx, cy - (float) (Math.tan(Math.toRadians(aoa)) * PX));
        }
        if (bus.has(Labels.IR_HDG)) {
            headingTape(d, cx, bottom + height - 14, bus.getDouble(Labels.IR_HDG, 0));
        }
        if (bus.has(Labels.ADR_IAS)) {
            box(d, left + 80, cy, String.valueOf(Math.round(bus.getDouble(Labels.ADR_IAS, 0))), "KT");
            box(d, left + width - 80, cy, String.valueOf(Math.round(bus.getDouble(Labels.ADR_ALT, 0))), "FT");
            double vs = bus.getDouble(Labels.ADR_VS, 0);
            d.text(String.format("VS %+d", Math.round(vs / 50) * 50), left + width - 80, cy - 32, HUD, Align.CENTER);
        }
        if (bus.has(Labels.IR_GS)) {
            d.text("GS " + Math.round(bus.getDouble(Labels.IR_GS, 0)), left + 80, cy - 32, HUD, Align.CENTER);
        }
        // Altímetro de radar: só perto do chão, onde ele importa
        double radioAlt = bus.getDouble(Labels.SIM_RADIO_ALT, 9999);
        if (bus.has(Labels.SIM_RADIO_ALT) && radioAlt < 2500 && !bus.getBoolean(Labels.SIM_ON_GROUND)) {
            d.text("RA " + Math.round(radioAlt / 10) * 10, left + width - 90, bottom + 22, HUD, Align.CENTER);
        }
        runwayDistance(d, bus, left + 90, bottom + 22);
        // Referência fixa do nariz (a "asinha" do avião)
        d.line(cx - 22, cy, cx - 9, cy, HUD);
        d.line(cx - 9, cy, cx - 5, cy - 4, HUD);
        d.line(cx + 9, cy, cx + 22, cy, HUD);
        d.line(cx + 9, cy, cx + 5, cy - 4, HUD);
        d.rect(cx - 1, cy - 1, 2, 2, HUD);
    }

    /**
     * Escada de arfagem: o horizonte e degraus a cada 5°, girando com a inclinação das asas.
     * Degraus acima do horizonte são cheios, abaixo são tracejados (dá para saber de relance se o nariz está acima).
     */
    private void pitchLadder(Draw d, float cx, float cy, double pitch, double roll) {
        double angle = Math.toRadians(roll);
        float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
        for (int rung = -30; rung <= 30; rung += 5) {
            double offset = Math.tan(Math.toRadians(rung - pitch)) * PX;
            if (offset > 58 || offset < -80) { // não invade a fita de proa lá em cima
                continue;
            }
            float oy = (float) offset;
            if (rung == 0) {
                segment(d, cx, cy, cos, sin, -230, oy, -34, oy, HUD);
                segment(d, cx, cy, cos, sin, 34, oy, 230, oy, HUD);
                continue;
            }
            float tick = rung > 0 ? -5 : 5;
            Color color = HUD_DIM;
            if (rung > 0) {
                segment(d, cx, cy, cos, sin, -70, oy, -34, oy, color);
                segment(d, cx, cy, cos, sin, 34, oy, 70, oy, color);
            } else {
                for (float s = 34; s < 70; s += 12) {
                    segment(d, cx, cy, cos, sin, -s - 7, oy, -s, oy, color);
                    segment(d, cx, cy, cos, sin, s, oy, s + 7, oy, color);
                }
            }
            segment(d, cx, cy, cos, sin, -70, oy, -70, oy + tick, color);
            segment(d, cx, cy, cos, sin, 70, oy, 70, oy + tick, color);
            String label = String.valueOf(Math.abs(rung));
            d.text(label, cx + rotX(-84, oy, cos, sin), cy + rotY(-84, oy, cos, sin), color, Align.CENTER);
            d.text(label, cx + rotX(84, oy, cos, sin), cy + rotY(84, oy, cos, sin), color, Align.CENTER);
        }
    }

    /** Asa direita para baixo (roll positivo) faz o mundo girar no sentido anti-horário na tela. */
    private static float rotX(float x, float y, float cos, float sin) {
        return x * cos - y * sin;
    }

    private static float rotY(float x, float y, float cos, float sin) {
        return x * sin + y * cos;
    }

    private void segment(Draw d, float cx, float cy, float cos, float sin, float x1, float y1, float x2, float y2,
                         Color color) {
        d.line(cx + rotX(x1, y1, cos, sin), cy + rotY(x1, y1, cos, sin),
                cx + rotX(x2, y2, cos, sin), cy + rotY(x2, y2, cos, sin), color);
    }

    /** Fita de proa no topo, na mesma escala do mundo: um grau na fita é um grau lá fora. */
    private void headingTape(Draw d, float cx, float y, double heading) {
        float half = 190;
        int first = (int) Math.floor(heading - half / PX_PER_DEG);
        int last = (int) Math.ceil(heading + half / PX_PER_DEG);
        for (int deg = first; deg <= last; deg++) {
            if (Math.floorMod(deg, 5) != 0) {
                continue;
            }
            float px = cx + (float) ((deg - heading) * PX_PER_DEG);
            if (Math.abs(px - cx) > half) {
                continue;
            }
            boolean major = Math.floorMod(deg, 10) == 0;
            d.line(px, y - 8, px, y - 8 + (major ? 6 : 3), HUD);
            if (major && Math.abs(px - cx) > 22) {
                int label = Math.floorMod(deg, 360);
                d.text(String.format("%03d", label == 0 ? 360 : label), px, y + 2, HUD_DIM, Align.CENTER);
            }
        }
        d.line(cx - half, y - 8, cx + half, y - 8, HUD_DIM);
        int shown = (int) Math.round(heading) % 360;
        d.rect(cx - 17, y - 5, 34, 14, HUD_SHADOW);
        d.frame(cx - 17, y - 5, 34, 14, HUD);
        d.text(String.format("%03d", shown == 0 ? 360 : shown), cx, y + 2, HUD, Align.CENTER);
        d.triangle(cx, y - 8, cx - 4, y - 14, cx + 4, y - 14, HUD);
    }

    /**
     * Distância até a cabeceira da pista de destino (SIM.DEST_RUNWAY, ex.: "SBGR 10L"; sem destino, SBKP 15),
     * pela posição das centrais inerciais (some se elas falharem).
     */
    private void runwayDistance(Draw d, DataBus bus, float x, float y) {
        if (!bus.has(Labels.IR_POS_X) || bus.getBoolean(Labels.SIM_ON_GROUND)) {
            return;
        }
        Runway target = destination(bus);
        double nm = Math.hypot(bus.getDouble(Labels.IR_POS_X, 0) - target.xNm(),
                bus.getDouble(Labels.IR_POS_Y, 0) - target.yNm());
        if (nm > 60) {
            return;
        }
        d.text(String.format("%s  %.1f NM", target.id(), nm), x, y, HUD, Align.CENTER);
    }

    static Runway destination(DataBus bus) {
        String[] parts = bus.getString(Labels.SIM_DEST_RUNWAY, "").split(" ");
        return parts.length == 2 ? World.runway(parts[0], parts[1]).orElse(World.HOME) : World.HOME;
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
