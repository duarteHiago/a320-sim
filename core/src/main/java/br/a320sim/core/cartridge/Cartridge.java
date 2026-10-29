package br.a320sim.core.cartridge;

import br.a320sim.core.module.AvionicsModule;

import java.util.List;

/** Um cartucho carregado: o manifesto mais as caixas que ele contém (composição). */
public final class Cartridge {

    private final CartridgeManifest manifest;
    private final List<AvionicsModule> modules;

    public Cartridge(CartridgeManifest manifest, List<AvionicsModule> modules) {
        this.manifest = manifest;
        this.modules = List.copyOf(modules);
    }

    public CartridgeManifest manifest() {
        return manifest;
    }

    public List<AvionicsModule> modules() {
        return modules;
    }

    public CartridgeGroup group() {
        return manifest.grupo();
    }

    public Generation generation() {
        return manifest.versao();
    }

    @Override
    public String toString() {
        return manifest.partNumber() + " (" + manifest.nome() + ")";
    }
}
