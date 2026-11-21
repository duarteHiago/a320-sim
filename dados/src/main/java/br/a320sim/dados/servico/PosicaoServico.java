package br.a320sim.dados.servico;

import br.a320sim.dados.modelo.Posicao;
import br.a320sim.dados.repositorio.PosicaoRepositorio;
import br.a320sim.flightmodel.FlightModel;
import br.a320sim.flightmodel.world.Airport;
import br.a320sim.flightmodel.world.World;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Regras das posições iniciais. Duas situações válidas:
 * <ul>
 *   <li>no chão: velocidade 0 e distância 0 (a altitude é sempre a do aeroporto de destino);</li>
 *   <li>em voo: velocidade de 120 a 350 kt e pelo menos 500 ft acima do aeroporto de destino.</li>
 * </ul>
 * Sempre: altitude 0 a 41000 ft, proa 0 a 359, distância 0 a 100 NM, nome único de 3 a 16 caracteres,
 * destino conhecido e uma pista que existe nele.
 */
public class PosicaoServico {

    public static final String CAMPO_NOME = "nome";
    public static final String CAMPO_ALTITUDE = "altitude";
    public static final String CAMPO_VELOCIDADE = "velocidade";
    public static final String CAMPO_PROA = "proa";
    public static final String CAMPO_DISTANCIA = "distancia";
    public static final String CAMPO_DESTINO = "destino";
    public static final String CAMPO_PISTA = "pista";

    public static final int NOME_MIN = 3;
    public static final int NOME_MAX = 16;
    public static final int ALTITUDE_MAX = 41_000;
    public static final int VELOCIDADE_MIN = 120;
    public static final int VELOCIDADE_MAX = 350;
    public static final int ALTURA_MIN_EM_VOO = 500;
    public static final int DISTANCIA_MAX = 100;
    /** Altitude de Viracopos (o aeroporto de sempre). */
    public static final int ELEVACAO_CAMPO = (int) Math.round(FlightModel.FIELD_ELEVATION_FT);
    /** Aeroportos de destino que o simulador conhece. */
    public static final List<String> DESTINOS = World.AIRPORTS.stream().map(Airport::icao).toList();

    private final PosicaoRepositorio repositorio;

    public PosicaoServico(PosicaoRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    public List<Posicao> listar() {
        return repositorio.listar();
    }

    public Optional<Posicao> buscar(long id) {
        return repositorio.buscar(id);
    }

    /** Pistas (os dois sentidos) de um destino; vazio se o destino não existe. */
    public static List<String> pistas(String destino) {
        return destino == null ? List.of() : World.airport(destino).map(Airport::designators).orElse(List.of());
    }

    /** Elevação do aeroporto (ft), ou a de Viracopos se o destino não existe. */
    public static int elevacao(String destino) {
        return (int) Math.round(World.airport(destino == null ? "" : destino)
                .map(Airport::elevationFt).orElse(FlightModel.FIELD_ELEVATION_FT));
    }

    public int contarSessoes(long id) {
        return repositorio.contarSessoes(id);
    }

    /**
     * Insere ou atualiza. No chão, a altitude é corrigida para a elevação do campo antes de gravar.
     *
     * @return a posição como ficou gravada
     * @throws ValidacaoException com um erro por campo inválido
     */
    public Posicao salvar(Posicao posicao) {
        String nome = Erros.limpo(posicao.nome());
        String destino = Erros.limpo(posicao.destino());
        String pista = Erros.limpo(posicao.pista());
        Posicao p = new Posicao(posicao.id(), nome == null ? null : nome.toUpperCase(Locale.ROOT),
                posicao.altitudeFt(), posicao.velocidadeKt(), posicao.proaGraus(), posicao.distanciaNm(),
                destino == null ? null : destino.toUpperCase(Locale.ROOT),
                pista == null ? null : pista.toUpperCase(Locale.ROOT));
        if (p.noChao()) {
            p = p.comAltitude(elevacao(p.destino()));
        }
        validar(p);
        if (p.id() == null) {
            return repositorio.inserir(p);
        }
        repositorio.atualizar(p);
        return p;
    }

    private void validar(Posicao p) {
        Erros erros = new Erros();
        int nome = p.nome() == null ? 0 : p.nome().length();
        erros.se(nome < NOME_MIN || nome > NOME_MAX, CAMPO_NOME, "de " + NOME_MIN + " a " + NOME_MAX + " caracteres");
        if (!erros.tem(CAMPO_NOME)) {
            Optional<Posicao> dono = repositorio.buscarPorNome(p.nome());
            erros.se(dono.isPresent() && !dono.get().id().equals(p.id()), CAMPO_NOME, "ja existe uma posicao com esse nome");
        }

        erros.se(p.altitudeFt() < 0 || p.altitudeFt() > ALTITUDE_MAX, CAMPO_ALTITUDE, "de 0 a " + ALTITUDE_MAX + " ft");
        erros.se(p.proaGraus() < 0 || p.proaGraus() > 359, CAMPO_PROA, "de 0 a 359 graus");
        erros.se(p.distanciaNm() < 0 || p.distanciaNm() > DISTANCIA_MAX, CAMPO_DISTANCIA,
                "de 0 a " + DISTANCIA_MAX + " NM");
        if (!p.noChao()) {
            erros.se(p.velocidadeKt() < VELOCIDADE_MIN || p.velocidadeKt() > VELOCIDADE_MAX, CAMPO_VELOCIDADE,
                    "em voo de " + VELOCIDADE_MIN + " a " + VELOCIDADE_MAX + " kt (no chao: 0 kt e 0 NM)");
            int minima = elevacao(p.destino()) + ALTURA_MIN_EM_VOO;
            erros.se(p.altitudeFt() < minima, CAMPO_ALTITUDE,
                    "em voo no minimo " + minima + " ft (" + ALTURA_MIN_EM_VOO + " acima do campo)");
        }
        erros.se(p.destino() == null || !DESTINOS.contains(p.destino()), CAMPO_DESTINO,
                "aeroporto desconhecido (use " + String.join(", ", DESTINOS) + ")");
        if (!erros.tem(CAMPO_DESTINO)) {
            erros.se(p.pista() == null || !pistas(p.destino()).contains(p.pista()), CAMPO_PISTA,
                    p.destino() + " tem as pistas " + String.join(" ", pistas(p.destino())));
        }
        erros.lancarSeHouver();
    }

    /** @throws RegraException se alguma sessão começou nesta posição */
    public void excluir(long id) {
        int sessoes = repositorio.contarSessoes(id);
        if (sessoes > 0) {
            throw new RegraException("nao pode excluir: usada em " + sessoes + (sessoes == 1 ? " sessao" : " sessoes"));
        }
        repositorio.excluir(id);
    }
}
