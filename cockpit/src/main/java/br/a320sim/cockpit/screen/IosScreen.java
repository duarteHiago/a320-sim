package br.a320sim.cockpit.screen;

import br.a320sim.cockpit.SimApp;
import br.a320sim.cockpit.gfx.Draw;
import br.a320sim.cockpit.gfx.Draw.Align;
import br.a320sim.cockpit.gfx.RetroButton;
import br.a320sim.cockpit.gfx.TextInput;
import br.a320sim.cockpit.visual.Windshield;
import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import br.a320sim.flightmodel.world.Airport;
import br.a320sim.flightmodel.world.Runway;
import br.a320sim.flightmodel.world.World;
import br.a320sim.host.Host;
import br.a320sim.cockpit.audio.Sfx;
import br.a320sim.dados.DadosException;
import br.a320sim.dados.modelo.Posicao;
import br.a320sim.host.command.ApplyPositionCommand;
import br.a320sim.host.command.FreezeCommand;
import br.a320sim.host.command.InstantAlignCommand;
import br.a320sim.host.command.ResetToRunwayCommand;
import br.a320sim.host.command.ReleaseBirdsCommand;
import br.a320sim.host.command.SetAutoBirdsCommand;
import br.a320sim.host.command.SetWeatherCommand;
import br.a320sim.host.command.SendMessageCommand;
import br.a320sim.host.command.ToggleFailureCommand;
import br.a320sim.host.failure.AutopilotFailure;
import br.a320sim.host.failure.EngineFailure;
import br.a320sim.host.failure.Failure;
import br.a320sim.host.failure.SensorFailure;
import br.a320sim.net.Message;
import br.a320sim.net.SimServer;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Estação do instrutor (IOS): roda o host, manda o barramento para o aluno,
 * mostra onde ele está e o que está fazendo, e oferece os comandos da sessão.
 */
public class IosScreen extends RetroScreen {

    private static final Color PANEL = Color.valueOf("15181d");
    private static final Color LINE = Color.valueOf("3a404a");
    private static final Color GREY = Color.valueOf("8a909a");
    private static final Color WHITE = Color.valueOf("e8ecf2");
    private static final Color GREEN = Color.valueOf("38ff6a");
    private static final Color AMBER = Color.valueOf("ffb020");
    private static final Color RED = Color.valueOf("ff3a30");
    private static final Color YELLOW = Color.valueOf("ffff48");
    private static final Color MAGENTA = Color.valueOf("ff58ff");

    private static final float MAP_X = 6;
    private static final float MAP_Y = 64;
    private static final float MAP_W = 324;
    private static final float MAP_H = 270;
    private static final float RANGE_NM = 15;
    private static final float SNAPSHOT_INTERVAL = 1 / 30f;
    private static final double TRAIL_SECONDS = 2;
    private static final int TRAIL_POINTS = 200;

    private final List<RetroButton> buttons = new ArrayList<>();
    private final Deque<double[]> trail = new ArrayDeque<>();
    private final TextInput message = new TextInput("", 60);
    private final Rectangle messageField = new Rectangle(6, 6, 628, 22);
    private final String addresses;
    /** Posições cadastradas; o botão POS alterna entre elas e IR P/ POSICAO aplica. */
    private final List<Posicao> positions = new ArrayList<>();
    private final boolean hasDatabase;
    private int position;
    /** Vento atual, escolhido entre as predefinições do botão VENTO. */
    private SetWeatherCommand weather = SetWeatherCommand.CALM;
    private float snapshotTimer;
    private double trailTimer;
    private float time;

