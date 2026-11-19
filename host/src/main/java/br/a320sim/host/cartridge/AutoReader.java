package br.a320sim.host.cartridge;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Escolhe a fonte de cartuchos pela propriedade {@code -Dsim.cartridges}:
 * <ul>
 *   <li>{@code auto} (padrão): cartões SD; se não houver nenhum plugado, usa as pastas sd-dev/</li>
 *   <li>{@code sd}: só cartões SD</li>
 *   <li>{@code dev}: só sd-dev/</li>
 * </ul>
 */
public class AutoReader implements CartridgeReader {

    public enum Source { AUTO, SD, DEV }

    private final CartridgeReader sd;
    private final CartridgeReader dev;
    private final Source source;

    public AutoReader(CartridgeReader sd, CartridgeReader dev, Source source) {
        this.sd = sd;
        this.dev = dev;
        this.source = source;
    }

    public static AutoReader fromSystemProperties() {
        Source source = Source.valueOf(System.getProperty("sim.cartridges", "auto").toUpperCase(Locale.ROOT));
        return new AutoReader(new SdCardReader(), new DevFolderReader(Path.of("sd-dev")), source);
    }

    @Override
    public List<DetectedCartridge> scan() {
        return switch (source) {
            case SD -> sd.scan();
            case DEV -> dev.scan();
            case AUTO -> {
                List<DetectedCartridge> cards = sd.scan();
                yield cards.isEmpty() ? dev.scan() : cards;
            }
        };
    }
}
