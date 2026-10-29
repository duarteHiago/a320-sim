package br.a320sim.core.cartridge;

/** Geração do A320. A ordem importa: NEO é mais nova que CEO. */
public enum Generation {
    CEO,
    NEO;

    public boolean isAtLeast(Generation other) {
        return compareTo(other) >= 0;
    }
}
