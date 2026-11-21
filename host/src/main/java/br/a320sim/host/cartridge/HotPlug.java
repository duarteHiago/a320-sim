package br.a320sim.host.cartridge;

import br.a320sim.core.cartridge.Cartridge;
import br.a320sim.core.cartridge.CartridgeGroup;
import br.a320sim.core.cartridge.CartridgeRejectedException;
import br.a320sim.host.Host;

import java.util.ArrayList;
import java.util.List;

/**
 * Liga os leitores SD ao rack com o avião funcionando:
 * <ul>
 *   <li>cartão arrancado do leitor → o slot esvazia e as caixas dele perdem o software;</li>
 *   <li>cartão encaixado num slot vazio → DATA LOADING, as caixas ligam e fazem o self-test de novo.</li>
 * </ul>
 * Chame {@link #update()} a cada quadro; ele só trabalha quando a gaveta muda.
 */
public class HotPlug {

    private final Host host;
    private final CartridgeLibrary library;
    private int seenRevision = -1;

    public HotPlug(Host host, CartridgeLibrary library) {
        this.host = host;
        this.library = library;
    }

    /** @return o que aconteceu nesta chamada (para log/tela); vazio se nada mudou */
    public List<String> update() {
        int revision = library.revision();
        if (revision == seenRevision) {
            return List.of();
        }
        seenRevision = revision;
        List<String> events = new ArrayList<>();

        for (Cartridge cartridge : List.copyOf(host.rack().slots().values())) {
            if (!library.isStillInserted(cartridge)) {
                host.ejectCartridge(cartridge.group());
                events.add("CARTUCHO " + cartridge.manifest().partNumber() + " REMOVIDO");
            }
        }

        if (host.isPowered()) {
            for (CartridgeGroup group : CartridgeGroup.values()) {
                if (host.rack().slot(group).isPresent()) {
                    continue;
                }
                library.available().stream()
                        .filter(c -> c.group() == group)
                        .findFirst()
                        .ifPresent(detected -> tryInsert(detected, events));
            }
        }
        return events;
    }

    private void tryInsert(DetectedCartridge detected, List<String> events) {
        Cartridge cartridge;
        try {
            cartridge = library.load(detected);
        } catch (CartridgeRejectedException e) {
            events.add("CARTUCHO " + detected.partNumber() + " RECUSADO: " + e.reason().label());
            return;
        } catch (RuntimeException e) {
            events.add("CARTUCHO " + detected.partNumber() + " ILEGIVEL");
            return;
        }
        Host.HotInsertResult result = host.hotInsert(cartridge);
        events.add(switch (result) {
            case LOADED -> "CARTUCHO " + detected.partNumber() + " CARREGADO";
            case INCOMPATIBLE -> "CARTUCHO " + detected.partNumber() + " INCOMPATIVEL";
            case SLOT_OCCUPIED -> "SLOT " + detected.group() + " OCUPADO";
        });
    }
}
