package br.a320sim.dados.servico;

import br.a320sim.dados.Banco;
import br.a320sim.dados.BancoDeTeste;
import br.a320sim.dados.modelo.Cartao;
import br.a320sim.dados.modelo.StatusCartao;
import br.a320sim.dados.repositorio.CartaoRepositorio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CartaoServicoTest {

    private static final String HASH_1 = "11".repeat(32);
    private static final String HASH_2 = "22".repeat(32);
    private static final LocalDate GRAVADO = LocalDate.of(2026, 11, 2);

    private final Banco banco = BancoDeTeste.banco();
    private final CartaoRepositorio repositorio = new CartaoRepositorio(banco.conexao());
    private final CartaoServico servico = new CartaoServico(repositorio, BancoDeTeste.RELOGIO);

    @AfterEach
    void fechar() {
        banco.close();
    }

    private Map<String, String> erros(Cartao c) {
        return assertThrows(ValidacaoException.class, () -> servico.salvar(c)).erros();
    }

    @Test
    void partNumberObrigatorioEtiquetaOpcionalAte10() {
        assertEquals("obrigatorio", erros(Cartao.manual("SD-07", " ", StatusCartao.ATIVO, null))
                .get(CartaoServico.CAMPO_PART_NUMBER));
        assertTrue(erros(Cartao.manual("SD-0000007X", "PLT-A320-CEO", StatusCartao.ATIVO, null))
                .containsKey(CartaoServico.CAMPO_ETIQUETA));

        Cartao semEtiqueta = servico.salvar(Cartao.manual("  ", "plt-a320-ceo", StatusCartao.ATIVO, null));
        assertNull(semEtiqueta.etiqueta(), "etiqueta vazia vira nula (nao conta para o UNIQUE)");
        assertEquals("PLT-A320-CEO", semEtiqueta.partNumber());
        servico.salvar(Cartao.manual("", "PLT-A320-CEO", StatusCartao.ATIVO, null));
    }

    @Test
    void etiquetaUnicaQuandoPreenchida() {
        assertEquals("ja usada por outro cartao",
                erros(Cartao.manual("sd-01", "PLT-A320-NEO", StatusCartao.ATIVO, null)).get(CartaoServico.CAMPO_ETIQUETA));
        Cartao sd01 = repositorio.buscarPorEtiqueta("SD-01").orElseThrow();
        servico.salvar(new Cartao(sd01.id(), "SD-01", sd01.partNumber(), null, null, null, null, 0,
                StatusCartao.BLOQUEADO, "trincado"));
        assertEquals(StatusCartao.BLOQUEADO, servico.buscar(sd01.id()).orElseThrow().status());
    }

    @Test
    void leituraDeCartaoDesconhecidoCadastraSozinho() {
        Cartao novo = servico.registrarLeitura("9999-0001", "XYZ-TESTE", HASH_1, GRAVADO);
        assertNull(novo.etiqueta(), "etiqueta vazia para o instrutor preencher");
        assertEquals(StatusCartao.ATIVO, novo.status());
        assertEquals(1, novo.leituras());
        assertEquals(BancoDeTeste.AGORA, novo.ultimaLeitura());
        assertEquals(HASH_1, novo.sha256());
        assertEquals(novo, servico.buscar(novo.id()).orElseThrow());
    }

    @Test
    void cadastroManualComMesmoPartNumberEAdotadoEmVezDeDuplicar() {
        int antes = servico.listar().size();
        Cartao lido = servico.registrarLeitura("ABCD-0001", "PRP-A320-NEO", HASH_1, GRAVADO);
        assertEquals("SD-04", lido.etiqueta());
        assertEquals("ABCD-0001", lido.volumeId());
        assertEquals(antes, servico.listar().size());

        // Um segundo cartão com o mesmo part number não acha mais o manual: vira outro cadastro
        Cartao outro = servico.registrarLeitura("ABCD-0002", "PRP-A320-NEO", HASH_1, GRAVADO);
        assertNull(outro.etiqueta());
        assertEquals(antes + 1, servico.listar().size());
    }

    @Test
    void cadaLeituraAtualizaDataContadorEHash() {
        servico.registrarLeitura("ABCD-0003", "PLT-A320-CEO", HASH_1, GRAVADO);
        CartaoServico depois = new CartaoServico(repositorio, BancoDeTeste.relogio(BancoDeTeste.AGORA.plusDays(1)));
        Cartao relido = depois.registrarLeitura("ABCD-0003", "PLT-A320-CEO", HASH_2, GRAVADO.plusDays(1));
        assertEquals(2, relido.leituras());
        assertEquals(HASH_2, relido.sha256(), "cartao regravado com outro software");
        assertEquals(BancoDeTeste.AGORA.plusDays(1), relido.ultimaLeitura());
        assertEquals("SD-01", relido.etiqueta());
    }
}
