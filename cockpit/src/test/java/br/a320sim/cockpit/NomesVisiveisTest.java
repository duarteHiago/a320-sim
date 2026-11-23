package br.a320sim.cockpit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O simulador se chama AVION: os nomes antigos (A320 e SICA) não podem voltar para os textos do programa.
 * Varre os textos entre aspas (e os text blocks) de src/main do cockpit e do host; "A320" só vale
 * dentro de um part number, como PLT-A320-CEO.
 */
class NomesVisiveisTest {

    private static final Pattern PART_NUMBER = Pattern.compile("(PLT|PRP|CKP)-A320-(CEO|NEO)");
    private static final Pattern TEXT_BLOCK = Pattern.compile("\"\"\"(.*?)\"\"\"", Pattern.DOTALL);
    private static final Pattern STRING = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");
    /** A320 em qualquer lugar; SICA só como palavra inteira (MUSICA e FISICA podem). */
    private static final Pattern OLD_NAME = Pattern.compile("A320|(?i:\\bSICA\\b)");

    /** Raiz do projeto: vem do Gradle; sem ele, sobe as pastas até achar o settings.gradle. */
    static Path projectRoot() {
        String property = System.getProperty("sim.projectRoot");
        if (property != null) {
            return Path.of(property);
        }
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isRegularFile(dir.resolve("settings.gradle"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("settings.gradle nao encontrado acima de " + Path.of("").toAbsolutePath());
        }
        return dir;
    }

    /** Textos entre aspas do código, com o número da linha onde cada um começa. */
    static List<String> violations(String fileName, String source) {
        List<String> found = new ArrayList<>();
        StringBuilder rest = new StringBuilder(source);
        Matcher blocks = TEXT_BLOCK.matcher(source);
        while (blocks.find()) {
            check(fileName, source, blocks.start(), blocks.group(1), found);
            // Apaga o text block (mantendo as quebras de linha) para não ser lido de novo como aspas simples
            for (int i = blocks.start(); i < blocks.end(); i++) {
                if (rest.charAt(i) != '\n') {
                    rest.setCharAt(i, ' ');
                }
            }
        }
        Matcher strings = STRING.matcher(rest);
        while (strings.find()) {
            check(fileName, source, strings.start(), strings.group(1), found);
        }
        return found;
    }

    private static void check(String fileName, String source, int offset, String text, List<String> found) {
        if (OLD_NAME.matcher(PART_NUMBER.matcher(text).replaceAll("")).find()) {
            long line = source.substring(0, offset).chars().filter(c -> c == '\n').count() + 1;
            found.add(fileName + ":" + line + "  \"" + text.strip() + "\"");
        }
    }

    @Test
    void nenhumTextoDoProgramaUsaONomeAntigo() throws IOException {
        Path root = projectRoot();
        List<String> found = new ArrayList<>();
        int files = 0;
        for (String module : List.of("cockpit", "host")) {
            Path main = root.resolve(module).resolve("src/main");
            assertTrue(Files.isDirectory(main), "pasta nao encontrada: " + main);
            try (Stream<Path> paths = Files.walk(main)) {
                for (Path file : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                    files++;
                    found.addAll(violations(root.relativize(file).toString(), read(file)));
                }
            }
        }
        assertTrue(files > 50, "leu so " + files + " arquivos: a raiz " + root + " esta certa?");
        assertEquals(List.of(), found, "Textos com o nome antigo (use AVION / JATO BIMOTOR):\n" + String.join("\n", found));
    }

    @Test
    void aceitaPartNumberERecusaONomeAntigo() {
        String source = """
                class X {
                    String ok = "CARTUCHO PLT-A320-CEO CARREGADO";
                    String pacote = "br.a320sim.cartridges.";
                    String velho = "MONTE SEU A320";
                    String banner = \"""
                        A320 SIM
                        \""";
                    String musica = "MUSICA DE FUNDO";
                    String tag = "SICA";
                    String shot = "sica-123.png";
                }
                """;
        // Os text blocks são lidos primeiro, depois os textos entre aspas
        assertEquals(List.of("X.java:5  \"A320 SIM\"", "X.java:4  \"MONTE SEU A320\"",
                "X.java:9  \"SICA\"", "X.java:10  \"sica-123.png\""), violations("X.java", source));
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
