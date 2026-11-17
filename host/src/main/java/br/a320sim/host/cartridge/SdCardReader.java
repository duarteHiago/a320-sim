package br.a320sim.host.cartridge;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Os leitores SD de verdade: cada unidade (G:\, H:\...) que tiver um cartridge.json na raiz é um cartucho.
 * O grupo vem do manifesto, então não importa em qual leitor do hub o cartão entrou.
 * <p>
 * {@code -Dsim.sd.drives=G,H,I} limita a busca a essas letras; sem isso, olha todas as unidades
 * (é só um "existe cartridge.json?" por unidade, barato o suficiente para repetir a cada segundo).
 * {@code -Dsim.sd.roots=pastaA;pastaB} troca as unidades por pastas que fingem ser leitores
 * (para testar sem hardware: copie uma imagem de cartridges/build/sd/ para dentro e apague para "arrancar").
 */
public class SdCardReader implements CartridgeReader {

    private final Supplier<List<Path>> roots;

    public SdCardReader() {
        this(SdCardReader::systemRoots);
    }

    /** Para testes: qualquer lista de pastas fazendo papel de raiz de cartão. */
    public SdCardReader(Supplier<List<Path>> roots) {
        this.roots = roots;
    }

    @Override
    public List<DetectedCartridge> scan() {
        List<DetectedCartridge> found = new ArrayList<>();
        for (Path root : roots.get()) {
            Path file = root.resolve(ManifestParser.MANIFEST_FILE);
            try {
                if (Files.isRegularFile(file)) {
                    found.add(new DetectedCartridge(ManifestParser.parse(file), root, true));
                }
            } catch (IOException | RuntimeException e) {
                // Cartão sendo arrancado no meio da leitura, ou manifesto corrompido: ignora nesta volta
                System.err.println("[SD] " + root + ": " + e.getMessage());
            }
        }
        return found;
    }

    private static List<Path> systemRoots() {
        String fakeRoots = System.getProperty("sim.sd.roots", "").trim();
        if (!fakeRoots.isEmpty()) {
            return Arrays.stream(fakeRoots.split(";")).filter(s -> !s.isBlank()).map(Path::of).toList();
        }
        Set<String> allowed = allowedDrives();
        List<Path> result = new ArrayList<>();
        for (Path root : FileSystems.getDefault().getRootDirectories()) {
            String letter = root.toString().substring(0, 1).toUpperCase(Locale.ROOT);
            if (allowed.isEmpty() || allowed.contains(letter)) {
                result.add(root);
            }
        }
        return result;
    }

    private static Set<String> allowedDrives() {
        String drives = System.getProperty("sim.sd.drives", "").trim();
        return Arrays.stream(drives.split("[,;\\s]+"))
                .filter(s -> !s.isBlank())
                .map(s -> s.substring(0, 1).toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }
}
