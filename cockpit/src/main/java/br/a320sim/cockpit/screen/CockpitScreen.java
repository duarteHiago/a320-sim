package br.a320sim.cockpit.screen;

import br.a320sim.cockpit.SimApp;
import br.a320sim.cockpit.audio.AlertSounds;
import br.a320sim.cockpit.audio.Sfx;
import br.a320sim.cockpit.display.CockpitDisplay;
import br.a320sim.cockpit.display.EwdDisplay;
import br.a320sim.cockpit.display.NdDisplay;
import br.a320sim.cockpit.display.PfdDisplay;
import br.a320sim.cockpit.gfx.Draw;
import br.a320sim.cockpit.gfx.Draw.Align;
import br.a320sim.cockpit.gfx.Theme;
import br.a320sim.cockpit.input.ControlInputs;
import br.a320sim.cockpit.input.AutoInput;
import br.a320sim.cockpit.input.MenuInput;
import br.a320sim.cockpit.panel.FcuPanel;
import br.a320sim.cockpit.panel.Glareshield;
import br.a320sim.cockpit.panel.Pedestal;
import br.a320sim.cockpit.session.SimSession;
import br.a320sim.cockpit.visual.Windshield;
import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import br.a320sim.host.command.FinalApproachCommand;
import br.a320sim.host.command.FreezeCommand;
import br.a320sim.host.command.InstantAlignCommand;
import br.a320sim.host.command.RepositionCommand;
import br.a320sim.host.command.ResetToRunwayCommand;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;

import java.util.List;

/**
 * A cabine do aluno: lê os controles, avança a sessão e desenha as telas a partir do barramento.
 * Funciona igual no modo SOLO e no modo ALUNO; só muda a {@link SimSession}.
 */
public class CockpitScreen extends RetroScreen {

    private static final float DISPLAY_SIZE = 206;
    private static final float DISPLAY_Y = 132;
    private static final float DISPLAY_GAP = 6;
    private static final Color BEZEL = Color.valueOf("3a3f48");
    private static final Color BANNER = new Color(0, 0, 0, 0.8f);
    private static final Color CYAN = Color.valueOf("38e8ff");
    private static final Color AMBER = Color.valueOf("ffb020");
    private static final Color RED = Color.valueOf("ff3a30");
    private static final double REPOSITION_ALT = 8000;
    private static final double REPOSITION_IAS = 250;
    private static final float MESSAGE_SECONDS = 10;

    private final SimSession session;
    private final AutoInput input = new AutoInput();
    private final List<CockpitDisplay> displays = List.of(new PfdDisplay(), new NdDisplay(), new EwdDisplay());
    private final FcuPanel fcu = new FcuPanel();
    private final Pedestal pedestal = new Pedestal();
    private final Glareshield glareshield = new Glareshield();
    private final AlertSounds alerts = new AlertSounds();
    private final Windshield windshield = new Windshield();
    private final InputAdapter scrollListener = new InputAdapter() {
        @Override
        public boolean scrolled(float amountX, float amountY) {
            scroll += amountY;
            return true;
        }
    };

    private ControlInputs controls = input.read();
    private String inputNotice;
    private float inputNoticeTimer;
    private float scroll;
    private float time;
    private int discPushes;
    private boolean windowView = "janela".equals(System.getProperty("sim.view"));
    private double lastMessageId;
    private float messageTimer;

    public CockpitScreen(SimApp app, SimSession session) {
        super(app);
        this.session = session;
        lastMessageId = session.bus().getDouble(Labels.IOS_MESSAGE_ID, 0);
        if (System.getProperty("sim.airborne") != null) {
            session.execute(new RepositionCommand(REPOSITION_ALT, REPOSITION_IAS));
        }
        if (System.getProperty("sim.final") != null) {
            session.execute(new FinalApproachCommand());
        }
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(scrollListener);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
        input.close();
        app.sound().silenceAmbience();
        super.hide();
    }

    @Override
    protected void update(float delta) {
        time += delta;
        DataBus bus = session.bus();

        if (Gdx.input.isKeyJustPressed(Keys.ESCAPE) || MenuInput.exit()) {
            app.sound().play(Sfx.BACK);
            session.close();
            if (session.acceptsCommands()) {
                app.setScreen(new RackScreen(app));
            } else {
                app.showTitle();
            }
            return;
        }
        if (session.acceptsCommands()) {
            if (Gdx.input.isKeyJustPressed(Keys.P) || MenuInput.pause()) {
                session.execute(new FreezeCommand(!bus.getBoolean(Labels.SIM_FREEZE)));
            }
            if (Gdx.input.isKeyJustPressed(Keys.R)) {
                session.execute(new ResetToRunwayCommand());
            }
            if (Gdx.input.isKeyJustPressed(Keys.T)) {
                session.execute(new RepositionCommand(REPOSITION_ALT, REPOSITION_IAS));
            }
            if (Gdx.input.isKeyJustPressed(Keys.I)) {
                session.execute(new InstantAlignCommand());
            }
        }
        if (Gdx.input.isKeyJustPressed(Keys.M)) {
            alerts.acknowledge();
        }
        input.poll(delta);
        if (input.viewToggled() || (input.simplified() && Gdx.input.isKeyJustPressed(Keys.C))) {
            windowView = !windowView;
        }
        controls = input.read(bus);
        String notice = input.takeNotice();
        if (notice != null) {
            inputNotice = notice;
            inputNoticeTimer = 4;
            app.sound().play(Sfx.CHIME);
        }
        inputNoticeTimer = Math.max(0, inputNoticeTimer - delta);
        if (controls.apDisconnect()) {
            discPushes++;
        }
        publishControls(bus);

        Vector2 mouse = mouse();
        boolean clicked = Gdx.input.isButtonJustPressed(Input.Buttons.LEFT);
        if (clicked && glareshield.contains(mouse)) {
            alerts.acknowledge();
        }
        boolean coarse = Gdx.input.isKeyPressed(Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Keys.SHIFT_RIGHT);
        fcu.handle(mouse, scroll, clicked, coarse, app.sound());
        scroll = 0;
        fcu.publish(bus);

        session.update(delta);
        for (CockpitDisplay display : displays) {
            display.update(bus, delta);
        }
        alerts.update(bus, delta, app.sound());
        cabinSound(bus);
        checkInstructorMessage(bus, delta);
    }

