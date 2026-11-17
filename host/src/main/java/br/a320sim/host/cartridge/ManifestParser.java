package br.a320sim.host.cartridge;

import br.a320sim.core.cartridge.CartridgeManifest;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Lê um cartridge.json e confere os campos obrigatórios. */
final class ManifestParser {

    static final String MANIFEST_FILE = "cartridge.json";

    private static final Gson GSON = new Gson();

    private ManifestParser() {
    }

    static CartridgeManifest parse(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            CartridgeManifest manifest = GSON.fromJson(reader, CartridgeManifest.class);
            if (manifest == null || manifest.partNumber() == null || manifest.grupo() == null
                    || manifest.versao() == null || manifest.modulos().isEmpty()) {
                throw new IOException("Manifesto incompleto: " + file);
            }
            return manifest;
        } catch (JsonParseException e) {
            throw new IOException("Manifesto inválido: " + file, e);
        }
    }
}
