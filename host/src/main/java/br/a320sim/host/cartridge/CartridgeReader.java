package br.a320sim.host.cartridge;

import br.a320sim.core.cartridge.CartridgeManifest;

import java.util.List;

/**
 * Fonte de cartuchos disponíveis.
 * Fase 0: pastas em sd-dev/ fingem ser cartões. Fase 3: leitores SD reais.
 */
public interface CartridgeReader {

    List<CartridgeManifest> scan();
}
