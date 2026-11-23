package br.a320sim.cockpit.screen;

import br.a320sim.cockpit.SimApp;
import br.a320sim.cockpit.audio.Sfx;
import br.a320sim.cockpit.gfx.Draw;
import br.a320sim.cockpit.gfx.Draw.Align;
import br.a320sim.cockpit.gfx.Sprites;
import br.a320sim.cockpit.input.MenuInput;
import com.badlogic.gdx.graphics.Color;

/**
 * Abertura à moda dos consoles: o logo cai com um "whoosh", toca a vinheta, um jato cruza a tela
 * deixando a trilha e aparece "APERTE START". Qualquer tecla ou botão pula.
 */
public class IntroScreen extends RetroScreen {

    private static final Color GREY = Color.valueOf("8a909a");
    private static final Color DIM = Color.valueOf("4a505a");
    private static final Color TRAIL = new Color(1, 1, 1, 0.35f);
    private static final float LOGO_DROP = 0.45f;
    private static final float LOGO_LAND = 1.05f;
    private static final float AUTO_ADVANCE = 7f;

    private float time;
    private boolean whoosh;
    private boolean jingle;

    public IntroScreen(SimApp app) {
        super(app);
    }

    @Override
    protected void update(float delta) {
        time += delta;
        if (!whoosh && time >= LOGO_DROP) {
            whoosh = true;
            app.sound().play(Sfx.WHOOSH);
        }
        if (!jingle && time >= LOGO_LAND) {
            jingle = true;
            app.sound().play(Sfx.JINGLE);
        }
        if ((time > 0.3f && MenuInput.any()) || time > AUTO_ADVANCE) {
            app.sound().play(Sfx.SELECT);
            app.showTitle();
        }
    }

    @Override
    protected void paint(Draw d) {
        float logoY = 236;
        if (time < LOGO_DROP) {
            return;
        }
        // Logo caindo do alto, com um quique no final
        float t = Math.min(1, (time - LOGO_DROP) / (LOGO_LAND - LOGO_DROP));
        float eased = 1 - (1 - t) * (1 - t);
        float bounce = t >= 1 ? (float) (Math.exp(-(time - LOGO_LAND) * 7) * Math.sin((time - LOGO_LAND) * 30) * 6) : 0;
        Sprites.logo(d, WIDTH / 2f, logoY + (1 - eased) * 200 + bounce, 7);

        // Clarão no instante em que o logo assenta
        float flash = time - LOGO_LAND;
        if (flash > 0 && flash < 0.18f) {
            d.rect(0, 0, WIDTH, HEIGHT, new Color(1, 1, 1, 0.6f * (1 - flash / 0.18f)));
        }

        // Jato cruzando a tela com a trilha de condensação
        float planeT = (time - 1.2f) / 2.2f;
        if (planeT > 0 && planeT < 1.4f) {
            float px = -60 + planeT * (WIDTH + 140);
            float py = 132 + planeT * 18;
            for (float tx = -60; tx < px - 50; tx += 4) {
                float ty = 132 + ((tx + 60) / (WIDTH + 140)) * 18 + 3;
                d.rect(tx, ty, 3, 1, TRAIL);
            }
            Sprites.airliner(d, px, py, 1.6f);
        }

        if (time > LOGO_LAND) {
            d.text("AMBIENTE VIRTUAL DE INTEGRACAO E OPERACAO DE NAVEGACAO", WIDTH / 2f, 166, GREY, Align.CENTER);
        }
        if (time > 1.9f) {
            d.text("CARTRIDGE AVIONICS SYSTEM", WIDTH / 2f, 98, GREY, Align.CENTER);
            d.text("3 slots  -  6 cartuchos  -  instrutor e aluno", WIDTH / 2f, 82, DIM, Align.CENTER);
        }
        if (time > 2.8f && (time % 1f) < 0.6f) {
            d.text(MenuInput.hasController() ? "APERTE START" : "APERTE ENTER", WIDTH / 2f, 46, Color.WHITE,
                    Align.CENTER, 2);
        }
        d.text("(c) 2026  AVION", WIDTH / 2f, 12, DIM, Align.CENTER);
    }

    @Override
    protected boolean scanlines() {
        return true;
    }
}
