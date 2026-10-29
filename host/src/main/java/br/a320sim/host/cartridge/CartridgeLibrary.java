package br.a320sim.host.cartridge;

import br.a320sim.core.cartridge.Cartridge;
import br.a320sim.core.cartridge.CartridgeManifest;

import java.util.List;
import java.util.Optional;

/** A "gaveta": junta quem encontra os cartuchos com quem os carrega. */
public class CartridgeLibrary {

    private final CartridgeReader reader;
    private final CartridgeLoader loader;

    public CartridgeLibrary(CartridgeReader reader, CartridgeLoader loader) {
        this.reader = reader;
        this.loader = loader;
    }

    public List<CartridgeManifest> available() {
        return reader.scan();
    }

    public Optional<CartridgeManifest> find(String partNumber) {
        return available().stream()
                .filter(m -> m.partNumber().equalsIgnoreCase(partNumber))
                .findFirst();
    }

    public Cartridge load(CartridgeManifest manifest) {
        return loader.load(manifest);
    }
}
