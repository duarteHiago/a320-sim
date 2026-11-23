package br.a320sim.host.cartridge;

import br.a320sim.core.cartridge.CartridgeRejectedException;

/**
 * Quem decide se um cartão físico pode rodar, além da assinatura (ex.: o cadastro de cartões).
 * O {@link CartridgeLoader} chama a política depois de conferir a assinatura e o checksum,
 * e só para cartões com .jar (as pastas sd-dev/ não passam por aqui).
 * <p>
 * O host não sabe de onde vem a decisão: a implementação de verdade fica no módulo {@code dados}.
 */
public interface CardPolicy {

    /** Sem cadastro: todo cartão bem assinado roda. */
    CardPolicy ACCEPT_ALL = (reading, newInsertion) -> {
    };

    /**
     * Chamada em toda carga do cartão, para o bloqueio valer mesmo com o cartão já no leitor.
     *
     * @param newInsertion true só na primeira carga depois que o cartão entrou no leitor (ou na primeira
     *                     depois que o programa abriu): é quando a leitura deve ser registrada
     * @throws CartridgeRejectedException com {@code CARTAO_BLOQUEADO} se o cartão não pode rodar
     */
    void check(CardReading reading, boolean newInsertion);
}
