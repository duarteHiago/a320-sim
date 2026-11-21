package br.a320sim.dados;

import br.a320sim.core.cartridge.CartridgeRejectedException;
import br.a320sim.dados.modelo.Cartao;
import br.a320sim.dados.modelo.StatusCartao;
import br.a320sim.dados.servico.CartaoServico;
import br.a320sim.host.cartridge.CardPolicy;
import br.a320sim.host.cartridge.CardReading;

import java.util.Optional;

/**
 * A {@link CardPolicy} de verdade: cartão BLOQUEADO ou PERDIDO no cadastro não roda;
 * os outros rodam e, na primeira carga depois da inserção, a leitura é registrada.
 * <p>
 * Se o banco falhar no meio do voo, o cartão roda assim mesmo: um problema no cadastro
 * não pode derrubar o avião (o erro vai para o console).
 */
public class CartaoPolicyBanco implements CardPolicy {

    private final CartaoServico cartoes;

    public CartaoPolicyBanco(CartaoServico cartoes) {
        this.cartoes = cartoes;
    }

    @Override
    public void check(CardReading leitura, boolean novaInsercao) {
        Optional<Cartao> cadastro;
        try {
            cadastro = cartoes.identificar(leitura.volumeId(), leitura.partNumber());
        } catch (DadosException e) {
            System.err.println("[BANCO] cartao " + leitura.partNumber() + " nao conferido: " + e.getMessage());
            return;
        }
        if (cadastro.isPresent() && cadastro.get().status() != StatusCartao.ATIVO) {
            Cartao c = cadastro.get();
            throw new CartridgeRejectedException(CartridgeRejectedException.Reason.CARTAO_BLOQUEADO,
                    "cartao " + c.nomeCurto() + " esta " + c.status() + " no cadastro");
        }
        if (novaInsercao) {
            try {
                cartoes.registrarLeitura(leitura.volumeId(), leitura.partNumber(), leitura.jarSha256(),
                        leitura.gravadoEm());
            } catch (DadosException e) {
                System.err.println("[BANCO] leitura de " + leitura.partNumber() + " nao registrada: " + e.getMessage());
            }
        }
    }
}
