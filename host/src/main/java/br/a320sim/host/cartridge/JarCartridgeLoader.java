package br.a320sim.host.cartridge;

import br.a320sim.core.cartridge.CartridgeRejectedException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * "Data loading": copia o .jar do cartão para o cache do host e abre um class loader só dele.
 * <p>
 * A cópia é o que um avião de verdade faz (o software vai do cartão para a memória da caixa) e,
 * de quebra, o Windows não trava o arquivo no SD, então o cartão pode ser removido a qualquer hora.
 * O cache é indexado pelo hash do conteúdo: regravar o cartão com outra versão gera outro class loader.
 */
public class JarCartridgeLoader implements AutoCloseable {

    /** Pacote do software das caixas: estas classes vêm sempre do .jar, nunca do programa. */
    static final String CARTRIDGE_PACKAGE = "br.a320sim.cartridges.";

    private final Path cacheDir;
    private final Map<String, URLClassLoader> byHash = new HashMap<>();

    public JarCartridgeLoader(Path cacheDir) {
        this.cacheDir = cacheDir;
    }

    /**
     * @param expectedSha256 o hash que a assinatura garantiu; os bytes lidos aqui têm que bater com ele
     *                       (o cartão pode ter sido trocado entre a verificação e a cópia)
     */
    public synchronized ClassLoader classLoaderFor(String partNumber, Path jarOnCard, String expectedSha256) {
        try {
            byte[] bytes = Files.readAllBytes(jarOnCard);
            String fullHash = sha256(bytes);
            if (!fullHash.equals(expectedSha256)) {
                throw new CartridgeRejectedException(CartridgeRejectedException.Reason.ARQUIVO_CORROMPIDO,
                        jarOnCard.getFileName() + " mudou depois da verificação");
            }
            String hash = fullHash.substring(0, 12);
            URLClassLoader cached = byHash.get(hash);
            if (cached != null) {
                return cached;
            }
            Files.createDirectories(cacheDir);
            Path copy = cacheDir.resolve(partNumber + "-" + hash + ".jar");
            if (!Files.isRegularFile(copy) || Files.size(copy) != bytes.length) {
                Path temp = Files.createTempFile(cacheDir, partNumber, ".tmp");
                Files.write(temp, bytes);
                Files.move(temp, copy, StandardCopyOption.REPLACE_EXISTING);
            }
            URLClassLoader loader = new CartridgeClassLoader(copy.toUri().toURL(), getClass().getClassLoader());
            byHash.put(hash, loader);
            return loader;
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha no DATA LOADING de " + jarOnCard, e);
        }
    }

    /** Fecha os .jar do cache (as caixas já carregadas deixam de poder carregar classes novas). */
    @Override
    public synchronized void close() {
        for (URLClassLoader loader : byHash.values()) {
            try {
                loader.close();
            } catch (IOException e) {
                System.err.println("[SD] " + e.getMessage());
            }
        }
        byHash.clear();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Class loader "filho primeiro" para o pacote das caixas: mesmo que o programa também tenha essas
     * classes (modo dev), as do cartão ganham. O resto (core, Java) vem do pai, para que
     * AvionicsModule e DataBus sejam as mesmas classes dos dois lados.
     */
    static final class CartridgeClassLoader extends URLClassLoader {

        static {
            registerAsParallelCapable();
        }

        CartridgeClassLoader(URL jar, ClassLoader parent) {
            super(new URL[]{jar}, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith(CARTRIDGE_PACKAGE)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> type = findLoadedClass(name);
                if (type == null) {
                    type = findClass(name);
                }
                if (resolve) {
                    resolveClass(type);
                }
                return type;
            }
        }
    }
}
