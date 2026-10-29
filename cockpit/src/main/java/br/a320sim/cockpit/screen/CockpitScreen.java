package br.a320sim.cockpit.screen;

import br.a320sim.cockpit.SimApp;
import br.a320sim.cockpit.audio.AlertSounds;
import br.a320sim.cockpit.display.CockpitDisplay;
import br.a320sim.cockpit.display.EwdDisplay;
import br.a320sim.cockpit.display.NdDisplay;
import br.a320sim.cockpit.display.PfdDisplay;
import br.a320sim.cockpit.gfx.Draw;
import br.a320sim.cockpit.gfx.Theme;
import br.a320sim.cockpit.input.ControlInputs;
import br.a320sim.cockpit.input.InputDevice;
import br.a320sim.cockpit.input.KeyboardInput;
import br.a320sim.cockpit.panel.FcuPanel;
import br.a320sim.cockpit.panel.Glareshield;
import br.a320sim.cockpit.panel.Pedestal;
import br.a320sim.cockpit.visual.Windshield;
import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import br.a320sim.host.Host;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;

import java.util.List;
import java.util.stream.Collectors;

/** A cabine do aluno: lê os controles, roda o host e desenha as telas a partir do barramento. */
public class CockpitScreen extends RetroScreen {

    private static final float DISPLAY_SIZE = 206;
    private static final float DISPLAY_Y = 132;
    private static final float DISPLAY_GAP = 6;
    private static final Color BEZEL = Color.valueOf("3a3f48");
    private static final double REPOSITION_ALT = 8000;
    private static final double REPOSITION_IAS = 250;

    private final InputDevice input = new KeyboardInput();
    private final List<CockpitDisplay> displays = List.of(new PfdDisplay(), new NdDisplay(), new EwdDisplay());
    private final FcuPanel fcu = new FcuPanel();
    private final Pedestal pedestal = new Pedestal();
    private final Glareshield glareshield = new Glareshield();
    private final AlertSounds alerts = new AlertSounds();
    private final Windshield windshield = new Windshield();
    private final String rackText;
    private final InputAdapter scrollListener = new InputAdapter() {
        @Override
        public boolean scrolled(float amountX, float amountY) {
            scroll += amountY;
            return true;
        }
    };

    private ControlInputs controls = input.read();
    private float scroll;
    private float time;
    private boolean paused;
    private int discPushes;
    private int alignRequests;
    private boolean windowView = "janela".equals(System.getProperty("sim.view"));

    public CockpitScreen(SimApp app) {
        super(app);
        rackText = app.host().rack().slots().values().stream()
                .map(c -> c.manifest().partNumber())
                .collect(Collectors.joining("  "));
        if (System.getProperty("sim.airborne") != null) {
            reposition();
        }
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(scrollListener);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
        super.hide();
    }

    @Override
    protected void update(float delta) {
        time += delta;
        Host host = app.host();
        DataBus bus = host.bus();

        if (Gdx.input.isKeyJustPressed(Keys.ESCAPE)) {
            host.powerOff();
            app.setScreen(new RackScreen(app));
            return;
        }
        if (Gdx.input.isKeyJustPressed(Keys.P)) {
            paused = !paused;
            bus.publish(Labels.SIM_FREEZE, paused);
        }
        if (Gdx.input.isKeyJustPressed(Keys.R)) {
            host.flightModel().resetOnRunway();
        }
        if (Gdx.input.isKeyJustPressed(Keys.T)) {
            reposition();
        }
        if (Gdx.input.isKeyJustPressed(Keys.I)) {
            bus.publish(Labels.SIM_IR_INSTANT_ALIGN, (double) ++alignRequests);
        }
        if (Gdx.input.isKeyJustPressed(Keys.M)) {
            alerts.acknowledge();
        }
        if (Gdx.input.isKeyJustPressed(Keys.C)) {
            windowView = !windowView;
        }

        input.poll(delta);
        controls = input.read();
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

        host.advance(delta);
        for (CockpitDisplay display : displays) {
            display.update(bus, delta);
        }
        alerts.update(bus, delta, app.sound());
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

    /** Atalho de instrutor: põe o avião em voo nivelado e alinha os sensores na hora. */
    private void reposition() {
        DataBus bus = app.host().bus();
        double heading = bus.getDouble(Labels.TRUE_HDG, 0);
        app.host().flightModel().resetAirborne(REPOSITION_ALT, REPOSITION_IAS, heading);
        bus.publish(Labels.SIM_IR_INSTANT_ALIGN, (double) ++alignRequests);
    }

    @Override
    protected void paint(Draw d) {
        DataBus bus = app.host().bus();
        Theme theme = theme();

        glareshield.draw(d, alerts, time, rackText);
        if (windowView) {
            windshield.draw(d, bus, 0, DISPLAY_Y);
            fcu.draw(d, bus);
            pedestal.draw(d, bus, controls, paused);
            return;
        }
        for (int i = 0; i < displays.size(); i++) {
            float x = 5 + i * (DISPLAY_SIZE + DISPLAY_GAP);
            d.rect(x - 2, DISPLAY_Y - 2, DISPLAY_SIZE + 4, DISPLAY_SIZE + 4, BEZEL);
            if (theme == null) {
                d.rect(x, DISPLAY_Y, DISPLAY_SIZE, DISPLAY_SIZE, Color.BLACK);
            } else {
                displays.get(i).draw(d, bus, theme, x, DISPLAY_Y, DISPLAY_SIZE);
            }
        }
        fcu.draw(d, bus);
        pedestal.draw(d, bus, controls, paused);
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
        return Theme.forStyle(app.host().bus().has(Labels.DMC_STYLE)
                ? app.host().bus().getString(Labels.DMC_STYLE, null) : null);
    }
}
