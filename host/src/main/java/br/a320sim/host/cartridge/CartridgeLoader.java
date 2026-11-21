package br.a320sim.host.cartridge;

import br.a320sim.core.cartridge.Cartridge;
import br.a320sim.core.cartridge.CartridgeManifest;
import br.a320sim.core.cartridge.CartridgeRejectedException;
import br.a320sim.core.cartridge.CartridgeSignature;
import br.a320sim.core.module.AvionicsModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Transforma um cartucho detectado em um cartucho com as caixas instanciadas.
 * Se o manifesto aponta um .jar, as classes vêm dele; senão, do próprio programa.
 * <p>
 * Antes de carregar qualquer classe de um cartão, confere a assinatura da fábrica e o checksum de
 * cartridge.json e do .jar ({@link CartridgeSignature}). Cartão sem assinatura válida não roda código.
 * Depois da assinatura, a {@link CardPolicy} ainda pode recusar um cartão bloqueado no cadastro.
 */
public class CartridgeLoader {

    private final ModuleFactory classpath;
    private final JarCartridgeLoader jars;
    private final PublicKey trusted;
    private final CardPolicy policy;

    public CartridgeLoader(ModuleFactory classpath, JarCartridgeLoader jars, PublicKey trusted, CardPolicy policy) {
        this.classpath = classpath;
        this.jars = jars;
        this.trusted = trusted;
        this.policy = policy;
    }

    public CartridgeLoader(ModuleFactory classpath, JarCartridgeLoader jars, PublicKey trusted) {
        this(classpath, jars, trusted, CardPolicy.ACCEPT_ALL);
    }

    public CartridgeLoader(ModuleFactory classpath, JarCartridgeLoader jars) {
        this(classpath, jars, CartridgeTrust.factoryKey());
    }

    public CartridgeLoader(ModuleFactory classpath) {
        this(classpath, CardPolicy.ACCEPT_ALL);
    }

    public CartridgeLoader(ModuleFactory classpath, CardPolicy policy) {
        this(classpath, new JarCartridgeLoader(Path.of("cache", "cartridges")), CartridgeTrust.factoryKey(), policy);
    }

    /** Carga sem contar como leitura nova (ex.: testes, ou o cartão já foi lido nesta inserção). */
    public Cartridge load(DetectedCartridge detected) {
        return load(detected, false);
    }

    /**
     * @param newInsertion primeira carga depois que o cartão entrou no leitor (repassado à {@link CardPolicy})
     * @throws CartridgeRejectedException se a assinatura ou o checksum não baterem, ou o cartão estiver bloqueado
     */
    public Cartridge load(DetectedCartridge detected, boolean newInsertion) {
        CartridgeManifest manifest = detected.manifest();
        ModuleFactory factory = factoryFor(detected, newInsertion);
        List<AvionicsModule> modules = manifest.modulos().stream()
                .map(factory::create)
                .toList();
        return new Cartridge(manifest, modules, detected.location(), detected.removable());
    }

    private ModuleFactory factoryFor(DetectedCartridge detected, boolean newInsertion) {
        String jarName = detected.manifest().jar();
        if (jarName == null || jarName.isBlank()) {
            return classpath; // pastas sd-dev/: o software já está no programa, nada vem do cartão
        }
        Path jar = detected.root().resolve(jarName);
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException("Cartão " + detected.partNumber() + " sem o software (" + jarName + ")");
        }

        Map<String, String> hashes = CartridgeSignature.verify(detected.root(), trusted,
                ManifestParser.MANIFEST_FILE, jarName);
        requireSameManifest(detected);
        policy.check(new CardReading(detected.partNumber(), VolumeId.of(detected.root(), detected.partNumber()),
                hashes.get(jarName), recordedOn(jar)), newInsertion);
        return new ClasspathModuleFactory(jars.classLoaderFor(detected.partNumber(), jar, hashes.get(jarName)));
    }

    /** Data do .jar no cartão, que é quando ele foi gravado; nula se não der para ler. */
    private static LocalDate recordedOn(Path jar) {
        try {
            return LocalDate.ofInstant(Files.getLastModifiedTime(jar).toInstant(), ZoneId.systemDefault());
        } catch (IOException e) {
            return null;
        }
    }

    /** O manifesto lido na varredura tem que ser o mesmo que acabou de ser verificado. */
    private static void requireSameManifest(DetectedCartridge detected) {
        try {
            CartridgeManifest verified = ManifestParser.parse(detected.root().resolve(ManifestParser.MANIFEST_FILE));
            if (!verified.equals(detected.manifest())) {
                throw new CartridgeRejectedException(CartridgeRejectedException.Reason.ARQUIVO_CORROMPIDO,
                        "cartridge.json mudou durante a leitura");
            }
        } catch (IOException e) {
            throw new CartridgeRejectedException(CartridgeRejectedException.Reason.ARQUIVO_FALTANDO, e.getMessage());
        }
    }
}
