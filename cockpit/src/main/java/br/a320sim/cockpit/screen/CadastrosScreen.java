package br.a320sim.cockpit.screen;

import br.a320sim.cockpit.SimApp;
import br.a320sim.cockpit.audio.Sfx;
import br.a320sim.cockpit.gfx.Draw;
import br.a320sim.cockpit.gfx.Draw.Align;
import br.a320sim.cockpit.input.MenuInput;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;

/** Menu dos cadastros (opção 4 da tela de título). Cada item abre a mesma {@link CrudScreen}. */
public class CadastrosScreen extends RetroScreen {

    private static final Color YELLOW = Color.valueOf("ffff48");
    private static final Color GREY = Color.valueOf("aab0ba");
    private static final Color DIM = Color.valueOf("6c7380");
    private static final Color RED = Color.valueOf("ff3a30");
    private static final Color PANEL = Color.valueOf("15181d");
    private static final Color EDGE = Color.valueOf("38e8ff");

    private record Option(String key, String title, String description) {
    }

    private static final Option[] OPTIONS = {
            new Option("pessoas", "PESSOAS", "alunos e instrutores"),
            new Option("cartoes", "CARTOES", "cartoes SD fisicos: etiqueta, status, leituras"),
            new Option("posicoes", "POSICOES INICIAIS", "onde o aviao comeca a sessao"),
            new Option("sessoes", "SESSOES DE TREINO", "historico, eventos, nota do instrutor")};

    private final boolean hasDatabase;
    private int selected;
    private float time;

    public CadastrosScreen(SimApp app) {
        super(app);
        hasDatabase = app.banco().abrir().isPresent();
    }

    @Override
    protected void update(float delta) {
        time += delta;
        if (MenuInput.back()) {
            app.sound().play(Sfx.BACK);
            app.showTitle();
            return;
        }
        if (MenuInput.up()) {
            selected = (selected + OPTIONS.length - 1) % OPTIONS.length;
            app.sound().play(Sfx.MOVE);
        }
        if (MenuInput.down()) {
            selected = (selected + 1) % OPTIONS.length;
            app.sound().play(Sfx.MOVE);
        }
        for (int i = 0; i < OPTIONS.length; i++) {
            if (Gdx.input.isKeyJustPressed(Keys.NUM_1 + i)) {
                selected = i;
                choose();
                return;
            }
        }
        if (MenuInput.confirm()) {
            choose();
        }
    }

    private void choose() {
        if (!hasDatabase) {
            app.sound().play(Sfx.ERROR);
            return;
        }
        app.sound().play(Sfx.SELECT);
        app.showCadastro(OPTIONS[selected].key());
    }

    @Override
    protected void paint(Draw d) {
        d.text("CADASTROS", WIDTH / 2f, 318, YELLOW, Align.CENTER, 2);
        float panelX = 150, panelY = 92, panelW = 340, panelH = 188;
        d.rect(panelX, panelY, panelW, panelH, PANEL);
        d.frame(panelX, panelY, panelW, panelH, EDGE);
        for (int i = 0; i < OPTIONS.length; i++) {
            float y = panelY + panelH - 28 - i * 44;
            boolean active = i == selected;
            if (active) {
                d.rect(panelX + 6, y - 22, panelW - 12, 40, new Color(1, 1, 1, 0.08f));
                if ((time % 0.8f) < 0.55f) {
                    d.text(">", panelX + 20, y + 2, YELLOW, Align.CENTER, 2);
                }
            }
            Color title = !hasDatabase ? DIM : active ? Color.WHITE : GREY;
            d.text((i + 1) + " " + OPTIONS[i].title(), panelX + 38, y + 2, title, Align.LEFT, 2);
            d.text(OPTIONS[i].description(), panelX + 38, y - 14, active ? GREY : DIM, Align.LEFT);
        }
        if (!hasDatabase) {
            d.text("SEM BANCO", WIDTH / 2f, 66, RED, Align.CENTER, 2);
            String why = app.banco().erro();
            if (why != null) {
                d.text(why.length() > 100 ? why.substring(0, 98) + ".." : why, WIDTH / 2f, 46, RED, Align.CENTER);
            }
        } else {
            d.text("banco: " + app.bancoCaminho(), WIDTH / 2f, 66, DIM, Align.CENTER);
        }
        d.text("SETAS + ENTER  ou  1/2/3/4     ESC: voltar", WIDTH / 2f, 12, Color.WHITE, Align.CENTER);
    }

    @Override
    protected boolean scanlines() {
        return true;
    }
}
