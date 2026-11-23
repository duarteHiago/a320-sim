package br.a320sim.host.cartridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Identifica o cartão físico: no Windows, o número de série do volume FAT32 (o mesmo que o
 * {@code vol G:} mostra, ex.: "A1B2-C3D4"). Muda se o cartão for formatado, mas não se for regravado.
 */
public final class VolumeId {

    private VolumeId() {
    }

    /**
     * @return o número de série; o part number se o sistema não informar, ou se {@code root} for uma
     * pasta fingindo ser leitor ({@code -Dsim.sd.roots}), já que pastas do mesmo disco têm o mesmo número
     */
    public static String of(Path root, String partNumber) {
        if (root.getParent() != null) {
            return partNumber;
        }
        try {
            Object vsn = Files.getFileStore(root).getAttribute("volume:vsn");
            if (vsn instanceof Integer serial) {
                return String.format("%04X-%04X", serial >>> 16, serial & 0xFFFF);
            }
        } catch (IOException | UnsupportedOperationException | IllegalArgumentException e) {
            // sistema sem esse atributo (Linux, macOS): fica o part number
        }
        return partNumber;
    }
}
