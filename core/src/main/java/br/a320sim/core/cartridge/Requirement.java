package br.a320sim.core.cartridge;

/** "Este cartucho exige que o slot {@code grupo} tenha geração ≥ {@code versaoMin}". */
public record Requirement(CartridgeGroup grupo, Generation versaoMin) {
}
