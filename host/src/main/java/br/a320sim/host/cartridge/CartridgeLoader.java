package br.a320sim.host.cartridge;

import br.a320sim.core.cartridge.Cartridge;
import br.a320sim.core.cartridge.CartridgeManifest;
import br.a320sim.core.module.AvionicsModule;

import java.util.List;

/** Transforma um manifesto em um cartucho com suas caixas instanciadas. */
public class CartridgeLoader {

    private final ModuleFactory factory;

    public CartridgeLoader(ModuleFactory factory) {
        this.factory = factory;
    }

    public Cartridge load(CartridgeManifest manifest) {
        List<AvionicsModule> modules = manifest.modulos().stream()
                .map(factory::create)
                .toList();
        return new Cartridge(manifest, modules);
    }
}
