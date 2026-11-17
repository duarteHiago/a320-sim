package br.a320sim.host.cartridge;

import br.a320sim.core.cartridge.Cartridge;
import br.a320sim.core.cartridge.CartridgeManifest;
import br.a320sim.core.module.AvionicsModule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Transforma um cartucho detectado em um cartucho com as caixas instanciadas.
 * Se o manifesto aponta um .jar, as classes vêm dele; senão, do próprio programa.
 */
public class CartridgeLoader {

    private final ModuleFactory classpath;
    private final JarCartridgeLoader jars;

    public CartridgeLoader(ModuleFactory classpath, JarCartridgeLoader jars) {
        this.classpath = classpath;
        this.jars = jars;
    }

    public CartridgeLoader(ModuleFactory classpath) {
        this(classpath, new JarCartridgeLoader(Path.of("cache", "cartridges")));
    }

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
            return classpath;
        }
        Path jar = detected.root().resolve(jarName);
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException("Cartão " + detected.partNumber() + " sem o software (" + jarName + ")");
        }
        return new ClasspathModuleFactory(jars.classLoaderFor(detected.partNumber(), jar));
    }
}
