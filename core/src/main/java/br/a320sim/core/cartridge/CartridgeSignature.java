package br.a320sim.core.cartridge;

import br.a320sim.core.cartridge.CartridgeRejectedException.Reason;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Integridade e autenticidade dos cartões, como o data loading de um avião de verdade.
 * <p>
 * O cartão leva, ao lado do software:
 * <ul>
 *   <li>{@code checksum.sha256}: o SHA-256 de cada arquivo (mesmo formato do comando {@code sha256sum});</li>
 *   <li>{@code assinatura.sig}: a assinatura Ed25519 do checksum, feita com a chave privada da "fábrica".</li>
 * </ul>
 * O simulador só tem a chave pública. Assinatura válida prova que o checksum veio da fábrica
 * (autenticidade); os hashes conferindo provam que nenhum arquivo mudou depois (integridade).
 * Sem a chave privada ninguém consegue gerar um cartão aceito.
 */
public final class CartridgeSignature {

    public static final String CHECKSUM_FILE = "checksum.sha256";
    public static final String SIGNATURE_FILE = "assinatura.sig";
    private static final String ALGORITHM = "Ed25519";

    private CartridgeSignature() {
    }

    // ------------------------------------------------------------------------------------------
    // Chaves
    // ------------------------------------------------------------------------------------------

