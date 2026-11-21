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
import java.util.List;
import java.util.Map;

/**
 * Transforma um cartucho detectado em um cartucho com as caixas instanciadas.
 * Se o manifesto aponta um .jar, as classes vêm dele; senão, do próprio programa.
 * <p>
 * Antes de carregar qualquer classe de um cartão, confere a assinatura da fábrica e o checksum de
 * cartridge.json e do .jar ({@link CartridgeSignature}). Cartão sem assinatura válida não roda código.
 */
public class CartridgeLoader {

    private final ModuleFactory classpath;
    private final JarCartridgeLoader jars;
    private final PublicKey trusted;

    public CartridgeLoader(ModuleFactory classpath, JarCartridgeLoader jars, PublicKey trusted) {
        this.classpath = classpath;
        this.jars = jars;
        this.trusted = trusted;
    }

    public CartridgeLoader(ModuleFactory classpath, JarCartridgeLoader jars) {
        this(classpath, jars, CartridgeTrust.factoryKey());
    }

    public CartridgeLoader(ModuleFactory classpath) {
        this(classpath, new JarCartridgeLoader(Path.of("cache", "cartridges")));
    }

    /**
     * @throws CartridgeRejectedException se a assinatura ou o checksum não baterem
     */
    public Cartridge load(DetectedCartridge detected) {
        CartridgeManifest manifest = detected.manifest();
        ModuleFactory factory = factoryFor(detected);
        List<AvionicsModule> modules = manifest.modulos().stream()
                .map(factory::create)
                .toList();
        return new Cartridge(manifest, modules, detected.location(), detected.removable());
    }

    private ModuleFactory factoryFor(DetectedCartridge detected) {
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
        return new ClasspathModuleFactory(jars.classLoaderFor(detected.partNumber(), jar, hashes.get(jarName)));
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
