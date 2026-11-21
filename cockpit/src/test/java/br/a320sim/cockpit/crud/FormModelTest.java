package br.a320sim.cockpit.crud;

import br.a320sim.dados.servico.RegraException;
import br.a320sim.dados.servico.ValidacaoException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormModelTest {

    private static final List<Campo> CAMPOS = List.of(
            Campo.texto("nome", "NOME", 5),
            Campo.leitura("leituras", "LEITURAS"),
            Campo.opcao("perfil", "PERFIL", List.of("ALUNO", "INSTRUTOR")),
            Campo.texto("email", "E-MAIL", 20));

    private FormModel form() {
        return new FormModel("NOVO", CAMPOS, Map.of("leituras", "3"));
    }

    @Test
    void comecaNoPrimeiroCampoEditavelEPulaOsDeLeitura() {
        FormModel f = form();
        assertEquals("nome", f.atual().chave());
        f.proximo();
        assertEquals("perfil", f.atual().chave(), "LEITURAS nao recebe o cursor");
        f.proximo();
        assertEquals("email", f.atual().chave());
        f.proximo();
        assertEquals("email", f.atual().chave(), "para no ultimo");
        f.anterior();
        f.anterior();
        f.anterior();
        assertEquals("nome", f.atual().chave(), "para no primeiro");
    }

    @Test
    void opcaoComecaNaPrimeiraEGiraComEsquerdaEDireita() {
        FormModel f = form();
        assertEquals("ALUNO", f.valor("perfil"));
        f.irPara("perfil");
        f.opcaoSeguinte();
        assertEquals("INSTRUTOR", f.valor("perfil"));
        f.opcaoSeguinte();
        assertEquals("ALUNO", f.valor("perfil"), "da a volta");
        f.opcaoAnterior();
        assertEquals("INSTRUTOR", f.valor("perfil"));
    }

    @Test
    void digitarSoNoCampoDeTextoComLimiteEApagar() {
        FormModel f = form();
        "Anabela".chars().forEach(c -> f.digitar((char) c));
        assertEquals("Anabe", f.valor("nome"), "maximo de 5");
        f.digitar('\b');
        assertEquals("Anab", f.valor("nome"));
        f.digitar('é'); // acento: a fonte pixel nao tem
        f.digitar('\n');
        assertEquals("Anab", f.valor("nome"));

        f.irPara("perfil");
        f.digitar('x');
        assertEquals("ALUNO", f.valor("perfil"), "opcao nao recebe letras");
    }

    @Test
    void errosDeValidacaoVaoParaOCampoEOCursorParaOPrimeiro() {
        FormModel f = form();
        f.irPara("email");
        boolean salvou = f.salvar(v -> {
            throw new ValidacaoException(Map.of("nome", "de 3 a 40 caracteres"));
        });
        assertFalse(salvou);
        assertEquals("de 3 a 40 caracteres", f.erro("nome"));
        assertNull(f.erro("email"));
        assertEquals("nome", f.atual().chave());

        f.digitar('A');
        assertNull(f.erro("nome"), "mexeu no campo, o erro some");
    }

    @Test
    void erroDeCampoQueNaoEstaNoFormularioViraErroGeral() {
        FormModel f = form();
        f.salvar(v -> {
            throw new ValidacaoException(Map.of("outro", "ja existe"));
        });
        assertEquals("ja existe", f.erroGeral());
        f.salvar(v -> {
            throw new RegraException("nao pode excluir: usada em 3 sessoes");
        });
        assertEquals("nao pode excluir: usada em 3 sessoes", f.erroGeral());
    }

    @Test
    void salvarPassaOsValoresDosCampos() {
        FormModel f = form();
        "Eva".chars().forEach(c -> f.digitar((char) c));
        AtomicReference<Map<String, String>> recebido = new AtomicReference<>();
        assertTrue(f.salvar(recebido::set));
        assertEquals(Map.of("nome", "Eva", "leituras", "3", "perfil", "ALUNO", "email", ""), recebido.get());
        assertNull(f.erroGeral());
    }

    // --- campo bloqueado: altitude de posição no chão ---

    private FormModel posicao(String velocidade, String distancia) {
        return new FormModel("EDITAR", new PosicoesAdapter(null).campos(),
                Map.of("nome", "TESTE", "velocidade", velocidade, "distancia", distancia, "altitude", "8000",
                        "proa", "148", "destino", "SBKP"));
    }

    @Test
    void noChaoAAltitudeFicaBloqueadaComAElevacaoDoCampo() {
        FormModel f = posicao("0", "0");
        assertEquals("2170", f.valor("altitude"));
        assertEquals("2170", f.valores().get("altitude"), "salva o valor do campo");
        assertFalse(f.irPara("altitude"));
        f.irPara("distancia");
        f.proximo();
        assertEquals("proa", f.atual().chave(), "pula a altitude bloqueada");
    }

    @Test
    void sairDoChaoDesbloqueiaAAltitude() {
        FormModel f = posicao("0", "0");
        f.irPara("velocidade");
        f.digitar('\b');
        "250".chars().forEach(c -> f.digitar((char) c));
        assertEquals("8000", f.valor("altitude"));
        assertTrue(f.irPara("altitude"));
    }

    @Test
    void parouLongeDaPistaNaoEChao() {
        assertTrue(PosicoesAdapter.noChao(Map.of("velocidade", "0", "distancia", "0,0")));
        assertFalse(PosicoesAdapter.noChao(Map.of("velocidade", "0", "distancia", "5")));
        assertFalse(PosicoesAdapter.noChao(Map.of("velocidade", "", "distancia", "0")));
    }

    // --- conversão de números ---

    @Test
    void conversaoJuntaOsErrosDeNumero() {
        Conversao c = new Conversao(Map.of("a", "12", "b", "doze", "c", "", "d", "7,5", "e", ""));
        assertEquals(12, c.inteiro("a"));
        c.inteiro("b");
        c.inteiro("c");
        assertEquals(7.5, c.decimalOpcional("d"));
        assertNull(c.decimalOpcional("e"), "vazio = sem nota");
        Map<String, String> erros = assertThrows(ValidacaoException.class, c::lancarSeHouver).erros();
        assertEquals(Map.of("b", "numero inteiro (ex.: 250)", "c", "obrigatorio"), erros);
    }
}
