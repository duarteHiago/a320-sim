package br.a320sim.cockpit.screen;

import br.a320sim.cockpit.SimApp;
import br.a320sim.cockpit.audio.Sfx;
import br.a320sim.cockpit.gfx.Draw;
import br.a320sim.cockpit.gfx.Draw.Align;
import br.a320sim.cockpit.gfx.Sprites;
import br.a320sim.cockpit.input.MenuInput;
import br.a320sim.cockpit.visual.OutsideView;
import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import br.a320sim.flightmodel.FlightModel;
import br.a320sim.host.cartridge.DetectedCartridge;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;

import java.util.List;

/**
 * Tela de título: escolhe o papel deste PC na sessão. Ao fundo roda o "modo demonstração":
 * o próprio sistema visual voando uma aproximação para a pista 15, em loop.
 */
public class TitleScreen extends RetroScreen {

    private static final Color YELLOW = Color.valueOf("ffff48");
    private static final Color GREY = Color.valueOf("aab0ba");
    private static final Color DIM = Color.valueOf("6c7380");
    private static final Color RED = Color.valueOf("ff3a30");
    private static final Color GREEN = Color.valueOf("38ff6a");
    private static final Color SKY_TOP = Color.valueOf("0c1838");
    private static final Color SKY_LOW = Color.valueOf("82a2d4");
    private static final Color PANEL = new Color(0.02f, 0.03f, 0.06f, 0.72f);
    private static final Color PANEL_EDGE = Color.valueOf("38e8ff");
    private static final float VIEW_H = OutsideView.HEIGHT * 2;
    /** Demonstração: começa a 6 NM, desce na rampa de 3° e recomeça perto da cabeceira. */
    private static final double DEMO_START_NM = 6;
    private static final double DEMO_SPEED_NM_S = 0.11;

    private record Option(String title, String description, Runnable action) {
    }

    private final Option[] options;
    private final String error;
    private final OutsideView view = new OutsideView();
    private final DataBus demo = new DataBus();
    private int selected;
    private float time;
    /** Sair pede confirmação: ESC/B uma vez arma, de novo em 2 s sai. */
    private float exitArmed;

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
        updateDemo();
        exitArmed = Math.max(0, exitArmed - delta);
        if (MenuInput.back()) {
            if (exitArmed > 0 && time > 0.5f) {
                Gdx.app.exit();
                return;
            }
            exitArmed = 2;
            app.sound().play(Sfx.BACK);
        }
        if (MenuInput.up()) {
            selected = (selected + options.length - 1) % options.length;
            app.sound().play(Sfx.MOVE);
        }
        if (MenuInput.down()) {
            selected = (selected + 1) % options.length;
            app.sound().play(Sfx.MOVE);
        }
        for (int i = 0; i < options.length; i++) {
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
        app.sound().play(Sfx.SELECT);
        options[selected].action().run();
    }

    /** Câmera da demonstração: aproximação com um leve zigue-zague, para dar vida. */
    private void updateDemo() {
        double cycle = DEMO_START_NM / DEMO_SPEED_NM_S;
        double t = time % cycle;
        double distance = DEMO_START_NM - t * DEMO_SPEED_NM_S;
        double rwy = Math.toRadians(FlightModel.RUNWAY_HEADING);
        double weave = Math.sin(time * 0.35) * 0.05 * distance / DEMO_START_NM;
        double x = -Math.sin(rwy) * distance + Math.cos(rwy) * weave;
        double y = -Math.cos(rwy) * distance - Math.sin(rwy) * weave;
        double heightFt = distance * 6076 * Math.tan(Math.toRadians(3)) + 50;
        demo.publish(Labels.TRUE_POS_X, x);
        demo.publish(Labels.TRUE_POS_Y, y);
        demo.publish(Labels.SIM_RADIO_ALT, heightFt);
        demo.publish(Labels.TRUE_HDG, FlightModel.RUNWAY_HEADING + Math.cos(time * 0.35) * 2);
        demo.publish(Labels.TRUE_PITCH, 0.5);
        demo.publish(Labels.TRUE_ROLL, -Math.cos(time * 0.35) * 5);
        demo.publish(Labels.SIM_TIME, (double) time);
    }

