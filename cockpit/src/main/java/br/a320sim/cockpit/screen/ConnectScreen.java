package br.a320sim.cockpit.screen;

import br.a320sim.cockpit.SimApp;
import br.a320sim.cockpit.audio.Sfx;
import br.a320sim.cockpit.gfx.Draw;
import br.a320sim.cockpit.gfx.Draw.Align;
import br.a320sim.cockpit.gfx.TextInput;
import br.a320sim.cockpit.session.RemoteSession;
import br.a320sim.net.SimClient;
import br.a320sim.net.SimServer;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;

import java.util.concurrent.CompletableFuture;

/** Modo ALUNO: digita o IP do instrutor e conecta. */
public class ConnectScreen extends RetroScreen {

    private static final Color GREEN = Color.valueOf("38ff6a");
    private static final Color GREY = Color.valueOf("8a909a");
    private static final Color RED = Color.valueOf("ff3a30");
    private static final Color YELLOW = Color.valueOf("ffff48");

    private final TextInput address = new TextInput(System.getProperty("sim.host", ""), 40);
    private CompletableFuture<SimClient> connecting;
    private String message = "";
    private float time;

    public ConnectScreen(SimApp app) {
        super(app);
        address.setActive(true);
        if (System.getProperty("sim.host") != null) {
            connect();
        }
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(address);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
        super.hide();
    }

    @Override
    protected void update(float delta) {
        time += delta;
        if (connecting != null) {
            if (!connecting.isDone()) {
                return;
            }
            try {
                SimClient client = connecting.join();
                app.sound().play(Sfx.BOOT);
                app.setScreen(new CockpitScreen(app, new RemoteSession(client)));
            } catch (RuntimeException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                message = "FALHOU: " + cause.getMessage();
                app.sound().play(Sfx.ERROR);
                connecting = null;
            }
            return;
        }
        if (Gdx.input.isKeyJustPressed(Keys.ESCAPE)) {
            app.showTitle();
            return;
        }
        if (Gdx.input.isKeyJustPressed(Keys.ENTER) && !address.text().isBlank()) {
            connect();
        }
    }

    private void connect() {
        String target = address.text().trim();
        String name = System.getProperty("user.name", "aluno");
        message = "";
        connecting = CompletableFuture.supplyAsync(() -> {
            try {
                return SimClient.connect(target, SimServer.DEFAULT_PORT, name);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
    }

    @Override
    protected void paint(Draw d) {
        d.text("MODO ALUNO", WIDTH / 2f, 310, YELLOW, Align.CENTER, 2);
        d.text("digite o IP que aparece na tela do instrutor", WIDTH / 2f, 260, GREY, Align.CENTER);

        d.frame(170, 196, 300, 30, Color.WHITE);
        String cursor = connecting == null && (time % 0.8f) < 0.5f ? "_" : "";
        d.text(address.text() + cursor, 180, 211, GREEN, Align.LEFT);

        if (connecting != null) {
            int dots = (int) (time * 3) % 4;
            d.text("CONECTANDO" + ".".repeat(dots), WIDTH / 2f, 160, GREEN, Align.CENTER);
        } else if (!message.isEmpty()) {
            d.text(message, WIDTH / 2f, 160, RED, Align.CENTER);
        }

        d.text("Mesmo Wi-Fi: use o IP local do instrutor (ex.: 192.168.0.15)", WIDTH / 2f, 100, GREY, Align.CENTER);
        d.text("Casas diferentes: os dois na mesma VPN (Radmin VPN / Tailscale)", WIDTH / 2f, 84, GREY,
                Align.CENTER);
        d.text("ENTER: conectar      ESC: voltar", WIDTH / 2f, 14, GREY, Align.CENTER);
    }

    @Override
    protected boolean scanlines() {
        return true;
    }
}
