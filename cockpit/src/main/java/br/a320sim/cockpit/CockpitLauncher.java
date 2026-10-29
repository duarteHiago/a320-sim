package br.a320sim.cockpit;

import br.a320sim.cockpit.screen.RetroScreen;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

public final class CockpitLauncher {

    private CockpitLauncher() {
    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("A320 SIM");
        config.setWindowedMode(RetroScreen.WIDTH * 2, RetroScreen.HEIGHT * 2);
        config.setForegroundFPS(60);
        config.useVsync(true);
        new Lwjgl3Application(new SimApp(), config);
    }
}
