package br.a320sim.host.cartridge;

import java.util.List;

/**
 * Fonte de cartuchos disponíveis: pastas em sd-dev/ ({@link DevFolderReader})
 * ou cartões SD de verdade nos leitores ({@link SdCardReader}).
 */
public interface CartridgeReader {

    List<DetectedCartridge> scan();
}
