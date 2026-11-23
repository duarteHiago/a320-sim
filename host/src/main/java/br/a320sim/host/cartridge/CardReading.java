package br.a320sim.host.cartridge;

import java.time.LocalDate;

/**
 * O que o data loader sabe de um cartão físico depois de conferir a assinatura.
 *
 * @param volumeId  número de série do volume (FAT32) ou, sem ele, o part number ({@link VolumeId})
 * @param jarSha256 hash do caixas.jar que a assinatura garantiu
 * @param gravadoEm data do caixas.jar no cartão (quando ele foi gravado)
 */
public record CardReading(String partNumber, String volumeId, String jarSha256, LocalDate gravadoEm) {
}
