package br.a320sim.host;

import br.a320sim.core.cartridge.CartridgeRejectedException;
import br.a320sim.core.cartridge.CartridgeRejectedException.Reason;
import br.a320sim.core.cartridge.CartridgeSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regras de integridade e autenticidade do cartão, sem simulador envolvido. */
class CartridgeSignatureTest {

    private static final KeyPair FACTORY = CartridgeSignature.generateKeyPair();

    @TempDir
    Path card;

    private void writeCard() throws IOException {
        Files.writeString(card.resolve("cartridge.json"), "{\"partNumber\":\"PLT-A320-CEO\"}");
        Files.write(card.resolve("caixas.jar"), new byte[]{1, 2, 3, 4});
    }

    private Reason rejection(String... required) {
        return assertThrows(CartridgeRejectedException.class,
                () -> CartridgeSignature.verify(card, FACTORY.getPublic(), required)).reason();
    }

    @Test
    void signedCardPassesAndChecksumUsesSha256sumFormat() throws IOException {
        writeCard();
        CartridgeSignature.sign(card, FACTORY.getPrivate(), "cartridge.json", "caixas.jar");

        Map<String, String> hashes = CartridgeSignature.verify(card, FACTORY.getPublic(), "cartridge.json", "caixas.jar");
        assertEquals(CartridgeSignature.sha256(new byte[]{1, 2, 3, 4}), hashes.get("caixas.jar"));
        String checksum = Files.readString(card.resolve(CartridgeSignature.CHECKSUM_FILE));
        assertTrue(checksum.contains("  caixas.jar\n"), checksum); // dá para conferir com "sha256sum -c"
    }

    @Test
    void editingTheChecksumBreaksTheSignature() throws IOException {
        writeCard();
        CartridgeSignature.sign(card, FACTORY.getPrivate(), "cartridge.json", "caixas.jar");
        // Quem altera o .jar e recalcula o checksum ainda não tem a chave privada para assinar de novo
        Files.write(card.resolve("caixas.jar"), new byte[]{9, 9, 9});
        String forged = CartridgeSignature.sha256(new byte[]{9, 9, 9}) + "  caixas.jar\n"
                + CartridgeSignature.sha256(Files.readAllBytes(card.resolve("cartridge.json"))) + "  cartridge.json\n";
        Files.writeString(card.resolve(CartridgeSignature.CHECKSUM_FILE), forged, StandardCharsets.UTF_8);
        assertEquals(Reason.ASSINATURA_INVALIDA, rejection("cartridge.json", "caixas.jar"));
    }

    @Test
    void signatureThatDoesNotCoverTheJarIsNotEnough() throws IOException {
        writeCard();
        CartridgeSignature.sign(card, FACTORY.getPrivate(), "cartridge.json");
        assertEquals(Reason.ASSINATURA_INVALIDA, rejection("cartridge.json", "caixas.jar"));
    }

    @Test
    void missingFileListedInChecksumIsRefused() throws IOException {
        writeCard();
        CartridgeSignature.sign(card, FACTORY.getPrivate(), "cartridge.json", "caixas.jar");
        Files.delete(card.resolve("caixas.jar"));
        assertEquals(Reason.ARQUIVO_FALTANDO, rejection("cartridge.json", "caixas.jar"));
    }

    @Test
    void simulatorWithoutPublicKeyRefusesEverything() throws IOException {
        writeCard();
        CartridgeSignature.sign(card, FACTORY.getPrivate(), "cartridge.json", "caixas.jar");
        CartridgeRejectedException e = assertThrows(CartridgeRejectedException.class,
                () -> CartridgeSignature.verify(card, null, "caixas.jar"));
        assertEquals(Reason.SEM_CHAVE_PUBLICA, e.reason());
    }

    @Test
    void keysSurviveTheTextFormatUsedOnDisk() {
        KeyPair copy = new KeyPair(
                CartridgeSignature.decodePublic(CartridgeSignature.encode(FACTORY.getPublic())),
                CartridgeSignature.decodePrivate(CartridgeSignature.encode(FACTORY.getPrivate())));
        assertEquals(FACTORY.getPublic(), copy.getPublic());
        assertEquals(FACTORY.getPrivate(), copy.getPrivate());
    }
}