    public IosScreen(SimApp app) {
        super(app);
        Host host = app.host();
        addresses = SimServer.localAddresses().stream().limit(2).collect(Collectors.joining("  "));

        float y = 172;
        for (Failure failure : new Failure[]{new EngineFailure(1), new SensorFailure(), new AutopilotFailure()}) {
            buttons.add(new RetroButton(338, y, 296, 20, failure::name, () -> host.hasFailure(failure), RED,
                    () -> host.execute(new ToggleFailureCommand(failure))));
            y -= 24;
        }
        // Ambiente: vento (as predefinições valem para a pista de destino); começa com o que já está valendo
        DataBus bus = host.bus();
        weather = SetWeatherCommand.presets(Windshield.destination(bus)).stream()
                .filter(w -> w.speedKt() == bus.getDouble(Labels.SIM_WIND_SPD, 0)
                        && w.fromDeg() == bus.getDouble(Labels.SIM_WIND_DIR, 0))
                .findFirst().orElse(SetWeatherCommand.CALM);
        buttons.add(new RetroButton(338, 98, 140, 20, () -> "VENTO: " + weather.name(),
                () -> weather.speedKt() > 0, AMBER, this::nextWeather));
        // Aves: um bando à frente agora, ou bandos ao acaso perto dos aeroportos (desligado no começo)
        buttons.add(new RetroButton(484, 98, 66, 20, () -> "AVES", () -> bus.getBoolean(Labels.SIM_BIRDS), AMBER,
                () -> host.execute(new ReleaseBirdsCommand())));
        buttons.add(new RetroButton(556, 98, 78, 20, () -> "AVES AUTO", () -> bus.getBoolean(Labels.SIM_BIRDS_AUTO),
                AMBER, () -> host.execute(new SetAutoBirdsCommand(!bus.getBoolean(Labels.SIM_BIRDS_AUTO)))));

        hasDatabase = loadPositions();
        buttons.add(new RetroButton(6, 34, 100, 22,
                () -> host.bus().getBoolean(Labels.SIM_FREEZE) ? "CONTINUAR" : "PAUSAR",
                () -> host.bus().getBoolean(Labels.SIM_FREEZE), YELLOW,
                () -> host.execute(new FreezeCommand(!host.bus().getBoolean(Labels.SIM_FREEZE)))));
        buttons.add(new RetroButton(112, 34, 74, 22, "PISTA", () -> host.execute(new ResetToRunwayCommand())));
        buttons.add(new RetroButton(192, 34, 138, 22, this::positionLabel, () -> false, Color.WHITE,
                this::nextPosition));
        buttons.add(new RetroButton(336, 34, 96, 22, "IR P/ POSICAO", this::goToPosition));
        buttons.add(new RetroButton(438, 34, 96, 22, "ALINHAR IR", () -> host.execute(new InstantAlignCommand())));
        buttons.add(new RetroButton(540, 34, 94, 22, "CARTUCHOS", this::backToRack));
    }

    /** Lê as posições do cadastro e começa na escolhida na NOVA SESSAO. */
    private boolean loadPositions() {
        var dados = app.banco().dados();
        if (dados.isEmpty()) {
            return false;
        }
        try {
            positions.addAll(dados.get().posicoes().listar());
        } catch (DadosException e) {
            System.err.println("[IOS] " + e.getMessage());
            return false;
        }
        Posicao chosen = app.lastPlan().posicao();
        for (int i = 0; chosen != null && i < positions.size(); i++) {
            if (positions.get(i).id().equals(chosen.id())) {
                position = i;
            }
        }
        return true;
    }

    /** Próxima predefinição de vento, calculada para a pista de destino de agora. */
    private void nextWeather() {
        Host host = app.host();
        List<SetWeatherCommand> presets = SetWeatherCommand.presets(Windshield.destination(host.bus()));
        int current = presets.stream().map(SetWeatherCommand::name).toList().indexOf(weather.name());
        weather = presets.get((current + 1) % presets.size());
        host.execute(weather);
    }

    private String positionLabel() {
        return positions.isEmpty() ? "POS: SEM BANCO" : "POS: " + positions.get(position).nome();
    }

    private void nextPosition() {
        if (!positions.isEmpty()) {
            position = (position + 1) % positions.size();
            app.sound().play(Sfx.MOVE);
        }
    }

    private void goToPosition() {
        if (positions.isEmpty()) {
            app.sound().play(Sfx.ERROR);
            return;
        }
        Posicao p = positions.get(position);
        app.host().execute(new ApplyPositionCommand(p.nome(), p.altitudeFt(), p.velocidadeKt(), p.proaGraus(),
                p.distanciaNm(), p.destino(), p.pista()));
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(message);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
        super.hide();
    }

    private void backToRack() {
        app.showRack();
    }

