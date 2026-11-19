package br.a320sim.host.cartridge;

import br.a320sim.core.cartridge.CartridgeGroup;
import br.a320sim.core.cartridge.CartridgeManifest;

import java.nio.file.Path;

/**
 * Um cartucho encontrado por um leitor, ainda não carregado: o manifesto e a pasta (ou raiz do cartão)
 * onde ele está. {@code removable} = cartão físico, que pode sumir a qualquer momento.
 */
public record DetectedCartridge(CartridgeManifest manifest, Path root, boolean removable) {

    public String partNumber() {
        return manifest.partNumber();
    }

    public CartridgeGroup group() {
        return manifest.grupo();
    }

    public String location() {
        return root.toString();
    }
}
