package br.a320sim.host.cartridge;

import br.a320sim.core.cartridge.CartridgeManifest;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Lê cada subpasta de uma pasta raiz como se fosse um cartão SD com cartridge.json. */
public class DevFolderReader implements CartridgeReader {

    private static final String MANIFEST_FILE = "cartridge.json";

    private final Path root;
    private final Gson gson = new Gson();

    public DevFolderReader(Path root) {
        this.root = root;
    }

    @Override
    public List<CartridgeManifest> scan() {
        List<CartridgeManifest> found = new ArrayList<>();
        try (Stream<Path> folders = Files.list(root)) {
            folders.map(folder -> folder.resolve(MANIFEST_FILE))
                    .filter(Files::isRegularFile)
                    .sorted()
                    .forEach(file -> found.add(parse(file)));
        } catch (IOException e) {
            throw new IllegalStateException("Não consegui ler " + root, e);
        }
        return found;
    }

    private CartridgeManifest parse(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, CartridgeManifest.class);
        } catch (IOException | JsonParseException e) {
            throw new IllegalStateException("Manifesto inválido: " + file, e);
        }
    }
}