    /** Ronco dos motores, vento e trepidação das rodas, a partir do que o avião está fazendo. */
    private void cabinSound(DataBus bus) {
        if (bus.getBoolean(Labels.SIM_CRASHED)) {
            app.sound().silenceAmbience();
            return;
        }
        double n1 = (bus.getDouble(Labels.ENG1_N1, 0) + bus.getDouble(Labels.ENG2_N1, 0)) / 200;
        double ias = bus.getDouble(Labels.TRUE_IAS, 0);
        double wind = Math.pow(Math.min(1, ias / 330), 2);
        double rumble = bus.getBoolean(Labels.SIM_ON_GROUND) ? Math.min(1, bus.getDouble(Labels.TRUE_GS, 0) / 110) : 0;
        app.sound().ambience(n1, wind, rumble);
    }

    private void publishControls(DataBus bus) {
        bus.publish(Labels.CTL_PITCH, controls.pitch());
        bus.publish(Labels.CTL_ROLL, controls.roll());
        bus.publish(Labels.CTL_YAW, controls.yaw());
        bus.publish(Labels.CTL_BRAKES, controls.brakes());
        bus.publish(Labels.CTL_FLAPS, (double) controls.flaps());
        bus.publish(Labels.CTL_GEAR_DOWN, controls.gearDown());
        bus.publish(Labels.SIM_THRUST_LEVER, controls.thrustLever());
        bus.publish(Labels.PANEL_AP_DISC, (double) discPushes);
    }

    private void checkInstructorMessage(DataBus bus, float delta) {
        double id = bus.getDouble(Labels.IOS_MESSAGE_ID, 0);
        if (id != lastMessageId) {
            lastMessageId = id;
            messageTimer = MESSAGE_SECONDS;
            app.sound().play(Sfx.CHIME);
        }
        messageTimer = Math.max(0, messageTimer - delta);
    }

    @Override
    protected void paint(Draw d) {
        DataBus bus = session.bus();
        Theme theme = theme();

        glareshield.draw(d, alerts, time, session.rackText());
        if (windowView) {
            windshield.draw(d, bus, 0, DISPLAY_Y);
        } else {
            for (int i = 0; i < displays.size(); i++) {
                float x = 5 + i * (DISPLAY_SIZE + DISPLAY_GAP);
                d.rect(x - 2, DISPLAY_Y - 2, DISPLAY_SIZE + 4, DISPLAY_SIZE + 4, BEZEL);
                if (theme == null) {
                    d.rect(x, DISPLAY_Y, DISPLAY_SIZE, DISPLAY_SIZE, Color.BLACK);
                } else {
                    displays.get(i).draw(d, bus, theme, x, DISPLAY_Y, DISPLAY_SIZE);
                }
            }
        }
        fcu.draw(d, bus);
        pedestal.draw(d, bus, controls, bus.getBoolean(Labels.SIM_FREEZE), input.simplified());
        if (inputNoticeTimer > 0) {
            banner(d, inputNotice, CYAN, 270);
        }

        if (messageTimer > 0) {
            banner(d, "INSTRUTOR: " + bus.getString(Labels.IOS_MESSAGE, ""), CYAN, 310);
        }
        String status = session.status();
        if (status != null) {
            banner(d, status, AMBER, 236);
        }
        List<String> missing = bus.getList(Labels.RACK_MISSING);
        if (!missing.isEmpty() && (time % 1f) < 0.7f) {
            banner(d, "CARTUCHO REMOVIDO: " + String.join("  ", missing), RED, 196);
        }
    }

    private void banner(Draw d, String text, Color color, float y) {
        d.rect(20, y - 13, WIDTH - 40, 26, BANNER);
        d.frame(20, y - 13, WIDTH - 40, 26, color);
        d.text(text, WIDTH / 2f, y, color, Align.CENTER);
    }

    @Override
    public void dispose() {
        windshield.dispose();
        super.dispose();
    }

    @Override
    protected boolean scanlines() {
        Theme theme = theme();
        return theme != null && theme.scanlines();
    }

    private Theme theme() {
        DataBus bus = session.bus();
        return Theme.forStyle(bus.has(Labels.DMC_STYLE) ? bus.getString(Labels.DMC_STYLE, null) : null);
    }
}
