package br.a320sim.dados.servico;

import br.a320sim.dados.BancoDeTeste;
import br.a320sim.dados.Dados;
import br.a320sim.dados.modelo.EventoSessao;
import br.a320sim.dados.modelo.Sessao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessaoServicoTest {

    private final Dados dados = BancoDeTeste.dados();
    private final SessaoServico servico = dados.sessoes();
    private long id;

    @BeforeEach
    void abrir() {
        id = servico.abrir(dados.pessoas().listar().get(0).id(), null, Sessao.SOLO, null, "PLT-A320-CEO");
    }

    @AfterEach
    void fechar() {
        dados.close();
    }

    @Test
    void abreComOInicioEFechaComOFim() {
        Sessao aberta = servico.buscar(id).orElseThrow();
        assertEquals(BancoDeTeste.AGORA, aberta.inicio());
        assertTrue(aberta.aberta());
        servico.fechar(id, 61.0, false, "PLT-A320-CEO");
        assertEquals(BancoDeTeste.AGORA, servico.buscar(id).orElseThrow().fim());
    }

    @Test
    void notaDe0a10ComUmaCasaDecimal() {
        assertTrue(assertThrows(ValidacaoException.class, () -> servico.avaliar(id, 10.5, null)).erros()
                .containsKey(SessaoServico.CAMPO_NOTA));
        assertTrue(assertThrows(ValidacaoException.class, () -> servico.avaliar(id, -0.1, null)).erros()
                .containsKey(SessaoServico.CAMPO_NOTA));
        assertEquals("no maximo uma casa decimal (ex.: 7.5)",
                assertThrows(ValidacaoException.class, () -> servico.avaliar(id, 7.25, null)).erros()
                        .get(SessaoServico.CAMPO_NOTA));
        servico.avaliar(id, 0.0, null);
        servico.avaliar(id, 10.0, null);
        servico.avaliar(id, 8.7, "  boa aproximacao  ");
        Sessao avaliada = servico.buscar(id).orElseThrow();
        assertEquals(8.7, avaliada.nota());
        assertEquals("boa aproximacao", avaliada.observacao());
    }

    @Test
    void notaPodeFicarVaziaEnquantoNaoAvaliada() {
        servico.avaliar(id, 9.0, "x");
        servico.avaliar(id, null, "");
        Sessao s = servico.buscar(id).orElseThrow();
        assertNull(s.nota());
        assertNull(s.observacao());
    }

    @Test
    void observacaoAte60Caracteres() {
        assertTrue(assertThrows(ValidacaoException.class, () -> servico.avaliar(id, null, "x".repeat(61))).erros()
                .containsKey(SessaoServico.CAMPO_OBSERVACAO));
    }

    @Test
    void descricaoLongaDeEventoECortadaEmVezDeFalhar() {
        servico.registrarEventos(id, List.of(new EventoSessao(1, "M".repeat(200))));
        assertEquals(SessaoServico.DESCRICAO_MAX, servico.eventos(id).get(0).descricao().length());
    }
}
