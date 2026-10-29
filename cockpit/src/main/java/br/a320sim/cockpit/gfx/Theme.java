package br.a320sim.cockpit.gfx;

import com.badlogic.gdx.graphics.Color;

/** Paleta das telas. Cada cartucho de Telas (DMC) escolhe a sua. */
public record Theme(
        Color background,
        Color tape,
        Color sky,
        Color ground,
        Color white,
        Color green,
        Color amber,
        Color red,
        Color cyan,
        Color magenta,
        Color yellow,
        boolean scanlines) {

    /** Tubo de raios catódicos: cores saturadas, brilho esverdeado e linhas de varredura. */
    public static final Theme CRT = new Theme(
            Color.valueOf("040a08"), Color.valueOf("26302c"),
            Color.valueOf("1f78e8"), Color.valueOf("a85c1c"),
            Color.valueOf("e4ffe8"), Color.valueOf("38ff6a"), Color.valueOf("ffb020"),
            Color.valueOf("ff3a30"), Color.valueOf("38e8ff"), Color.valueOf("ff58ff"),
            Color.valueOf("ffff48"), true);

    /** Tela de cristal líquido: cores mais sóbrias e imagem limpa. */
    public static final Theme LCD = new Theme(
            Color.valueOf("0a0e14"), Color.valueOf("2a2f38"),
            Color.valueOf("2f6fb8"), Color.valueOf("7a4a22"),
            Color.valueOf("f0f0f0"), Color.valueOf("22d455"), Color.valueOf("f0a020"),
            Color.valueOf("e83030"), Color.valueOf("30c8f0"), Color.valueOf("e050e0"),
            Color.valueOf("f0e030"), false);

    /** @return o tema do estilo publicado pelo DMC, ou null se não há DMC funcionando */
    public static Theme forStyle(String style) {
        if (style == null) {
            return null;
        }
        return switch (style) {
            case "CRT" -> CRT;
            case "LCD" -> LCD;
            default -> null;
        };
    }
}
