package br.a320sim.cockpit.screen;

import br.a320sim.cockpit.SimApp;
import br.a320sim.cockpit.audio.Sfx;
import br.a320sim.cockpit.gfx.Draw;
import br.a320sim.cockpit.gfx.Draw.Align;
import br.a320sim.core.cartridge.Cartridge;
import br.a320sim.core.cartridge.CartridgeGroup;
import br.a320sim.core.cartridge.CartridgeManifest;
import br.a320sim.core.cartridge.Generation;
import br.a320sim.core.module.AvionicsModule;
import br.a320sim.host.Host;
import br.a320sim.host.rack.Rack;
import br.a320sim.host.rack.RackReport;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * "Monte seu A320": um slot por grupo, setas escolhem o cartucho de cada um.
 * A validação roda ao vivo, então dá para ver na hora uma combinação incompatível.
 */
public class RackScreen extends RetroScreen {

    private static final CartridgeGroup[] GROUPS = CartridgeGroup.values();
    private static final float CART_W = 170;
    private static final float CART_H = 142;
    private static final float CART_Y = 120;
    private static final Color BODY = Color.valueOf("7d8590");
    private static final Color BODY_DARK = Color.valueOf("4f555e");
    private static final Color CEO = Color.valueOf("e0902c");
    private static final Color NEO = Color.valueOf("2f8fe0");
    private static final Color GOLD = Color.valueOf("d8b040");
    private static final Color GREY = Color.valueOf("8a909a");
    private static final Color GREEN = Color.valueOf("38ff6a");
    private static final Color RED = Color.valueOf("ff3a30");
    private static final Color YELLOW = Color.valueOf("ffff48");

    private final Map<CartridgeGroup, List<CartridgeManifest>> options = new EnumMap<>(CartridgeGroup.class);
    private final Map<CartridgeGroup, Integer> choice = new EnumMap<>(CartridgeGroup.class);
    private final Map<CartridgeGroup, Cartridge> preview = new EnumMap<>(CartridgeGroup.class);
    private RackReport report;
    private int selected;
    private float time;
    private boolean autostart;

    public RackScreen(SimApp app) {
        super(app);
        for (CartridgeManifest m : app.library().available()) {
            options.computeIfAbsent(m.grupo(), g -> new ArrayList<>()).add(m);
        }
        String auto = System.getProperty("sim.autostart");
        for (CartridgeGroup group : GROUPS) {
            List<CartridgeManifest> list = options.getOrDefault(group, List.of());
            String current = app.host().rack().slot(group).map(c -> c.manifest().partNumber()).orElse(null);
            int index = list.isEmpty() ? -1 : 0;
            for (int i = 0; i < list.size(); i++) {
                String pn = list.get(i).partNumber();
                if (pn.equals(current) || (auto != null && auto.contains(pn))) {
                    index = i;
                }
            }
            choice.put(group, index);
        }
        autostart = auto != null;
        refresh();
    }

    @Override
    protected void update(float delta) {
        time += delta;
        if (autostart) {
            autostart = false;
            start();
            return;
        }
        if (Gdx.input.isKeyJustPressed(Keys.ESCAPE)) {
            app.showTitle();
            return;
        }
        if (Gdx.input.isKeyJustPressed(Keys.LEFT) || Gdx.input.isKeyJustPressed(Keys.A)) {
            cycle(-1);
        }
        if (Gdx.input.isKeyJustPressed(Keys.RIGHT) || Gdx.input.isKeyJustPressed(Keys.D)) {
            cycle(1);
        }
        if (Gdx.input.isKeyJustPressed(Keys.UP) || Gdx.input.isKeyJustPressed(Keys.W)) {
            selected = (selected + GROUPS.length - 1) % GROUPS.length;
            app.sound().play(Sfx.CLICK);
        }
        if (Gdx.input.isKeyJustPressed(Keys.DOWN) || Gdx.input.isKeyJustPressed(Keys.S)
                || Gdx.input.isKeyJustPressed(Keys.TAB)) {
            selected = (selected + 1) % GROUPS.length;
            app.sound().play(Sfx.CLICK);
        }
        if (Gdx.input.isKeyJustPressed(Keys.ENTER)) {
            start();
        }
    }

    /** Troca o cartucho do slot selecionado; a última opção é deixar o slot vazio. */
    private void cycle(int direction) {
        CartridgeGroup group = GROUPS[selected];
        int count = options.getOrDefault(group, List.of()).size();
        int next = Math.floorMod(choice.get(group) + 1 + direction, count + 1) - 1;
        choice.put(group, next);
        app.sound().play(Sfx.CLICK);
        refresh();
    }

