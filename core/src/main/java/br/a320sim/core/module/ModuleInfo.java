package br.a320sim.core.module;

/**
 * @param code         nome técnico da caixa (ADIRU, FADEC...), também prefixo dos seus labels
 * @param friendlyName nome que aparece na UI (Sensores, Motores...)
 * @param variant      variante desta implementação (ex.: "IAE V2500")
 */
public record ModuleInfo(String code, String friendlyName, String variant) {
}
