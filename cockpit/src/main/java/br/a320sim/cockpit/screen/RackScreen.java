package br.a320sim.cockpit.screen;

import br.a320sim.cockpit.SimApp;
import br.a320sim.cockpit.audio.Sfx;
import br.a320sim.cockpit.gfx.Draw;
import br.a320sim.cockpit.gfx.Draw.Align;
import br.a320sim.cockpit.input.MenuInput;
import br.a320sim.core.cartridge.Cartridge;
import br.a320sim.core.cartridge.CartridgeGroup;
import br.a320sim.core.cartridge.CartridgeManifest;
import br.a320sim.core.cartridge.CartridgeRejectedException;
import br.a320sim.core.cartridge.Generation;
import br.a320sim.core.module.AvionicsModule;
import br.a320sim.host.Host;
import br.a320sim.host.cartridge.DetectedCartridge;
import br.a320sim.host.rack.Rack;
import br.a320sim.host.rack.RackReport;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Monte seu A320": um slot por grupo, setas escolhem o cartucho de cada um.
 * A validação roda ao vivo, então dá para ver na hora uma combinação incompatível.
 * <p>
 * Com cartões SD, a tela acompanha os leitores: cartão encaixado vai direto para o slot do grupo dele,
 * cartão removido some do slot.
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

    private final Map<CartridgeGroup, List<DetectedCartridge>> options = new EnumMap<>(CartridgeGroup.class);
    private final Map<CartridgeGroup, Integer> choice = new EnumMap<>(CartridgeGroup.class);
    private final Map<CartridgeGroup, Cartridge> preview = new EnumMap<>(CartridgeGroup.class);
    private final Set<String> knownCards = new HashSet<>();
    private RackReport report;
    private int selected;
    private float time;
    private boolean autostart;
    private boolean sdMode;
    private int seenRevision;
    /** Segundos desde que cada slot trocou de cartucho (animação de encaixe). */
    private final Map<CartridgeGroup, Float> slide = new EnumMap<>(CartridgeGroup.class);
    private static final float SLIDE_TIME = 0.28f;

    public RackScreen(SimApp app) {
        super(app);
        seenRevision = app.library().revision();
        List<DetectedCartridge> available = app.library().available();
        rebuildOptions(available);
        available.forEach(c -> knownCards.add(key(c)));
        String auto = System.getProperty("sim.autostart");
        for (CartridgeGroup group : GROUPS) {
            List<DetectedCartridge> list = options.getOrDefault(group, List.of());
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

    private static String key(DetectedCartridge c) {
        return c.partNumber() + "@" + c.location();
    }

    private void rebuildOptions(List<DetectedCartridge> available) {
        options.clear();
        sdMode = false;
        for (DetectedCartridge c : available) {
            options.computeIfAbsent(c.group(), g -> new ArrayList<>()).add(c);
            sdMode |= c.removable();
        }
    }

    /** Um cartão entrou ou saiu de um leitor: refaz as opções mantendo o que dava para manter. */
    private void onCardsChanged() {
        Map<CartridgeGroup, String> previous = new EnumMap<>(CartridgeGroup.class);
        for (CartridgeGroup group : GROUPS) {
            int index = choice.get(group);
            if (index >= 0) {
                previous.put(group, key(options.get(group).get(index)));
            }
        }
        List<DetectedCartridge> available = app.library().available();
        rebuildOptions(available);

        boolean inserted = false;
        boolean removed = false;
        for (CartridgeGroup group : GROUPS) {
            List<DetectedCartridge> list = options.getOrDefault(group, List.of());
            int index = -1;
            for (int i = 0; i < list.size(); i++) {
                String k = key(list.get(i));
                if (!knownCards.contains(k)) {
                    // Cartão novo no leitor: vai para o slot, e o slot fica selecionado
                    index = i;
                    selected = group.ordinal();
                    inserted = true;
                    break;
                }
                if (k.equals(previous.get(group))) {
                    index = i;
                }
            }
            if (index < 0 && previous.containsKey(group)) {
                removed = true;
                index = list.isEmpty() ? -1 : 0;
            }
            choice.put(group, index);
        }
        knownCards.clear();
        available.forEach(c -> knownCards.add(key(c)));
        for (CartridgeGroup group : GROUPS) {
            int index = choice.get(group);
            String now = index >= 0 ? key(options.get(group).get(index)) : null;
            if (now != null && !now.equals(previous.get(group))) {
                slide.put(group, 0f);
            }
        }
        if (inserted) {
            app.sound().play(Sfx.INSERT);
        } else if (removed) {
            app.sound().play(Sfx.EJECT);
        }
        refresh();
    }

    @Override
    protected void update(float delta) {
        time += delta;
        if (app.library().revision() != seenRevision) {
            seenRevision = app.library().revision();
            onCardsChanged();
        }
        if (autostart) {
            autostart = false;
            start();
            return;
        }
        slide.replaceAll((group, t) -> t + delta);
        if (MenuInput.back()) {
            app.sound().play(Sfx.BACK);
            app.showTitle();
            return;
        }
        // Setas para os lados trocam o cartucho; para cima/baixo (ou TAB) trocam de slot
        if (MenuInput.left()) {
            cycle(-1);
        }
        if (MenuInput.right()) {
            cycle(1);
        }
        if (MenuInput.up()) {
            selected = (selected + GROUPS.length - 1) % GROUPS.length;
            app.sound().play(Sfx.MOVE);
        }
        if (MenuInput.down() || Gdx.input.isKeyJustPressed(Keys.TAB)) {
            selected = (selected + 1) % GROUPS.length;
            app.sound().play(Sfx.MOVE);
        }
        if (MenuInput.confirm()) {
            start();
        }
    }

    /** Troca o cartucho do slot selecionado; a última opção é deixar o slot vazio. */
    private void cycle(int direction) {
        CartridgeGroup group = GROUPS[selected];
        int count = options.getOrDefault(group, List.of()).size();
        int next = Math.floorMod(choice.get(group) + 1 + direction, count + 1) - 1;
        choice.put(group, next);
        app.sound().play(next < 0 ? Sfx.EJECT : Sfx.INSERT);
        slide.put(group, 0f);
        refresh();
    }

    private void refresh() {
        preview.clear();
        List<String> loadErrors = new ArrayList<>();
        Rack rack = new Rack();
        for (CartridgeGroup group : GROUPS) {
            int index = choice.get(group);
            if (index >= 0) {
                DetectedCartridge detected = options.get(group).get(index);
                try {
                    Cartridge cartridge = app.library().load(detected);
                    preview.put(group, cartridge);
                    rack.insert(cartridge);
                } catch (CartridgeRejectedException e) {
                    loadErrors.add("CARTAO " + detected.partNumber() + " RECUSADO: " + e.reason().label());
                    System.err.println("[RACK] " + detected.partNumber() + ": " + e.getMessage());
                } catch (RuntimeException e) {
                    loadErrors.add("CARTAO " + detected.partNumber() + " ILEGIVEL");
                    System.err.println("[RACK] " + detected.partNumber() + ": " + e.getMessage());
                }
            }
        }
        List<String> problems = new ArrayList<>(loadErrors);
        problems.addAll(rack.validate().problems());
        report = new RackReport(problems);
    }

    private void start() {
        if (!report.ok()) {
            app.sound().play(Sfx.ERROR);
            return;
        }
        app.sound().play(Sfx.SELECT);
        Host host = app.host();
        host.powerOff();
        for (CartridgeGroup group : GROUPS) {
            host.rack().remove(group);
            host.rack().insert(preview.get(group));
        }
        app.showNewSession();
    }

    @Override
    protected void paint(Draw d) {
        boolean instructor = app.mode() == SimApp.Mode.INSTRUCTOR;
        d.text(instructor ? "MONTE O A320 DO ALUNO" : "MONTE SEU A320", WIDTH / 2f, 334, YELLOW, Align.CENTER, 2);
        d.text(sdMode ? "encaixe um cartao em cada leitor (ou escolha com as setas)"
                : "escolha um cartucho para cada slot", WIDTH / 2f, 310, GREY, Align.CENTER);

        for (int i = 0; i < GROUPS.length; i++) {
            CartridgeGroup group = GROUPS[i];
            float x = 35 + i * 200;
            d.text(group.name(), x + CART_W / 2, 290, Color.WHITE, Align.CENTER);
            d.text(group.description(), x + CART_W / 2, 276, GREY, Align.CENTER);

            Cartridge cartridge = preview.get(group);
            if (cartridge == null) {
                emptySlot(d, x);
            } else {
                // Encaixe: o cartucho desce de cima e assenta no slot
                float t = Math.min(1, slide.getOrDefault(group, SLIDE_TIME) / SLIDE_TIME);
                float drop = (1 - t) * (1 - t) * 70;
                cartridge(d, x, drop, cartridge);
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
                d.text(MenuInput.hasController() ? "A = LIGAR O AVIAO" : "ENTER = LIGAR O AVIAO", WIDTH / 2f, 46,
                        Color.WHITE, Align.CENTER, 2);
            }
        } else {
            float y = 72;
            for (String problem : report.problems()) {
                d.text("X " + problem, WIDTH / 2f, y, RED, Align.CENTER);
                y -= 14;
            }
        }
        d.text(MenuInput.hasController() ? "DIRECIONAL: escolher    A: ligar    B: voltar"
                : "SETAS: escolher    ENTER: ligar    ESC: voltar", WIDTH / 2f, 14, GREY, Align.CENTER);
    }

    private void cartridge(Draw d, float x, float drop, Cartridge cartridge) {
        final float CART_Y = RackScreen.CART_Y + drop;
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
        String origin = cartridge.removable() ? "LEITOR " + readerName(cartridge.location()) : "PASTA DEV";
        d.text(origin, sx + sw / 2, sy + 24, Color.WHITE, Align.CENTER);

        float y = CART_Y - 14;
        for (AvionicsModule module : cartridge.modules()) {
            d.text(module.info().variant(), x + CART_W / 2, y, GREY, Align.CENTER);
            y -= 13;
        }
    }

    /** "G:\\" vira "G:"; uma pasta fazendo papel de leitor (sim.sd.roots) mostra só o nome dela. */
    private static String readerName(String location) {
        Path name = Path.of(location).getFileName();
        return name != null ? name.toString() : location.replace("\\", "");
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
        d.text("VAZIO", x + CART_W / 2, CART_Y + CART_H / 2 + 14, GREY, Align.CENTER, 2);
        if ((time % 1f) < 0.6f) {
            d.text("INSIRA O", x + CART_W / 2, CART_Y + CART_H / 2 - 12, YELLOW, Align.CENTER);
            d.text("CARTUCHO", x + CART_W / 2, CART_Y + CART_H / 2 - 24, YELLOW, Align.CENTER);
        }
    }
}