    @Override
    protected void update(float delta) {
        time += delta;
        Host host = app.host();
        DataBus bus = host.bus();
        SimServer server = app.server();

        if (server != null) {
            server.pollControls().ifPresent(values -> values.forEach((label, value) -> {
                if (Labels.isPilotInput(label)) {
                    bus.publish(label, value);
                }
            }));
        }
        bus.publish(Labels.IOS_RACK, rackText(host));
        host.advance(delta);
        snapshotTimer -= delta;
        if (server != null && snapshotTimer <= 0) {
            snapshotTimer = SNAPSHOT_INTERVAL;
            server.send(new Message.BusSnapshot(bus.snapshot()));
        }
        updateTrail(bus, delta);

        if (message.isActive()) {
            if (Gdx.input.isKeyJustPressed(Keys.ENTER)) {
                if (!message.text().isBlank()) {
                    host.execute(new SendMessageCommand(message.text().trim()));
                }
                message.clear();
                message.setActive(false);
            } else if (Gdx.input.isKeyJustPressed(Keys.ESCAPE)) {
                message.setActive(false);
            }
            return;
        }
        if (Gdx.input.isKeyJustPressed(Keys.ESCAPE)) {
            backToRack();
            return;
        }
        if (Gdx.input.isKeyJustPressed(Keys.ENTER)) {
            message.setActive(true);
        }
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            Vector2 mouse = mouse();
            if (messageField.contains(mouse)) {
                message.setActive(true);
            }
            for (RetroButton button : List.copyOf(buttons)) {
                if (button.click(mouse)) {
                    break;
                }
            }
        }
    }

    private void updateTrail(DataBus bus, double dt) {
        double[] here = {bus.getDouble(Labels.TRUE_POS_X, 0), bus.getDouble(Labels.TRUE_POS_Y, 0)};
        double[] last = trail.peekLast();
        if (last != null && Math.hypot(here[0] - last[0], here[1] - last[1]) > 3) {
            trail.clear();
        }
        trailTimer -= dt;
        if (trailTimer <= 0) {
            trailTimer = TRAIL_SECONDS;
            trail.addLast(here);
            if (trail.size() > TRAIL_POINTS) {
                trail.removeFirst();
            }
        }
    }

    @Override
    protected void paint(Draw d) {
        DataBus bus = app.host().bus();
        Vector2 mouse = mouse();

        header(d);
        map(d, bus);
        mirror(d, bus);
        d.text("FALHAS  (clique para ligar/desligar)", 338, 198, GREY, Align.LEFT);
        for (RetroButton button : buttons) {
            button.draw(d, mouse);
        }
        log(d);

        d.rect(messageField.x, messageField.y, messageField.width, messageField.height, PANEL);
        d.frame(messageField.x, messageField.y, messageField.width, messageField.height,
                message.isActive() ? GREEN : LINE);
        String cursor = message.isActive() && (time % 0.8f) < 0.5f ? "_" : "";
        String hint = message.isActive() ? "" : "  (ENTER ou clique para escrever ao aluno)";
        d.text("MSG> " + message.text() + cursor + hint, 12, 17, message.isActive() ? GREEN : GREY, Align.LEFT);
    }

    private void header(Draw d) {
        d.text("IOS", 6, 350, YELLOW, Align.LEFT);
        if (!hasDatabase) {
            d.text("SEM BANCO", 36, 350, RED, Align.LEFT);
        } else if (app.sessions().recording()) {
            d.text("SESSAO: " + app.lastPlan().aluno().nome(), 36, 350, GREEN, Align.LEFT);
        }
        SimServer server = app.server();
        if (server == null) {
            d.text("SEM SERVIDOR DE REDE", 634, 350, RED, Align.RIGHT);
        } else if (server.hasStudent()) {
            d.text("ALUNO: " + server.studentDescription(), 634, 350, GREEN, Align.RIGHT);
        } else if ((time % 1.2f) < 0.9f) {
            d.text("AGUARDANDO ALUNO  IP: " + addresses, 634, 350, AMBER, Align.RIGHT);
        }
    }

    private void map(Draw d, DataBus bus) {
        d.rect(MAP_X, MAP_Y, MAP_W, MAP_H, PANEL);
        float cx = MAP_X + MAP_W / 2;
        float cy = MAP_Y + MAP_H / 2;
        float ppn = (MAP_H / 2 - 4) / RANGE_NM;
        double posX = bus.getDouble(Labels.TRUE_POS_X, 0);
        double posY = bus.getDouble(Labels.TRUE_POS_Y, 0);

        d.clip(MAP_X, MAP_Y, MAP_W, MAP_H);
        for (int ring = 5; ring <= RANGE_NM; ring += 5) {
            d.circle(cx, cy, ring * ppn, LINE);
        }
        // As pistas dos aeroportos no alcance do mapa, com o código de cada um
        for (Airport airport : World.AIRPORTS) {
            for (Runway r : airport.strips()) {
                d.filled().setColor(WHITE);
                d.filled().rectLine(cx + (float) ((r.xNm() - posX) * ppn), cy + (float) ((r.yNm() - posY) * ppn),
                        cx + (float) ((r.endXNm() - posX) * ppn), cy + (float) ((r.endYNm() - posY) * ppn), 3);
            }
            double[] c = airport.center();
            d.text(airport.icao(), cx + (float) ((c[0] - posX) * ppn) - 8, cy + (float) ((c[1] - posY) * ppn) + 12,
                    GREY, Align.RIGHT);
        }
        for (double[] p : trail) {
            d.rect(cx + (float) ((p[0] - posX) * ppn) - 1, cy + (float) ((p[1] - posY) * ppn) - 1, 2, 2, MAGENTA);
        }
        if (bus.getBoolean(Labels.SIM_BIRDS)) {
            float bx = cx + (float) ((bus.getDouble(Labels.SIM_BIRDS_X, 0) - posX) * ppn);
            float by = cy + (float) ((bus.getDouble(Labels.SIM_BIRDS_Y, 0) - posY) * ppn);
            d.line(bx - 4, by + 3, bx, by, AMBER);
            d.line(bx, by, bx + 4, by + 3, AMBER);
            d.text("AVES", bx + 6, by - 6, AMBER, Align.LEFT);
        }
        d.unclip();

        double hdg = Math.toRadians(bus.getDouble(Labels.TRUE_HDG, 0));
        d.triangle(cx + 9 * (float) Math.sin(hdg), cy + 9 * (float) Math.cos(hdg),
                cx + 7 * (float) Math.sin(hdg + 2.4), cy + 7 * (float) Math.cos(hdg + 2.4),
                cx + 7 * (float) Math.sin(hdg - 2.4), cy + 7 * (float) Math.cos(hdg - 2.4), YELLOW);
        d.text("N", MAP_X + 12, MAP_Y + MAP_H - 12, GREY, Align.CENTER);
        d.text(Math.round(RANGE_NM) + " NM", MAP_X + MAP_W - 6, MAP_Y + 10, GREY, Align.RIGHT);
        if (bus.getBoolean(Labels.SIM_FREEZE)) {
            d.text("PAUSADO", cx, MAP_Y + MAP_H - 20, YELLOW, Align.CENTER, 2);
        }
        if (bus.getBoolean(Labels.SIM_CRASHED)) {
            d.text("CRASH", cx, MAP_Y + 30, RED, Align.CENTER, 2);
        }
        d.frame(MAP_X, MAP_Y, MAP_W, MAP_H, LINE);
    }

    private void mirror(Draw d, DataBus bus) {
        d.text("ESPELHO DO ALUNO", 338, 334, GREY, Align.LEFT);
        row(d, 316, "IAS", fmt(bus, Labels.TRUE_IAS, "%.0f kt"), "ALT", fmt(bus, Labels.TRUE_ALT, "%.0f ft"));
        row(d, 300, "HDG", fmt(bus, Labels.TRUE_HDG, "%03.0f"), "V/S", fmt(bus, Labels.TRUE_VS, "%+.0f"));
        row(d, 284, "N1 1", fmt(bus, Labels.ENG1_N1, "%.1f%%"), "N1 2", fmt(bus, Labels.ENG2_N1, "%.1f%%"));
        row(d, 268, "FLAPS", fmt(bus, Labels.CTL_FLAPS, "%.0f"), "TREM",
                bus.getBoolean(Labels.CTL_GEAR_DOWN) ? "DN" : "UP");
        row(d, 252, "LEI", bus.getString(Labels.FCTL_LAW, "--"), "MANETE",
                fmt(bus, Labels.SIM_THRUST_LEVER, "%.2f"));
        String ap = bus.getBoolean(Labels.AP_ENGAGED)
                ? "AP1 " + bus.getString(Labels.AP_LAT_MODE, "") + " " + bus.getString(Labels.AP_VERT_MODE, "")
                : "OFF";
        row(d, 236, "AP", ap, "", "");

        List<String> alerts = new ArrayList<>();
        bus.getList(Labels.FWC_WARNINGS).forEach(w -> alerts.add("!" + w));
        alerts.addAll(bus.getList(Labels.FWC_CAUTIONS));
        String line = alerts.isEmpty() ? "sem alertas" : String.join("  ", alerts);
        boolean warning = !bus.getList(Labels.FWC_WARNINGS).isEmpty();
        d.text(line, 342, 216, alerts.isEmpty() ? GREY : warning ? RED : AMBER, Align.LEFT);
        d.line(338, 204, 634, 204, LINE);
    }

    private void row(Draw d, float y, String label1, String value1, String label2, String value2) {
        d.text(label1, 342, y, GREY, Align.LEFT);
        d.text(value1, 392, y, WHITE, Align.LEFT);
        d.text(label2, 490, y, GREY, Align.LEFT);
        d.text(value2, 552, y, WHITE, Align.LEFT);
    }

    private void log(Draw d) {
        List<Host.LoggedCommand> entries = app.host().log();
        float y = 84;
        for (int i = Math.max(0, entries.size() - 2); i < entries.size(); i++) {
            Host.LoggedCommand entry = entries.get(i);
            int seconds = (int) entry.simTime();
            d.text(String.format("%02d:%02d %s", seconds / 60, seconds % 60, entry.description()), 342, y, GREY,
                    Align.LEFT);
            y -= 14;
        }
    }

    /** O rack muda em voo se alguém arranca um cartão, então é montado a cada quadro. */
    private static String rackText(Host host) {
        return host.rack().slots().values().stream()
                .map(c -> c.manifest().partNumber())
                .collect(Collectors.joining("  "));
    }

    private static String fmt(DataBus bus, String label, String format) {
        return bus.has(label) ? String.format(format, bus.getDouble(label, 0)) : "--";
    }
}
