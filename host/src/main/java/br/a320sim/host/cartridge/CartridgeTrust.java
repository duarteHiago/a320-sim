package br.a320sim.host.cartridge;

import br.a320sim.core.cartridge.CartridgeSignature;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;

/**
 * De onde vem a chave pública que o simulador usa para aceitar cartões.
 * <p>
 * Padrão: o recurso {@code chave-publica-cartuchos.txt}, que vai dentro do programa
 * (gerado por {@code gradlew :cartridges:gerarChaves}). {@code -Dsim.chavePublica=arquivo} troca por outro arquivo.
 */
public final class CartridgeTrust {

    static final String RESOURCE = "/chave-publica-cartuchos.txt";

    private CartridgeTrust() {
    }

    /** @return a chave da fábrica, ou {@code null} se o programa foi compilado sem ela (todo cartão será recusado) */
    public static PublicKey factoryKey() {
        try {
            String path = System.getProperty("sim.chavePublica", "").trim();
            if (!path.isEmpty()) {
                return CartridgeSignature.decodePublic(Files.readString(Path.of(path)));
            }
            try (InputStream in = CartridgeTrust.class.getResourceAsStream(RESOURCE)) {
                if (in == null) {
                    System.err.println("[SD] sem chave pública no programa: cartões SD serão recusados."
                            + " Rode gradlew :cartridges:gerarChaves");
                    return null;
                }
                return CartridgeSignature.decodePublic(new String(in.readAllBytes(), StandardCharsets.US_ASCII));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