    @Override
    protected void paint(Draw d) {
        // Céu em degradê emendando com a vista ao vivo na parte de baixo
        int bands = 24;
        float top = HEIGHT - VIEW_H;
        for (int i = 0; i < bands; i++) {
            Color c = new Color(SKY_TOP).lerp(SKY_LOW, (float) i / (bands - 1));
            d.rect(0, HEIGHT - (i + 1) * top / bands, WIDTH, top / bands + 1, c);
        }
        d.image(view.render(demo), 0, 0, WIDTH, VIEW_H);
        double cycle = DEMO_START_NM / DEMO_SPEED_NM_S;
        float sinceStart = (float) (time % cycle);
        float fade = Math.max(0, Math.max(1 - sinceStart / 0.6f, (float) ((sinceStart - (cycle - 0.8)) / 0.8)));
        if (fade > 0) {
            d.rect(0, 0, WIDTH, VIEW_H, new Color(0, 0, 0, fade));
        }

        Sprites.logo(d, WIDTH / 2f, 304, 5);

        // Menu num painel translúcido sobre a vista
        float panelX = 150, panelY = 70, panelW = 340, panelH = 150;
        d.rect(panelX, panelY, panelW, panelH, PANEL);
        d.frame(panelX, panelY, panelW, panelH, PANEL_EDGE);
        for (int i = 0; i < options.length; i++) {
            float y = panelY + panelH - 28 - i * 44;
            boolean active = i == selected;
            if (active) {
                d.rect(panelX + 6, y - 22, panelW - 12, 40, new Color(1, 1, 1, 0.08f));
                if ((time % 0.8f) < 0.55f) {
                    d.text(">", panelX + 20, y + 2, YELLOW, Align.CENTER, 2);
                }
            }
            d.text((i + 1) + " " + options[i].title(), panelX + 38, y + 2, active ? Color.WHITE : GREY, Align.LEFT, 2);
            d.text(options[i].description(), panelX + 38, y - 14, active ? GREY : DIM, Align.LEFT);
        }

        d.rect(0, 0, WIDTH, 64, new Color(0, 0, 0, 0.55f));
        cartridgeStatus(d, 52);
        if (error != null) {
            d.text(error, WIDTH / 2f, 36, RED, Align.CENTER);
        }
        String help = MenuInput.hasController() ? "DIRECIONAL + A     B: sair" : "SETAS + ENTER  ou  1/2/3     ESC: sair";
        if (exitArmed > 0) {
            help = MenuInput.hasController() ? "APERTE B DE NOVO PARA SAIR" : "APERTE ESC DE NOVO PARA SAIR";
        }
        d.text(help, WIDTH / 2f, 12, exitArmed > 0 ? YELLOW : Color.WHITE, Align.CENTER);
    }

    /** Linha com os cartuchos que estão nos leitores, ou o aviso piscando para inserir. */
    private void cartridgeStatus(Draw d, float y) {
        List<DetectedCartridge> found = app.library().available();
        List<String> cards = found.stream().filter(DetectedCartridge::removable).map(DetectedCartridge::partNumber).toList();
        if (cards.isEmpty()) {
            if ((time % 1f) < 0.6f) {
                d.text("INSIRA OS CARTUCHOS", WIDTH / 2f, y, YELLOW, Align.CENTER);
            }
            d.text(found.isEmpty() ? "nenhum cartucho" : "(sem cartao SD: usando as pastas sd-dev)", WIDTH / 2f, y - 13,
                    Color.WHITE, Align.CENTER);
        } else {
            d.text("CARTUCHOS: " + String.join("  ", cards), WIDTH / 2f, y, GREEN, Align.CENTER);
        }
    }

    @Override
    public void dispose() {
        view.dispose();
        super.dispose();
    }

    @Override
    protected boolean scanlines() {
        return true;
    }
}