    public static KeyPair generateKeyPair() {
        try {
            return KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String encode(PublicKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static String encode(PrivateKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static PublicKey decodePublic(String base64) {
        try {
            byte[] der = Base64.getDecoder().decode(base64.strip());
            return KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Chave pública inválida", e);
        }
    }

    public static PrivateKey decodePrivate(String base64) {
        try {
            byte[] der = Base64.getDecoder().decode(base64.strip());
            return KeyFactory.getInstance(ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Chave privada inválida", e);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Assinar (fábrica) e verificar (simulador)
    // ------------------------------------------------------------------------------------------

    /** Grava checksum.sha256 e assinatura.sig na pasta do cartão, cobrindo os arquivos indicados. */
    public static void sign(Path cardDir, PrivateKey key, String... files) throws IOException {
        StringBuilder checksum = new StringBuilder();
        for (String name : Arrays.stream(files).sorted().distinct().toList()) {
            requirePlainName(name);
            checksum.append(sha256(Files.readAllBytes(cardDir.resolve(name)))).append("  ").append(name).append('\n');
        }
        byte[] checksumBytes = checksum.toString().getBytes(StandardCharsets.UTF_8);
        try {
            Signature signer = Signature.getInstance(ALGORITHM);
            signer.initSign(key);
            signer.update(checksumBytes);
            String signature = Base64.getEncoder().encodeToString(signer.sign());
            Files.write(cardDir.resolve(CHECKSUM_FILE), checksumBytes);
            Files.writeString(cardDir.resolve(SIGNATURE_FILE), signature + "\n", StandardCharsets.US_ASCII);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Confere a assinatura e os hashes. Os {@code required} (ex.: cartridge.json e caixas.jar) precisam
     * estar listados no checksum: um checksum assinado que não cubra o software não serve.
     *
     * @return o SHA-256 (hex) de cada arquivo verificado, para quem for ler o arquivo de novo conferir
     * @throws CartridgeRejectedException com o motivo, se algo não bater
     */
    public static Map<String, String> verify(Path cardDir, PublicKey trusted, String... required) {
        if (trusted == null) {
            throw new CartridgeRejectedException(Reason.SEM_CHAVE_PUBLICA,
                    "o simulador não tem a chave pública da fábrica (rode gradlew :cartridges:gerarChaves)");
        }
        Path checksumFile = cardDir.resolve(CHECKSUM_FILE);
        Path signatureFile = cardDir.resolve(SIGNATURE_FILE);
        if (!Files.isRegularFile(checksumFile) || !Files.isRegularFile(signatureFile)) {
            throw new CartridgeRejectedException(Reason.SEM_ASSINATURA, "faltam " + CHECKSUM_FILE + " ou " + SIGNATURE_FILE);
        }

        byte[] checksumBytes;
        try {
            checksumBytes = Files.readAllBytes(checksumFile);
            byte[] signature = Base64.getDecoder().decode(Files.readString(signatureFile, StandardCharsets.US_ASCII).strip());
            Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(trusted);
            verifier.update(checksumBytes);
            if (!verifier.verify(signature)) {
                throw new CartridgeRejectedException(Reason.ASSINATURA_INVALIDA, "o cartão não foi assinado pela fábrica");
            }
        } catch (IOException e) {
            throw new CartridgeRejectedException(Reason.ARQUIVO_FALTANDO, e.getMessage());
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new CartridgeRejectedException(Reason.ASSINATURA_INVALIDA, "assinatura ilegível");
        }

        // A partir daqui o checksum é confiável: só falta ver se os arquivos ainda batem com ele
        Map<String, String> expected = parseChecksum(new String(checksumBytes, StandardCharsets.UTF_8));
        for (String name : required) {
            if (!expected.containsKey(name)) {
                throw new CartridgeRejectedException(Reason.ASSINATURA_INVALIDA, name + " não está coberto pela assinatura");
            }
        }
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            Path file = cardDir.resolve(entry.getKey());
            String actual;
            try {
                actual = sha256(Files.readAllBytes(file));
            } catch (IOException e) {
                throw new CartridgeRejectedException(Reason.ARQUIVO_FALTANDO, entry.getKey());
            }
            if (!actual.equals(entry.getValue())) {
                throw new CartridgeRejectedException(Reason.ARQUIVO_CORROMPIDO, entry.getKey() + " foi alterado");
            }
        }
        return expected;
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, String> parseChecksum(String text) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : text.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            int space = line.indexOf("  ");
            if (space != 64) {
                throw new CartridgeRejectedException(Reason.ARQUIVO_CORROMPIDO, "linha inválida no " + CHECKSUM_FILE);
            }
            String name = line.substring(space + 2).strip();
            requirePlainName(name);
            if (result.put(name, line.substring(0, space).toLowerCase()) != null) {
                throw new CartridgeRejectedException(Reason.ARQUIVO_CORROMPIDO, name + " aparece duas vezes");
            }
        }
        return result;
    }

    /** Só nomes de arquivo na raiz do cartão: nada de pastas, "..", nem caminhos absolutos. */
    private static void requirePlainName(String name) {
        if (name.isEmpty() || name.contains("/") || name.contains("\\") || name.contains("..") || name.contains(":")) {
            throw new CartridgeRejectedException(Reason.ARQUIVO_CORROMPIDO, "nome de arquivo inválido: " + name);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Linha de comando (usada pelas tarefas do Gradle)
    // ------------------------------------------------------------------------------------------

    /**
     * {@code gerar-chaves <privada> <publica>}: cria o par de chaves (recusa sobrescrever a privada).<br>
     * {@code assinar <pastaDoCartao> <privada> <arquivo>...}: assina a imagem de um cartão.
     */
    public static void main(String[] args) throws IOException {
        if (args.length >= 3 && args[0].equals("gerar-chaves")) {
            Path privateFile = Path.of(args[1]);
            Path publicFile = Path.of(args[2]);
            if (Files.exists(privateFile)) {
                System.err.println("Já existe " + privateFile + ". Apague só se quiser invalidar TODOS os cartões gravados.");
                System.exit(1);
            }
            KeyPair pair = generateKeyPair();
            Files.createDirectories(privateFile.toAbsolutePath().getParent());
            Files.createDirectories(publicFile.toAbsolutePath().getParent());
            Files.writeString(privateFile, encode(pair.getPrivate()) + "\n");
            Files.writeString(publicFile, encode(pair.getPublic()) + "\n");
            System.out.println("Chave privada: " + privateFile + "  (NÃO vai para o Git; guarde uma cópia)");
            System.out.println("Chave pública: " + publicFile + "  (vai para o Git, dentro do programa)");
        } else if (args.length >= 4 && args[0].equals("assinar")) {
            Path card = Path.of(args[1]);
            Path privateFile = Path.of(args[2]);
            if (!Files.isRegularFile(privateFile)) {
                System.err.println("Sem a chave privada (" + privateFile + "): a imagem " + card.getFileName()
                        + " ficou SEM ASSINATURA e o simulador vai recusá-la. Rode gradlew :cartridges:gerarChaves"
                        + " ou peça a chave a quem grava os cartões.");
                return;
            }
            sign(card, decodePrivate(Files.readString(privateFile)), Arrays.copyOfRange(args, 3, args.length));
            System.out.println("Assinado: " + card.getFileName());
        } else {
            System.err.println("Uso: gerar-chaves <privada> <publica> | assinar <pasta> <privada> <arquivo>...");
            System.exit(2);
        }
    }
}
