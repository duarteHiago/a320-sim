package br.a320sim.core.cartridge;

/** Os 3 slots do rack. Cada cartucho ocupa exatamente um. */
public enum CartridgeGroup {
    PLATAFORMA("Sensores + Fly-by-wire"),
    PROPULSAO("Motores + Piloto automático"),
    COCKPIT("Telas + Alarmes");

    private final String description;

    CartridgeGroup(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
