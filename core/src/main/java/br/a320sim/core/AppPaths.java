package br.a320sim.core;

import java.nio.file.Path;
import java.util.function.Function;

/**
 * Onde o programa guarda e lê arquivos.
 * <p>
 * Em desenvolvimento (gradlew run), tudo é relativo à pasta do projeto, como sempre foi. Instalado
 * (pelo instalador do jpackage, em %LOCALAPPDATA%\SICA), a pasta do programa não guarda dados do usuário:
 * o banco e o cache dos cartões vão para %APPDATA%\SICA e os screenshots para Imagens\SICA.
 * O lançador do jpackage define a propriedade {@code jpackage.app-path} (o caminho do SICA.exe):
 * é assim que o programa sabe que está instalado.
 */
public final class AppPaths {

    /** Definida pelo lançador do jpackage: caminho do executável. */
    public static final String APP_PATH_PROPERTY = "jpackage.app-path";
    public static final String APP_FOLDER = "SICA";

    private static final AppPaths CURRENT = new AppPaths(System::getProperty, System::getenv);

    private final Function<String, String> properties;
    private final Function<String, String> environment;

    /** Para testes: propriedades e variáveis de ambiente de mentira. */
    public AppPaths(Function<String, String> properties, Function<String, String> environment) {
        this.properties = properties;
        this.environment = environment;
    }

    public static AppPaths current() {
        return CURRENT;
    }

    public boolean installed() {
        return properties.apply(APP_PATH_PROPERTY) != null;
    }

    /** Pasta do programa instalado (onde está o SICA.exe); em desenvolvimento, a pasta atual. */
    public Path appDir() {
        String exe = properties.apply(APP_PATH_PROPERTY);
        return exe == null ? Path.of("").toAbsolutePath() : Path.of(exe).toAbsolutePath().getParent();
    }

    /** Arquivo do banco, sem extensão ({@code -Dsim.db} sempre tem prioridade). */
    public Path database() {
        String custom = properties.apply("sim.db");
        if (custom != null && !custom.isBlank()) {
            return Path.of(custom);
        }
        return installed() ? userFolder("APPDATA").resolve("sica") : Path.of("dados", "sica");
    }

    /** Cópias dos .jar lidos dos cartões (data loading). Instalado, junto do banco, fora da pasta do programa. */
    public Path cartridgeCache() {
        return installed() ? userFolder("APPDATA").resolve("cache") : Path.of("cache", "cartridges");
    }

    /** Screenshots (F12). */
    public Path screenshots() {
        return installed() ? home().resolve("Pictures").resolve(APP_FOLDER) : Path.of("screenshots");
    }

    /** As pastas que fingem ser cartões (sem cartão SD plugado). Instalado, vão junto com o programa. */
    public Path devCartridges() {
        return installed() ? appDir().resolve("sd-dev") : Path.of("sd-dev");
    }

    /** %APPDATA%\SICA; sem a variável (Linux, testes), ~/.sica. */
    private Path userFolder(String variable) {
        String base = environment.apply(variable);
        return base == null || base.isBlank() ? home().resolve(".sica") : Path.of(base).resolve(APP_FOLDER);
    }

    private Path home() {
        String home = properties.apply("user.home");
        return Path.of(home == null ? "." : home);
    }
}
