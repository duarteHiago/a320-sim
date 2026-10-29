package br.a320sim.cockpit.screen;

import br.a320sim.cockpit.SimApp;
import br.a320sim.cockpit.audio.Sfx;
import br.a320sim.cockpit.gfx.Draw;
import br.a320sim.cockpit.gfx.Draw.Align;
import br.a320sim.core.cartridge.Cartridge;
import br.a320sim.core.module.AvionicsModule;
import br.a320sim.core.module.SelfTestResult;
import br.a320sim.host.Host;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sequência de boot no estilo console antigo: carrega cada cartucho (DATA LOADING),
 * liga o avião e mostra o self-test de cada caixa. Qualquer tecla acelera.
 */
public class BootScreen extends RetroScreen {

    private static final Color TEXT = Color.valueOf("38ff6a");
    private static final Color DIM = Color.valueOf("1d7a3a");
    private static final Color RED = Color.valueOf("ff3a30");
    private static final int BAR = 16;

    private record Step(float delay, Runnable action) {
    }

    private final List<Step> steps = new ArrayList<>();
    private final List<String> lines = new ArrayList<>();
    private final List<Color> colors = new ArrayList<>();
    private int next;
    private float timer;
    private boolean fast = System.getProperty("sim.autostart") != null;

    public BootScreen(SimApp app) {
        super(app);
        Host host = app.host();
        step(0.3f, () -> print("A320 SIM  HOST BIOS v0.2", TEXT));
        step(0.3f, () -> print("RACK: " + host.rack().slots().size() + " CARTUCHOS DETECTADOS", TEXT));
        for (Cartridge cartridge : host.rack().slots().values()) {
            String pn = cartridge.manifest().partNumber();
            step(0.1f, () -> print("", TEXT));
            for (int i = 0; i <= BAR; i++) {
                int filled = i;
                step(0.04f, () -> {
                    replaceLast("DATA LOADING  P/N " + pn + "  [" + "#".repeat(filled) + ".".repeat(BAR - filled) + "]");
                    if (filled % 4 == 0) {
                        app.sound().play(Sfx.LOAD);
                    }
                });
            }
            step(0.1f, () -> replaceLast(lastLine() + " OK"));
        }
        step(0.4f, () -> print("POWER UP  -  SELF TEST", TEXT));
        step(0.1f, () -> {
            Map<AvionicsModule, SelfTestResult> results = host.powerOn().selfTests();
            int insertAt = next;
            for (Map.Entry<AvionicsModule, SelfTestResult> e : results.entrySet()) {
                var info = e.getKey().info();
                boolean ok = e.getValue().passed();
                String line = "  " + info.friendlyName().toUpperCase() + "  (" + info.variant() + ")  ..... "
                        + (ok ? "OK" : "FALHA");
                steps.add(insertAt++, new Step(0.22f, () -> {
                    print(line, ok ? TEXT : RED);
                    app.sound().play(ok ? Sfx.CLICK : Sfx.ERROR);
                }));
            }
        });
        step(0.3f, () -> print("", TEXT));
        step(0.1f, () -> {
            print("TODOS OS SISTEMAS PRONTOS", Color.WHITE);
            app.sound().play(Sfx.BOOT);
        });
        step(1.4f, () -> app.setScreen(new CockpitScreen(app)));
    }

    private void step(float delay, Runnable action) {
        steps.add(new Step(delay, action));
    }

    private void print(String line, Color color) {
        lines.add(line);
        colors.add(color);
    }

    private String lastLine() {
        return lines.get(lines.size() - 1);
    }

    private void replaceLast(String line) {
        lines.set(lines.size() - 1, line);
    }

    @Override
    protected void update(float delta) {
        if (Gdx.input.isKeyJustPressed(Keys.ANY_KEY)) {
            fast = true;
        }
        timer -= fast ? delta * 8 : delta;
        while (timer <= 0 && next < steps.size() && app.getScreen() == this) {
            Step step = steps.get(next++);
            step.action().run();
            timer += step.delay();
        }
    }

    @Override
    protected void paint(Draw d) {
        float y = HEIGHT - 20;
        int first = Math.max(0, lines.size() - 22);
        for (int i = first; i < lines.size(); i++) {
            d.text(lines.get(i), 20, y, colors.get(i), Align.LEFT);
            y -= 15;
        }
        d.text("_", 20, y, ((int) (System.currentTimeMillis() / 400) % 2 == 0) ? TEXT : DIM, Align.LEFT);
    }

    @Override
    protected boolean scanlines() {
        return true;
    }
}