    private void refresh() {
        preview.clear();
        Rack rack = new Rack();
        for (CartridgeGroup group : GROUPS) {
            int index = choice.get(group);
            if (index >= 0) {
                Cartridge cartridge = app.library().load(options.get(group).get(index));
                preview.put(group, cartridge);
                rack.insert(cartridge);
            }
        }
        report = rack.validate();
    }

    private void start() {
        if (!report.ok()) {
            app.sound().play(Sfx.ERROR);
            return;
        }
        Host host = app.host();
        host.powerOff();
        for (CartridgeGroup group : GROUPS) {
            host.rack().remove(group);
            host.rack().insert(preview.get(group));
        }
        app.setScreen(new BootScreen(app));
    }

    @Override
    protected void paint(Draw d) {
        boolean instructor = app.mode() == SimApp.Mode.INSTRUCTOR;
        d.text(instructor ? "MONTE O A320 DO ALUNO" : "MONTE SEU A320", WIDTH / 2f, 334, YELLOW, Align.CENTER, 2);
        d.text("escolha um cartucho para cada slot", WIDTH / 2f, 310, GREY, Align.CENTER);

        for (int i = 0; i < GROUPS.length; i++) {
            CartridgeGroup group = GROUPS[i];
            float x = 35 + i * 200;
            d.text(group.name(), x + CART_W / 2, 290, Color.WHITE, Align.CENTER);
            d.text(group.description(), x + CART_W / 2, 276, GREY, Align.CENTER);

            Cartridge cartridge = preview.get(group);
            if (cartridge == null) {
                emptySlot(d, x);
            } else {
                cartridge(d, x, cartridge);
            }
            if (i == selected) {
                boolean blink = (time % 0.8f) < 0.55f;
                if (blink) {
                    d.frame(x - 5, CART_Y - 5, CART_W + 10, CART_H + 10, YELLOW);
                }
                d.text("<", x - 16, CART_Y + CART_H / 2, YELLOW, Align.CENTER, 2);
                d.text(">", x + CART_W + 16, CART_Y + CART_H / 2, YELLOW, Align.CENTER, 2);
            }
        }

        if (report.ok()) {
            d.text("CONFIGURACAO VALIDA", WIDTH / 2f, 66, GREEN, Align.CENTER);
            if ((time % 1f) < 0.6f) {
                d.text("ENTER = LIGAR O AVIAO", WIDTH / 2f, 46, Color.WHITE, Align.CENTER);
            }
        } else {
            float y = 72;
            for (String problem : report.problems()) {
                d.text("X " + problem, WIDTH / 2f, y, RED, Align.CENTER);
                y -= 14;
            }
        }
        d.text("SETAS: escolher    ENTER: ligar    ESC: voltar", WIDTH / 2f, 14, GREY, Align.CENTER);
    }

    private void cartridge(Draw d, float x, Cartridge cartridge) {
        CartridgeManifest m = cartridge.manifest();
        boolean neo = m.versao() == Generation.NEO;

        d.rect(x, CART_Y, CART_W, CART_H, BODY);
        d.rect(x + 50, CART_Y + CART_H - 10, CART_W - 100, 10, BODY_DARK);
        for (int pin = 0; pin < 14; pin++) {
            d.rect(x + 18 + pin * 10, CART_Y + 4, 6, 10, GOLD);
        }

        float sx = x + 14;
        float sy = CART_Y + 22;
        float sw = CART_W - 28;
        float sh = CART_H - 40;
        d.rect(sx, sy, sw, sh, neo ? NEO : CEO);
        d.frame(sx, sy, sw, sh, Color.BLACK);
        d.text("AIRBUS A320", sx + sw / 2, sy + sh - 10, Color.BLACK, Align.CENTER);
        d.text(m.versao().name(), sx + sw / 2, sy + sh / 2 + 4, Color.WHITE, Align.CENTER, 3);
        d.text(m.partNumber(), sx + sw / 2, sy + 10, Color.BLACK, Align.CENTER);

        float y = CART_Y - 14;
        for (AvionicsModule module : cartridge.modules()) {
            d.text(module.info().variant(), x + CART_W / 2, y, GREY, Align.CENTER);
            y -= 13;
        }
    }

    private void emptySlot(Draw d, float x) {
        for (float dx = 0; dx < CART_W; dx += 10) {
            d.line(x + dx, CART_Y, x + dx + 5, CART_Y, GREY);
            d.line(x + dx, CART_Y + CART_H, x + dx + 5, CART_Y + CART_H, GREY);
        }
        for (float dy = 0; dy < CART_H; dy += 10) {
            d.line(x, CART_Y + dy, x, CART_Y + dy + 5, GREY);
            d.line(x + CART_W, CART_Y + dy, x + CART_W, CART_Y + dy + 5, GREY);
        }
        d.text("VAZIO", x + CART_W / 2, CART_Y + CART_H / 2, GREY, Align.CENTER, 2);
    }
}
