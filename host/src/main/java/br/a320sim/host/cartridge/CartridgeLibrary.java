package br.a320sim.host.cartridge;

import br.a320sim.core.cartridge.Cartridge;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A "gaveta": junta quem encontra os cartuchos com quem os carrega.
 * <p>
 * Com {@link #startWatching} uma thread de fundo varre os leitores periodicamente (o Java não avisa
 * quando um cartão entra ou sai), e as telas só comparam {@link #revision()} para saber se algo mudou.
 */
public class CartridgeLibrary implements AutoCloseable {

    private final CartridgeReader reader;
    private final CartridgeLoader loader;
    private volatile List<DetectedCartridge> current;
    private volatile int revision;
    private ScheduledExecutorService watcher;

    public CartridgeLibrary(CartridgeReader reader, CartridgeLoader loader) {
        this.reader = reader;
        this.loader = loader;
    }

    /** Cartuchos visíveis agora. Com o vigia ligado, é a última varredura; sem ele, varre na hora. */
    public List<DetectedCartridge> available() {
        List<DetectedCartridge> snapshot = current;
        return snapshot != null ? snapshot : rescan();
    }

    public Optional<DetectedCartridge> find(String partNumber) {
        return available().stream()
                .filter(c -> c.partNumber().equalsIgnoreCase(partNumber))
                .findFirst();
    }

    public Cartridge load(DetectedCartridge detected) {
        return loader.load(detected);
    }

    /** Muda toda vez que um cartão entra ou sai. */
    public int revision() {
        return revision;
    }

    /** Um cartão físico continua no leitor onde foi lido? (pastas de desenvolvimento nunca somem) */
    public boolean isStillInserted(Cartridge cartridge) {
        if (!cartridge.removable()) {
            return true;
        }
        return available().stream().anyMatch(c -> c.partNumber().equals(cartridge.manifest().partNumber())
                && c.location().equals(cartridge.location()));
    }

    /** Varre os leitores agora e atualiza a revisão se algo mudou. */
    public synchronized List<DetectedCartridge> rescan() {
        List<DetectedCartridge> found = List.copyOf(reader.scan());
        if (current == null || !sameCards(current, found)) {
            current = found;
            revision++;
        }
        return current;
    }

    public synchronized void startWatching(long periodMillis) {
        if (watcher != null) {
            return;
        }
        rescan();
        watcher = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sd-watcher");
            t.setDaemon(true);
            return t;
        });
        watcher.scheduleWithFixedDelay(() -> {
            try {
                rescan();
            } catch (RuntimeException e) {
                System.err.println("[SD] varredura falhou: " + e.getMessage());
            }
        }, periodMillis, periodMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void close() {
        if (watcher != null) {
            watcher.shutdownNow();
            watcher = null;
        }
    }

    private static boolean sameCards(List<DetectedCartridge> a, List<DetectedCartridge> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!Objects.equals(a.get(i).partNumber(), b.get(i).partNumber())
                    || !a.get(i).root().equals(b.get(i).root())
                    || !Objects.equals(a.get(i).manifest(), b.get(i).manifest())) {
                return false;
            }
        }
        return true;
    }
}
