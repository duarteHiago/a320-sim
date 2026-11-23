package br.a320sim.dados.servico;

import br.a320sim.dados.BancoDeTeste;
import br.a320sim.dados.Dados;
import br.a320sim.dados.modelo.Posicao;
import br.a320sim.flightmodel.FlightModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PosicaoServicoTest {

    private final Dados dados = BancoDeTeste.dados();
    private final PosicaoServico servico = dados.posicoes();

    @AfterEach
    void fechar() {
        dados.close();
    }

    private static Posicao posicao(String nome, int alt, int vel, int proa, double dist) {
        return new Posicao(null, nome, alt, vel, proa, dist, "SBKP");
    }

    private Map<String, String> erros(Posicao p) {
        return assertThrows(ValidacaoException.class, () -> servico.salvar(p)).erros();
    }

    @Test
    void seedRespeitaAsRegras() {
        for (Posicao p : servico.listar()) {
            servico.salvar(p);
        }
    }

    @Test
    void noChaoAAltitudeViraAElevacaoDoCampo() {
        Posicao salva = servico.salvar(posicao("CABECEIRA", 0, 0, 148, 0));
        assertEquals(Math.round(FlightModel.FIELD_ELEVATION_FT), salva.altitudeFt());
        assertEquals(salva, servico.buscar(salva.id()).orElseThrow(), "gravada ja corrigida");
        assertTrue(salva.noChao());
    }

    @Test
    void emVooVelocidadeDe120a350() {
        assertTrue(erros(posicao("LENTO", 8000, 119, 148, 10)).containsKey(PosicaoServico.CAMPO_VELOCIDADE));
        assertTrue(erros(posicao("RAPIDO", 8000, 351, 148, 10)).containsKey(PosicaoServico.CAMPO_VELOCIDADE));
        // Parado mas longe da pista não é "no chão": precisa de velocidade de voo
        assertTrue(erros(posicao("PARADO LONGE", 8000, 0, 148, 10)).containsKey(PosicaoServico.CAMPO_VELOCIDADE));
        servico.salvar(posicao("LIMITE BAIXO", 8000, 120, 148, 10));
        servico.salvar(posicao("LIMITE ALTO", 8000, 350, 148, 10));
    }

    @Test
    void emVooPeloMenos500PesAcimaDoCampo() {
        int minima = (int) Math.round(FlightModel.FIELD_ELEVATION_FT) + 500;
        assertEquals("em voo no minimo " + minima + " ft (500 acima do campo)",
                erros(posicao("BAIXO", minima - 1, 200, 148, 3)).get(PosicaoServico.CAMPO_ALTITUDE));
        servico.salvar(posicao("BAIXO", minima, 200, 148, 3));
    }

    @Test
    void faixasDeAltitudeProaEDistancia() {
        assertTrue(erros(posicao("ALTO", 41_001, 250, 148, 10)).containsKey(PosicaoServico.CAMPO_ALTITUDE));
        assertTrue(erros(posicao("PROA", 8000, 250, 360, 10)).containsKey(PosicaoServico.CAMPO_PROA));
        assertTrue(erros(posicao("PROA", 8000, 250, -1, 10)).containsKey(PosicaoServico.CAMPO_PROA));
        assertTrue(erros(posicao("LONGE", 8000, 250, 148, 100.1)).containsKey(PosicaoServico.CAMPO_DISTANCIA));
        assertTrue(erros(posicao("PERTO", 8000, 250, 148, -1)).containsKey(PosicaoServico.CAMPO_DISTANCIA));
        servico.salvar(posicao("TETO", 41_000, 250, 0, 100));
        servico.salvar(posicao("NORTE", 8000, 250, 359, 0.5));
    }

    @Test
    void nomeUnicoDe3a16Caracteres() {
        assertTrue(erros(posicao("AB", 8000, 250, 148, 10)).containsKey(PosicaoServico.CAMPO_NOME));
        assertTrue(erros(posicao("X".repeat(17), 8000, 250, 148, 10)).containsKey(PosicaoServico.CAMPO_NOME));
        assertEquals("ja existe uma posicao com esse nome",
                erros(posicao("pista 15", 8000, 250, 148, 10)).get(PosicaoServico.CAMPO_NOME));
        assertEquals("X".repeat(16), servico.salvar(posicao("x".repeat(16), 8000, 250, 148, 10)).nome());
    }

    @Test
    void destinoPrecisaSerConhecido() {
        // SBGR passou a existir na Fase 3; o Santos Dumont continua fora do simulador
        Posicao sdu = new Posicao(null, "SDU", 8000, 250, 148, 10, "SBRJ");
        assertTrue(erros(sdu).containsKey(PosicaoServico.CAMPO_DESTINO));
    }

    @Test
    void posicaoUsadaEmSessaoNaoPodeSerExcluida() {
        Posicao livre = servico.salvar(posicao("LIVRE", 8000, 250, 148, 10));
        servico.excluir(livre.id());
        assertTrue(servico.buscar(livre.id()).isEmpty());

        Posicao pista = servico.listar().get(0);
        long ana = dados.pessoas().listar().get(0).id();
        for (int i = 0; i < 3; i++) {
            dados.sessoes().abrir(ana, null, "SOLO", pista.id(), "");
        }
        RegraException e = assertThrows(RegraException.class, () -> servico.excluir(pista.id()));
        assertEquals("nao pode excluir: usada em 3 sessoes", e.getMessage());
        assertFalse(servico.buscar(pista.id()).isEmpty());
    }

    // --- Fase 3: Guarulhos e Congonhas ---

    private static Posicao em(String nome, int alt, int vel, double dist, String destino, String pista) {
        return new Posicao(null, nome, alt, vel, 94, dist, destino, pista);
    }

    @Test
    void seedTemAsPosicoesDeGuarulhosECongonhas() {
        Posicao final10L = servico.listar().stream().filter(p -> p.nome().equals("FINAL GRU 10L")).findFirst().orElseThrow();
        assertEquals("SBGR", final10L.destino());
        assertEquals("10L", final10L.pista());
        assertEquals(4059, final10L.altitudeFt(), "1600 ft acima de GRU");
        Posicao pista15 = servico.listar().get(0);
        assertEquals("15", pista15.pista(), "posicoes antigas ficaram na pista 15");
        assertEquals(List.of("FINAL GRU 10L", "FINAL CGH 17R", "PISTA GRU 10L", "PISTA CGH 17R"),
                servico.listar().stream().map(Posicao::nome).toList().subList(3, 7));
    }

    @Test
    void pistaPrecisaExistirNoDestino() {
        assertEquals("SBGR tem as pistas 10L 28R 10R 28L",
                erros(em("GRU 15", 8000, 250, 10, "SBGR", "15")).get(PosicaoServico.CAMPO_PISTA));
        assertTrue(erros(em("GRU 09L", 8000, 250, 10, "SBGR", "09L")).containsKey(PosicaoServico.CAMPO_PISTA),
                "numeracao antiga");
        assertEquals("28L", servico.salvar(em("GRU 28L", 8000, 250, 10, "sbgr", "28l")).pista());
        assertEquals("35R", servico.salvar(em("CGH 35R", 8000, 250, 10, "SBSP", "35R")).pista());
    }

    @Test
    void noChaoAAltitudeEADoAeroportoDeDestino() {
        assertEquals(2459, servico.salvar(em("CHAO GRU", 0, 0, 0, "SBGR", "28R")).altitudeFt());
        assertEquals(2631, servico.salvar(em("CHAO CGH", 0, 0, 0, "SBSP", "17L")).altitudeFt());
    }

    @Test
    void emVoo500PesAcimaDoAeroportoDeDestino() {
        assertEquals("em voo no minimo 3131 ft (500 acima do campo)",
                erros(em("BAIXO CGH", 3000, 150, 5, "SBSP", "17R")).get(PosicaoServico.CAMPO_ALTITUDE));
        servico.salvar(em("BAIXO CGH", 3131, 150, 5, "SBSP", "17R"));
        servico.salvar(em("BAIXO KP", 2670, 150, 5, "SBKP", "33"));
    }
}
