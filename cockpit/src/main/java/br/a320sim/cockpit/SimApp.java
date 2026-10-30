package br.a320sim.cockpit;

import br.a320sim.cockpit.audio.SoundFx;
import br.a320sim.cockpit.screen.CockpitScreen;
import br.a320sim.cockpit.screen.ConnectScreen;
import br.a320sim.cockpit.screen.IosScreen;
import br.a320sim.cockpit.screen.RackScreen;
import br.a320sim.cockpit.screen.TitleScreen;
import br.a320sim.cockpit.session.LocalSession;
import br.a320sim.host.Host;
import br.a320sim.host.cartridge.CartridgeLibrary;
import br.a320sim.host.cartridge.CartridgeLoader;
import br.a320sim.host.cartridge.ClasspathModuleFactory;
import br.a320sim.host.cartridge.DevFolderReader;
import br.a320sim.net.SimServer;
import com.badlogic.gdx.Game;

import java.io.IOException;
import java.nio.file.Path;

/**
 * O mesmo programa roda nos dois PCs; o papel é escolhido na tela de título:
 * SOLO (tudo aqui), INSTRUTOR (host + estação do instrutor) ou ALUNO (cockpit conectado ao instrutor).
 */
public class SimApp extends Game {

    public enum Mode { SOLO, INSTRUCTOR, STUDENT }

    private Host host;
    private CartridgeLibrary library;
    private SoundFx sound;
    private Mode mode = Mode.SOLO;
    private SimServer server;
    private String error;

    @Override
    public void create() {
        host = new Host();
        library = new CartridgeLibrary(new DevFolderReader(Path.of("sd-dev")),
                new CartridgeLoader(new ClasspathModuleFactory()));
        sound = new SoundFx();

        // Atalhos de linha de comando, usados para testar sem passar pelos menus
        String startMode = System.getProperty("sim.mode", "");
        if (startMode.equals("instrutor")) {
            startInstructor();
        } else if (startMode.equals("aluno")) {
            startStudent();
        } else if (System.getProperty("sim.autostart") != null) {
            startSolo();
        } else {
            showTitle();
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
        sound.dispose();
    }
}
