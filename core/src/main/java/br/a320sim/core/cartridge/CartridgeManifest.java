package br.a320sim.core.cartridge;

import java.util.List;

/**
 * Conteúdo do cartridge.json que vai em cada cartão SD.
 * {@code jar} é o arquivo, ao lado do manifesto, com o software das caixas; sem ele as
 * classes vêm do próprio programa (modo de desenvolvimento, pastas sd-dev/).
 */
public record CartridgeManifest(
        String partNumber,
        String nome,
        CartridgeGroup grupo,
        Generation versao,
        List<String> modulos,
        List<Requirement> requer,
        String jar) {

    public CartridgeManifest {
        modulos = modulos == null ? List.of() : List.copyOf(modulos);
        requer = requer == null ? List.of() : List.copyOf(requer);
    }
}
