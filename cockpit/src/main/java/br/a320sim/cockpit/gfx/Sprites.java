package br.a320sim.cockpit.gfx;

import br.a320sim.cockpit.gfx.Draw.Align;
import com.badlogic.gdx.graphics.Color;

/** Desenhos reaproveitados pelas telas: o A320 de perfil e o logo do simulador. */
public final class Sprites {

    private static final Color BODY = Color.valueOf("f2f4f8");
    private static final Color SHADE = Color.valueOf("b8bec8");
    private static final Color CHEAT = Color.valueOf("2a6ad8");
    private static final Color WINDOW = Color.valueOf("1c2430");
    private static final Color ENGINE = Color.valueOf("8a909c");
    private static final Color LOGO_TOP = Color.valueOf("ffe838");
    private static final Color LOGO_BOTTOM = Color.valueOf("ff8a1c");
    private static final Color LOGO_SHADOW = Color.valueOf("5a1a08");
    private static final Color SUBTITLE = Color.valueOf("38e8ff");

    private Sprites() {
    }

    /** A320 de perfil voando para a direita. (x, y) = nariz; {@code s} = tamanho de um "pixel" do desenho. */
    public static void airliner(Draw d, float x, float y, float s) {
        float tail = x - 46 * s;
        // Deriva e estabilizador
        d.triangle(tail, y + 2 * s, tail + 9 * s, y + 2 * s, tail, y + 13 * s, BODY);
        d.rect(tail + 1 * s, y + 6 * s, 2 * s, 5 * s, CHEAT);
        d.triangle(tail - 1 * s, y + 1 * s, tail + 8 * s, y + 1 * s, tail - 1 * s, y + 3 * s, SHADE);
        // Fuselagem, nariz e faixa
        d.rect(tail, y - 2 * s, 40 * s, 6 * s, BODY);
        d.triangle(tail + 40 * s, y + 4 * s, tail + 40 * s, y - 2 * s, x, y, BODY);
        d.rect(tail + 2 * s, y - 2 * s, 40 * s, 1 * s, SHADE);
        d.rect(tail + 4 * s, y + 0.5f * s, 36 * s, 1 * s, CHEAT);
        for (int i = 0; i < 12; i++) {
            d.rect(tail + (8 + i * 2.6f) * s, y + 2 * s, 1.2f * s, 1 * s, WINDOW);
        }
        d.rect(x - 5 * s, y + 1.5f * s, 2.5f * s, 1.2f * s, WINDOW);
        // Asa e motor
        d.triangle(tail + 16 * s, y - 2 * s, tail + 28 * s, y - 2 * s, tail + 21 * s, y - 6 * s, SHADE);
        d.rect(tail + 22 * s, y - 6 * s, 7 * s, 3 * s, ENGINE);
        d.rect(tail + 28 * s, y - 6 * s, 1 * s, 3 * s, WINDOW);
    }

    /** Logo "A320 / SIM": letras em degradê amarelo-laranja com contorno escuro, como nos fliperamas. */
    public static void logo(Draw d, float cx, float cy, int scale) {
        float width = Draw.textWidth("A320", scale);
        float x = cx - width / 2;
        outline(d, "A320", x, cy, scale, Math.max(1, scale / 2));
        d.text("A320", x, cy, LOGO_BOTTOM, Align.LEFT, scale);
        // Metade de cima em amarelo: recorte horizontal
        d.clip(x - scale, cy - scale / 2f, width + 2 * scale, 5 * scale);
        d.text("A320", x, cy, LOGO_TOP, Align.LEFT, scale);
        d.unclip();
        // Faixas de "velocidade" saindo da esquerda
        for (int i = 0; i < 3; i++) {
            float ly = cy - 2 * scale + i * 2 * scale;
            d.rect(x - (40 + i * 18) - scale * 2, ly, 34 + i * 10, Math.max(1, scale / 2f), LOGO_BOTTOM);
        }
        int small = Math.max(1, scale / 2);
        float simWidth = Draw.textWidth("S I M", small);
        outline(d, "S I M", cx - simWidth / 2, cy - 6.5f * scale, small, Math.max(1, small / 2));
        d.text("S I M", cx - simWidth / 2, cy - 6.5f * scale, SUBTITLE, Align.LEFT, small);
    }

    /** Contorno: o texto escuro deslocado nas 8 direções, por baixo da letra. */
    private static void outline(Draw d, String text, float x, float y, int scale, float thickness) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) {
                    d.text(text, x + dx * thickness, y + dy * thickness, LOGO_SHADOW, Align.LEFT, scale);
                }
            }
        }
        d.text(text, x + thickness, y - 2 * thickness, LOGO_SHADOW, Align.LEFT, scale);
    }
}
