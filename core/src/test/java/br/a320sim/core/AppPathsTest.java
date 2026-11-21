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
        assertEquals(Path.of("dados", "sica"), dev.database());
        assertEquals(Path.of("cache", "cartridges"), dev.cartridgeCache());
        assertEquals(Path.of("screenshots"), dev.screenshots());
        assertEquals(Path.of("sd-dev"), dev.devCartridges());
    }

    @Test
    void instaladoUsaAsPastasDoUsuario() {
        AppPaths installed = paths(Map.of("user.home", "C:\\Users\\aluno",
                AppPaths.APP_PATH_PROPERTY, "C:\\Users\\aluno\\AppData\\Local\\SICA\\SICA.exe"));
        assertTrue(installed.installed());
        assertEquals(Path.of("C:\\Users\\aluno\\AppData\\Roaming\\SICA\\sica"), installed.database());
        assertEquals(Path.of("C:\\Users\\aluno\\AppData\\Local\\SICA\\cache"), installed.cartridgeCache());
        assertEquals(Path.of("C:\\Users\\aluno\\Pictures\\SICA"), installed.screenshots());
        assertEquals(Path.of("C:\\Users\\aluno\\AppData\\Local\\SICA\\sd-dev"), installed.devCartridges());
    }

    @Test
    void simDbSempreTemPrioridade() {
        AppPaths installed = paths(Map.of("sim.db", "D:\\aula\\turma1", "user.home", "C:\\Users\\aluno",
                AppPaths.APP_PATH_PROPERTY, "C:\\SICA\\SICA.exe"));
        assertEquals(Path.of("D:\\aula\\turma1"), installed.database());
    }

    @Test
    void semAsVariaveisDoWindowsUsaUmaPastaNoHome() {
        AppPaths linux = new AppPaths(Map.of("user.home", "/home/aluno", AppPaths.APP_PATH_PROPERTY,
                "/opt/sica/bin/SICA")::get, name -> null);
        assertEquals(Path.of("/home/aluno", ".sica", "sica"), linux.database());
    }
}
