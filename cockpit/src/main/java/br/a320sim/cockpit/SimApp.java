package br.a320sim.cockpit;

import br.a320sim.cockpit.audio.SoundFx;
import br.a320sim.cockpit.screen.RackScreen;
import br.a320sim.host.Host;
import br.a320sim.host.cartridge.CartridgeLibrary;
import br.a320sim.host.cartridge.CartridgeLoader;
import br.a320sim.host.cartridge.ClasspathModuleFactory;
import br.a320sim.host.cartridge.DevFolderReader;
import com.badlogic.gdx.Game;

import java.nio.file.Path;

/**
 * Aplicação do cockpit. Na Fase 2 o host roda no mesmo processo;
 * na Fase 4 ele vai para o PC do instrutor e conversa por rede.
 */
public class SimApp extends Game {

    private Host host;
    private CartridgeLibrary library;
    private SoundFx sound;

    @Override
    public void create() {
        host = new Host();
        library = new CartridgeLibrary(new DevFolderReader(Path.of("sd-dev")),
                new CartridgeLoader(new ClasspathModuleFactory()));
        sound = new SoundFx();
        setScreen(new RackScreen(this));
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

    @Override
    public void dispose() {
        super.dispose(); // chama hide() da tela atual, que se descarta
        sound.dispose();
    }
}
