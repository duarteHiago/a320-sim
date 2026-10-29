package br.a320sim.core.cartridge;

import java.util.List;

/** Conteúdo do cartridge.json que vai em cada cartão SD. */
public record CartridgeManifest(
        String partNumber,
        String nome,
        CartridgeGroup grupo,
        Generation versao,
        List<String> modulos,
        List<Requirement> requer) {

    public CartridgeManifest {
        modulos = modulos == null ? List.of() : List.copyOf(modulos);
        requer = requer == null ? List.of() : List.copyOf(requer);
    }
}
