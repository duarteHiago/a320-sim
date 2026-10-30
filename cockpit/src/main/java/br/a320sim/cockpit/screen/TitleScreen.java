package br.a320sim.cockpit.screen;

import br.a320sim.cockpit.SimApp;
import br.a320sim.cockpit.audio.Sfx;
import br.a320sim.cockpit.gfx.Draw;
import br.a320sim.cockpit.gfx.Draw.Align;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;

/** Tela de título: escolhe o papel deste PC na sessão. */
public class TitleScreen extends RetroScreen {

    private static final Color YELLOW = Color.valueOf("ffff48");
    private static final Color GREY = Color.valueOf("8a909a");
    private static final Color RED = Color.valueOf("ff3a30");

    private record Option(String title, String description, Runnable action) {
    }

    private final Option[] options;
    private final String error;
    private int selected;
    private float time;

    public TitleScreen(SimApp app) {
        super(app);
        options = new Option[]{
                new Option("SOLO", "tudo neste PC: voce monta o aviao e pilota", app::startSolo),
                new Option("INSTRUTOR", "este PC roda o aviao e comanda a sessao do aluno", app::startInstructor),
                new Option("ALUNO", "conectar ao PC do instrutor e pilotar", app::startStudent)};
        error = app.takeError();
    }

    @Override
    protected void update(float delta) {
        time += delta;
        if (Gdx.input.isKeyJustPressed(Keys.ESCAPE)) {
            Gdx.app.exit();
            return;
        }
        if (Gdx.input.isKeyJustPressed(Keys.UP) || Gdx.input.isKeyJustPressed(Keys.W)) {
            selected = (selected + options.length - 1) % options.length;
            app.sound().play(Sfx.CLICK);
        }
        if (Gdx.input.isKeyJustPressed(Keys.DOWN) || Gdx.input.isKeyJustPressed(Keys.S)) {
            selected = (selected + 1) % options.length;
            app.sound().play(Sfx.CLICK);
        }
        for (int i = 0; i < options.length; i++) {
            if (Gdx.input.isKeyJustPressed(Keys.NUM_1 + i)) {
                selected = i;
                options[i].action().run();
                return;
            }
        }
        if (Gdx.input.isKeyJustPressed(Keys.ENTER)) {
            app.sound().play(Sfx.BOOT);
            options[selected].action().run();
        }
    }

    @Override
    protected void paint(Draw d) {
        d.text("A320 SIM", WIDTH / 2f, 300, YELLOW, Align.CENTER, 4);
        d.text("simulador de cartuchos", WIDTH / 2f, 258, GREY, Align.CENTER);

        for (int i = 0; i < options.length; i++) {
            float y = 200 - i * 44;
            boolean active = i == selected;
            Color color = active ? Color.WHITE : GREY;
            if (active && (time % 0.8f) < 0.55f) {
                d.text(">", 170, y, YELLOW, Align.CENTER, 2);
            }
            d.text((i + 1) + "  " + options[i].title(), 195, y + 4, color, Align.LEFT, 2);
            d.text(options[i].description(), 225, y - 16, GREY, Align.LEFT);
        }
        if (error != null) {
            d.text(error, WIDTH / 2f, 40, RED, Align.CENTER);
        }
        d.text("SETAS + ENTER  ou  1/2/3      ESC: sair", WIDTH / 2f, 14, GREY, Align.CENTER);
    }

    @Override
    protected boolean scanlines() {
        return true;
    }
}
