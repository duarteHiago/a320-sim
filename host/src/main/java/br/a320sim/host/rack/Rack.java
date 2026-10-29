package br.a320sim.host.rack;

import br.a320sim.core.cartridge.Cartridge;
import br.a320sim.core.cartridge.CartridgeGroup;
import br.a320sim.core.cartridge.Requirement;
import br.a320sim.core.module.AvionicsModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** O rack de slots: um cartucho por grupo, com validação de compatibilidade. */
public class Rack {

    private final Map<CartridgeGroup, Cartridge> slots = new EnumMap<>(CartridgeGroup.class);

    /** @return o cartucho que já ocupava o slot, se houver (conflito) */
    public Optional<Cartridge> insert(Cartridge cartridge) {
        Cartridge current = slots.get(cartridge.group());
        if (current != null) {
            return Optional.of(current);
        }
        slots.put(cartridge.group(), cartridge);
        return Optional.empty();
    }

    public Optional<Cartridge> remove(CartridgeGroup group) {
        return Optional.ofNullable(slots.remove(group));
    }

    public Optional<Cartridge> slot(CartridgeGroup group) {
        return Optional.ofNullable(slots.get(group));
    }

    public Map<CartridgeGroup, Cartridge> slots() {
        return Collections.unmodifiableMap(slots);
    }

    public List<AvionicsModule> modules() {
        return slots.values().stream()
                .flatMap(cartridge -> cartridge.modules().stream())
                .toList();
    }

    public RackReport validate() {
        List<String> problems = new ArrayList<>();
        for (CartridgeGroup group : CartridgeGroup.values()) {
            if (!slots.containsKey(group)) {
                problems.add("Slot " + group + " vazio");
            }
        }
        for (Cartridge cartridge : slots.values()) {
            for (Requirement req : cartridge.manifest().requer()) {
                Cartridge other = slots.get(req.grupo());
                if (other != null && !other.generation().isAtLeast(req.versaoMin())) {
                    problems.add(cartridge.manifest().partNumber() + " exige " + req.grupo()
                            + " " + req.versaoMin() + " ou mais novo (inserido: " + other.generation() + ")");
                }
            }
        }
        return new RackReport(problems);
    }
}
