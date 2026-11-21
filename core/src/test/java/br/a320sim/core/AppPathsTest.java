package br.a320sim.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppPathsTest {

    private static final Map<String, String> WINDOWS = Map.of(
            "APPDATA", "C:\\Users\\aluno\\AppData\\Roaming",
            "LOCALAPPDATA", "C:\\Users\\aluno\\AppData\\Local");

    private static AppPaths paths(Map<String, String> properties) {
        return new AppPaths(properties::get, WINDOWS::get);
    }

    @Test
    void emDesenvolvimentoTudoFicaNaPastaDoProjeto() {
        AppPaths dev = paths(Map.of("user.home", "C:\\Users\\aluno"));
        assertFalse(dev.installed());
        assertEquals(Path.of("dados", "avion"), dev.database());
        assertEquals(Path.of("cache", "cartridges"), dev.cartridgeCache());
        assertEquals(Path.of("screenshots"), dev.screenshots());
        assertEquals(Path.of("sd-dev"), dev.devCartridges());
    }

    @Test
    void instaladoUsaAsPastasDoUsuario() {
        AppPaths installed = paths(Map.of("user.home", "C:\\Users\\aluno",
                AppPaths.APP_PATH_PROPERTY, "C:\\Users\\aluno\\AppData\\Local\\AVION\\AVION.exe"));
        assertTrue(installed.installed());
        assertEquals(Path.of("C:\\Users\\aluno\\AppData\\Roaming\\AVION\\avion"), installed.database());
        assertEquals(Path.of("C:\\Users\\aluno\\AppData\\Roaming\\AVION\\cache"), installed.cartridgeCache(),
                "fora da pasta do programa, que fica em %LOCALAPPDATA%\\AVION");
        assertEquals(Path.of("C:\\Users\\aluno\\Pictures\\AVION"), installed.screenshots());
        assertEquals(Path.of("C:\\Users\\aluno\\AppData\\Local\\AVION\\sd-dev"), installed.devCartridges());
    }

    @Test
    void simDbSempreTemPrioridade() {
        AppPaths installed = paths(Map.of("sim.db", "D:\\aula\\turma1", "user.home", "C:\\Users\\aluno",
                AppPaths.APP_PATH_PROPERTY, "C:\\AVION\\AVION.exe"));
        assertEquals(Path.of("D:\\aula\\turma1"), installed.database());
    }

    @Test
    void semAsVariaveisDoWindowsUsaUmaPastaNoHome() {
        AppPaths linux = new AppPaths(Map.of("user.home", "/home/aluno", AppPaths.APP_PATH_PROPERTY,
                "/opt/avion/bin/AVION")::get, name -> null);
        assertEquals(Path.of("/home/aluno", ".avion", "avion"), linux.database());
    }

    @Test
    void screenshotsVaoParaAPastaImagensDeVerdadeMesmoNoOneDrive() {
        Map<String, String> env = Map.of("USERPROFILE", "C:\\Users\\aluno", "APPDATA", "C:\\x");
        AppPaths installed = new AppPaths(Map.of("user.home", "C:\\Users\\aluno",
                AppPaths.APP_PATH_PROPERTY, "C:\\AVION\\AVION.exe")::get, env::get,
                () -> "%USERPROFILE%\\OneDrive\\Imagens");
        assertEquals(Path.of("C:\\Users\\aluno\\OneDrive\\Imagens\\AVION"), installed.screenshots());
    }

    @Test
    void pastaDoProgramaFicaAoLadoDoExe() {
        AppPaths installed = paths(Map.of("user.home", "C:\\Users\\aluno",
                AppPaths.APP_PATH_PROPERTY, "C:\\Users\\aluno\\AppData\\Local\\AVION\\AVION.exe"));
        assertEquals(Path.of("C:\\Users\\aluno\\AppData\\Local\\AVION"), installed.appDir());
    }
}
