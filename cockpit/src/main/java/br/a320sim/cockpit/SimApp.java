package br.a320sim.cockpit;

import br.a320sim.cockpit.audio.Sfx;
import br.a320sim.cockpit.audio.SoundFx;
import br.a320sim.cockpit.screen.CockpitScreen;
import br.a320sim.cockpit.screen.ConnectScreen;
import br.a320sim.cockpit.screen.IntroScreen;
import br.a320sim.cockpit.screen.IosScreen;
import br.a320sim.cockpit.screen.RackScreen;
import br.a320sim.cockpit.screen.TitleScreen;
import br.a320sim.cockpit.session.LocalSession;
import br.a320sim.host.Host;
import br.a320sim.host.cartridge.AutoReader;
import br.a320sim.host.cartridge.CartridgeLibrary;
import br.a320sim.host.cartridge.CartridgeLoader;
import br.a320sim.host.cartridge.ClasspathModuleFactory;
import br.a320sim.host.cartridge.HotPlug;
import br.a320sim.net.SimServer;
import com.badlogic.gdx.Game;

import java.io.IOException;

/**
 * O mesmo programa roda nos dois PCs; o papel é escolhido na tela de título:
 * SOLO (tudo aqui), INSTRUTOR (host + estação do instrutor) ou ALUNO (cockpit conectado ao instrutor).
 */
public class SimApp extends Game {

    public enum Mode { SOLO, INSTRUCTOR, STUDENT }

    /** De quanto em quanto tempo os leitores SD são varridos. */
    private static final long WATCH_MILLIS = 700;

    private Host host;
    private CartridgeLibrary library;
    private HotPlug hotPlug;
    private SoundFx sound;
    private Mode mode = Mode.SOLO;
    private SimServer server;
    private String error;

    @Override
    public void create() {
        host = new Host();
        library = new CartridgeLibrary(AutoReader.fromSystemProperties(),
                new CartridgeLoader(new ClasspathModuleFactory()));
        library.startWatching(WATCH_MILLIS);
        hotPlug = new HotPlug(host, library);
        sound = new SoundFx();

        // Atalhos de linha de comando, usados para testar sem passar pelos menus
        String startMode = System.getProperty("sim.mode", "");
        if (startMode.equals("instrutor")) {
            startInstructor();
        } else if (startMode.equals("aluno")) {
            startStudent();
        } else if (startMode.equals("solo")) {
            startSolo();
        } else if (System.getProperty("sim.autostart") != null) {
            startSolo();
        } else if ("TitleScreen".equals(System.getProperty("sim.shotScreen"))) {
            showTitle();
        } else {
            setScreen(new IntroScreen(this));
        }
    }

    public void showTitle() {
        host.powerOff();
        closeServer();
        setScreen(new TitleScreen(this));
    }

    public void startSolo() {
        mode = Mode.SOLO;
        setScreen(new RackScreen(this));
    }

    public void startInstructor() {
        try {
            if (server == null) {
                server = new SimServer(SimServer.DEFAULT_PORT);
            }
            mode = Mode.INSTRUCTOR;
            setScreen(new RackScreen(this));
        } catch (IOException e) {
            error = "Nao consegui abrir a porta " + SimServer.DEFAULT_PORT + ": " + e.getMessage();
            showTitle();
        }
    }

    public void startStudent() {
        mode = Mode.STUDENT;
        setScreen(new ConnectScreen(this));
    }

    /** Depois do boot: o instrutor vai para a estação dele, no SOLO o próprio aluno pilota. */
    public void afterBoot() {
        if (mode == Mode.INSTRUCTOR) {
            setScreen(new IosScreen(this));
        } else {
            setScreen(new CockpitScreen(this, new LocalSession(host)));
        }
    }

    /**
     * Com o avião ligado aqui (SOLO ou INSTRUTOR), cartão arrancado do leitor apaga as caixas dele,
     * e cartão encaixado num slot vazio carrega na hora. Na tela do rack quem cuida é a própria tela.
     */
    @Override
    public void render() {
        super.render();
        if (mode != Mode.STUDENT && host.isPowered()) {
            for (String event : hotPlug.update()) {
                System.out.println("[RACK] " + event);
                sound.play(event.endsWith("CARREGADO") ? Sfx.CHIME : Sfx.ERROR);
            }
        }
    }

    /** Erro pendente para a tela de título mostrar (lido uma vez só). */
    public String takeError() {
        String e = error;
        error = null;
        return e;
    }

    public Host host() {
        return host;
    }

    public CartridgeLibrary library() {
        return library;
    }

    public SoundFx sound() {
        return sound;
    }

    public Mode mode() {
        return mode;
    }

    public SimServer server() {
        return server;
    }

    private void closeServer() {
        if (server != null) {
            server.close();
            server = null;
        }
    }

    @Override
    public void dispose() {
        super.dispose(); // chama hide() da tela atual, que se descarta
        closeServer();
        library.close();
        sound.dispose();
    }
}
