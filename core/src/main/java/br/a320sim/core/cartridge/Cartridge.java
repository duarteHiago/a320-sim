package br.a320sim.core.cartridge;

import br.a320sim.core.module.AvionicsModule;

import java.util.List;

/**
 * Um cartucho carregado: o manifesto mais as caixas que ele contém (composição).
 * {@code location} diz de onde ele veio (ex.: "G:\" ou "sd-dev/PLT-A320-CEO");
 * {@code removable} indica um cartão físico, que pode ser arrancado do leitor.
 */
public final class Cartridge {

    private final CartridgeManifest manifest;
    private final List<AvionicsModule> modules;
    private final String location;
    private final boolean removable;

    public Cartridge(CartridgeManifest manifest, List<AvionicsModule> modules, String location, boolean removable) {
        this.manifest = manifest;
        this.modules = List.copyOf(modules);
        this.location = location;
        this.removable = removable;
    }

    public Cartridge(CartridgeManifest manifest, List<AvionicsModule> modules) {
        this(manifest, modules, "", false);
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

    public String location() {
        return location;
    }

    public boolean removable() {
        return removable;
    }

    @Override
    public String toString() {
        return manifest.partNumber() + " (" + manifest.nome() + ")";
    }
}
