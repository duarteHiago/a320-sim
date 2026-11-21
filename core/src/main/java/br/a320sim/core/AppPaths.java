package br.a320sim.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern REG_PICTURES = Pattern.compile("My Pictures\\s+REG_(?:EXPAND_)?SZ\\s+(.+)");
    private static final Pattern VARIABLE = Pattern.compile("%([^%]+)%");
    /** Só para expandir %VARIAVEIS% na leitura do registro. */
    private static final AppPaths CURRENT_ENV = new AppPaths(System::getProperty, System::getenv);
    private static final AppPaths CURRENT = new AppPaths(System::getProperty, System::getenv,
            AppPaths::windowsPicturesFolder);

    private final Function<String, String> properties;
    private final Function<String, String> environment;
    private final Supplier<String> picturesFolder;

    /**
     * Para testes: propriedades, variáveis de ambiente e a pasta Imagens do Windows (pode vir nula).
     */
    public AppPaths(Function<String, String> properties, Function<String, String> environment,
                    Supplier<String> picturesFolder) {
        this.properties = properties;
        this.environment = environment;
        this.picturesFolder = picturesFolder;
    }

    public AppPaths(Function<String, String> properties, Function<String, String> environment) {
        this(properties, environment, () -> null);
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

    /**
     * Screenshots (F12). Instalado, na pasta Imagens de verdade do usuário (que pode estar no OneDrive),
     * ou em ~/Pictures se não der para descobrir.
     */
    public Path screenshots() {
        if (!installed()) {
            return Path.of("screenshots");
        }
        String pictures = expand(picturesFolder.get());
        return (pictures == null || pictures.isBlank() ? home().resolve("Pictures") : Path.of(pictures))
                .resolve(APP_FOLDER);
    }

    /** Troca %USERPROFILE% e afins pelo valor da variável de ambiente. */
    private String expand(String path) {
        if (path == null) {
            return null;
        }
        Matcher m = VARIABLE.matcher(path);
        StringBuilder result = new StringBuilder();
        while (m.find()) {
            String value = environment.apply(m.group(1));
            m.appendReplacement(result, Matcher.quoteReplacement(value == null ? m.group() : value));
        }
        m.appendTail(result);
        return result.toString();
    }

    /**
     * A pasta Imagens do Windows, do registro do usuário (onde o Explorer guarda; pode ter sido movida
     * para o OneDrive). Lida com o reg.exe do próprio Windows; nula se não der.
     */
    static String windowsPicturesFolder() {
        if (!System.getProperty("os.name", "").startsWith("Windows")) {
            return null;
        }
        try {
            // Caminho completo: não confia no PATH para achar o reg.exe
            String systemRoot = System.getenv().getOrDefault("SystemRoot", "C:\\Windows");
            Process reg = new ProcessBuilder(Path.of(systemRoot, "System32", "reg.exe").toString(), "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\User Shell Folders",
                    "/v", "My Pictures").redirectErrorStream(true).start();
            String found = null;
            try (BufferedReader out = new BufferedReader(
                    new InputStreamReader(reg.getInputStream(), StandardCharsets.UTF_8))) {
                for (String line = out.readLine(); line != null; line = out.readLine()) {
                    Matcher m = REG_PICTURES.matcher(line.strip());
                    if (m.matches()) {
                        found = m.group(1).strip();
                    }
                }
            }
            reg.waitFor(5, TimeUnit.SECONDS);
            // O reg.exe escreve na página de código do console: com acento o caminho pode vir estragado.
            // Se a pasta lida não existir, melhor cair no ~/Pictures do que gravar num lugar estranho.
            String expanded = found == null ? null : CURRENT_ENV.expand(found);
            return expanded != null && Files.isDirectory(Path.of(expanded)) ? found : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
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
