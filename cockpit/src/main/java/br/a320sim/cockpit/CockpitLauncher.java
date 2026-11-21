package br.a320sim.cockpit;

import br.a320sim.cockpit.screen.RetroScreen;
import com.badlogic.gdx.Files.FileType;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

public final class CockpitLauncher {

    /** Ícone da janela e da barra de tarefas, em vários tamanhos (o sistema escolhe o melhor). */
    private static final String[] ICONS = {
            "icones/icone_16.png", "icones/icone_32.png", "icones/icone_48.png",
            "icones/icone_128.png", "icones/icone_256.png"};

    private CockpitLauncher() {
    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("SICA");
        config.setWindowIcon(FileType.Classpath, ICONS);
        config.setWindowedMode(RetroScreen.WIDTH * 2, RetroScreen.HEIGHT * 2);
        config.setForegroundFPS(60);
        config.useVsync(true);
        new Lwjgl3Application(new SimApp(), config);
    }
}
