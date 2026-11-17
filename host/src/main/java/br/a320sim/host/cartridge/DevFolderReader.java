package br.a320sim.host.cartridge;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Lê cada subpasta de uma pasta raiz como se fosse um cartão SD com cartridge.json. */
public class DevFolderReader implements CartridgeReader {

    private final Path root;

    public DevFolderReader(Path root) {
        this.root = root;
    }

    @Override
    public List<DetectedCartridge> scan() {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<DetectedCartridge> found = new ArrayList<>();
        try (Stream<Path> folders = Files.list(root)) {
            for (Path folder : folders.sorted().toList()) {
                Path file = folder.resolve(ManifestParser.MANIFEST_FILE);
                if (Files.isRegularFile(file)) {
                    found.add(new DetectedCartridge(ManifestParser.parse(file), folder, false));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Não consegui ler " + root, e);
        }
        return found;
    }
}
