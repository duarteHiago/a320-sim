package br.a320sim.cockpit;

import br.a320sim.cockpit.audio.Sfx;
import br.a320sim.cockpit.audio.SoundFx;
import br.a320sim.cockpit.crud.CartoesAdapter;
import br.a320sim.cockpit.crud.PessoasAdapter;
import br.a320sim.cockpit.crud.PosicoesAdapter;
import br.a320sim.cockpit.crud.SessoesAdapter;
import br.a320sim.cockpit.screen.BootScreen;
import br.a320sim.cockpit.screen.CadastrosScreen;
import br.a320sim.cockpit.screen.CockpitScreen;
import br.a320sim.cockpit.screen.ConnectScreen;
import br.a320sim.cockpit.screen.CrudScreen;
import br.a320sim.cockpit.screen.IntroScreen;
import br.a320sim.cockpit.screen.IosScreen;
import br.a320sim.cockpit.screen.NovaSessaoScreen;
import br.a320sim.cockpit.screen.RackScreen;
import br.a320sim.cockpit.screen.TitleScreen;
import br.a320sim.cockpit.session.LocalSession;
import br.a320sim.cockpit.session.SessionKeeper;
import br.a320sim.cockpit.session.SessionPlan;
import br.a320sim.core.AppPaths;
import br.a320sim.dados.BancoLocal;
import br.a320sim.dados.Dados;
import br.a320sim.dados.modelo.Sessao;
import br.a320sim.host.Host;
import br.a320sim.host.command.ReleaseBirdsCommand;
import br.a320sim.host.command.SetAutoBirdsCommand;
import br.a320sim.host.command.SetWeatherCommand;
import br.a320sim.host.plan.FlightPlan;
import br.a320sim.cockpit.visual.Windshield;
import br.a320sim.host.cartridge.AutoReader;
import br.a320sim.host.cartridge.CartridgeLibrary;
import br.a320sim.host.cartridge.CartridgeLoader;
import br.a320sim.host.cartridge.ClasspathModuleFactory;
import br.a320sim.host.cartridge.HotPlug;
import br.a320sim.net.SimServer;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;

import java.io.IOException;

/**
 * O mesmo programa roda nos dois PCs; o papel é escolhido na tela de título:
 * SOLO (tudo aqui), INSTRUTOR (host + estação do instrutor) ou ALUNO (cockpit conectado ao instrutor).
 * <p>
 * O banco (cadastros e sessões) só é aberto onde o host roda: no modo ALUNO ele fica fechado.
 */
public class SimApp extends Game {

    public enum Mode { SOLO, INSTRUCTOR, STUDENT }

    /** De quanto em quanto tempo os leitores SD são varridos. */
    private static final long WATCH_MILLIS = 700;

    private Host host;
    private final BancoLocal banco = BancoLocal.doSistema();
    private SessionKeeper sessions;
    /** O que foi escolhido na última NOVA SESSAO (vira o padrão da próxima). */
    private SessionPlan plan = SessionPlan.NONE;
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
                new CartridgeLoader(new ClasspathModuleFactory(), banco.politica()));
        library.startWatching(WATCH_MILLIS);
        hotPlug = new HotPlug(host, library);
        sessions = new SessionKeeper(host, banco);
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
        } else if (!System.getProperty("sim.crud", "").isBlank()) {
            showCadastro(System.getProperty("sim.crud").split(",")[0].strip());
        } else if ("CadastrosScreen".equals(System.getProperty("sim.shotScreen"))) {
            showCadastros();
        } else if ("TitleScreen".equals(System.getProperty("sim.shotScreen"))) {
            showTitle();
        } else {
            setScreen(new IntroScreen(this));
        }
    }

    public void showTitle() {
        sessions.finish();
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
        banco.close(); // o aluno não grava nada; libera o arquivo para um instrutor no mesmo PC
        setScreen(new ConnectScreen(this));
    }

    /** Desliga o avião (fechando a sessão) e volta para a montagem do rack. */
    public void showRack() {
        sessions.finish();
        host.powerOff();
        setScreen(new RackScreen(this));
    }

    /** Rack montado: escolhe aluno, instrutor e posição antes de ligar. */
    public void showNewSession() {
        setScreen(new NovaSessaoScreen(this));
    }

    public void startBoot(SessionPlan chosen) {
        plan = chosen;
        setScreen(new BootScreen(this));
    }

    public SessionPlan lastPlan() {
        return plan;
    }

    public SessionKeeper sessions() {
        return sessions;
    }

    public void showCadastros() {
        setScreen(new CadastrosScreen(this));
    }

    /** Abre um cadastro: pessoas, cartoes, posicoes ou sessoes. */
    public void showCadastro(String name) {
        Dados dados = banco.abrir().orElse(null);
        if (dados == null) {
            showCadastros();
            return;
        }
        switch (name) {
            case "pessoas" -> setScreen(new CrudScreen<>(this, new PessoasAdapter(dados.pessoas())));
            case "cartoes" -> setScreen(new CrudScreen<>(this, new CartoesAdapter(dados.cartoes())));
            case "posicoes" -> setScreen(new CrudScreen<>(this, new PosicoesAdapter(dados.posicoes())));
            case "sessoes" -> setScreen(new CrudScreen<>(this, new SessoesAdapter(dados.sessoes())));
            default -> showCadastros();
        }
    }

    /** Depois do boot: o instrutor vai para a estação dele, no SOLO o próprio aluno pilota. */
    public void afterBoot() {
        sessions.start(plan, mode == Mode.INSTRUCTOR ? Sessao.INSTRUTOR : Sessao.SOLO);
        // Atalho de teste: -Dsim.vento=1 (TRAVES 15 KT) ou 2 (FORTE 30 KT), relativo à pista de destino
        // Atalho de teste: -Dsim.aves=1 solta um bando à frente; -Dsim.aves=auto liga as aves automáticas
        String birds = System.getProperty("sim.aves", "");
        if (birds.equals("1")) {
            host.execute(new ReleaseBirdsCommand());
        } else if (birds.equals("auto")) {
            host.execute(new SetAutoBirdsCommand(true));
        }
        // Atalho de teste: no modo INSTRUTOR, -Dsim.plano=... faz de conta que o aluno inseriu esse plano no MCDU
        String[] planValues = System.getProperty("sim.plano", "").split(",");
        if (mode == Mode.INSTRUCTOR && planValues.length == 9) {
            new FlightPlan(planValues[0], planValues[1], planValues[2], Integer.parseInt(planValues[3]),
                    Integer.parseInt(planValues[4]), Integer.parseInt(planValues[5]), Integer.parseInt(planValues[6]),
                    Integer.parseInt(planValues[7]), Integer.parseInt(planValues[8])).publish(host.bus());
        }
        String wind = System.getProperty("sim.vento", "");
        if (wind.matches("[0-2]")) {
            host.execute(SetWeatherCommand.presets(Windshield.destination(host.bus())).get(Integer.parseInt(wind)));
        }
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
        if (mode != Mode.STUDENT) {
            sessions.update(Gdx.graphics.getDeltaTime());
        }
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

    public BancoLocal banco() {
        return banco;
    }

    public String bancoCaminho() {
        return AppPaths.current().database().toAbsolutePath() + ".mv.db";
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
        sessions.finish(); // fechar a janela também fecha a sessão
        closeServer();
        library.close();
        banco.close();
        sound.dispose();
    }
}
