package br.a320sim.cockpit.crud;

import br.a320sim.dados.modelo.Posicao;
import br.a320sim.dados.servico.PosicaoServico;

import java.util.List;
import java.util.Map;

/**
 * Cadastro das posições iniciais. Com velocidade 0 e distância 0 a posição é "no chão":
 * a altitude fica bloqueada mostrando a elevação do campo.
 */
public final class PosicoesAdapter implements CrudAdapter<Posicao> {

    private final PosicaoServico posicoes;

    public PosicoesAdapter(PosicaoServico posicoes) {
        this.posicoes = posicoes;
    }

    @Override
    public String titulo() {
        return "POSICOES INICIAIS";
    }

    @Override
    public List<Posicao> listar() {
        return posicoes.listar();
    }

    @Override
    public String linha(Posicao p) {
        String onde = p.noChao() ? "NO CHAO" : p.altitudeFt() + " FT";
        return String.format("%-16s %-8s %s", p.nome(), onde, p.destino().substring(2));
    }

    @Override
    public List<String> detalhes(Posicao p) {
        return List.of(
                "NOME|" + p.nome(),
                "SITUACAO|" + (p.noChao() ? "NO CHAO (CABECEIRA " + p.pista() + ")"
                        : "EM VOO, NO PROLONGAMENTO DA " + p.pista()),
                "ALTITUDE|" + p.altitudeFt() + " ft",
                "VELOCIDADE|" + p.velocidadeKt() + " kt",
                "PROA|" + String.format("%03d", p.proaGraus()),
                "DISTANCIA|" + formatar(p.distanciaNm()) + " NM",
                "DESTINO|" + p.destino() + "  PISTA " + p.pista(),
                "SESSOES|" + posicoes.contarSessoes(p.id()));
    }

    /** No chão quando velocidade e distância são 0 (se der para ler os dois números). */
    static boolean noChao(Map<String, String> v) {
        try {
            int velocidade = Integer.parseInt(v.getOrDefault(PosicaoServico.CAMPO_VELOCIDADE, "").strip());
            double distancia = Double.parseDouble(v.getOrDefault(PosicaoServico.CAMPO_DISTANCIA, "").strip()
                    .replace(',', '.'));
            return Posicao.noChao(velocidade, distancia);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public List<Campo> campos() {
        return List.of(
                Campo.texto(PosicaoServico.CAMPO_NOME, "NOME", PosicaoServico.NOME_MAX),
                Campo.texto(PosicaoServico.CAMPO_VELOCIDADE, "VELOCIDADE KT", 3),
                Campo.texto(PosicaoServico.CAMPO_DISTANCIA, "DISTANCIA NM", 5),
                Campo.texto(PosicaoServico.CAMPO_ALTITUDE, "ALTITUDE FT", 5)
                        .bloqueadoQuando(PosicoesAdapter::noChao,
                                v -> String.valueOf(PosicaoServico.elevacao(v.get(PosicaoServico.CAMPO_DESTINO)))),
                Campo.texto(PosicaoServico.CAMPO_PROA, "PROA", 3),
                Campo.opcao(PosicaoServico.CAMPO_DESTINO, "DESTINO", PosicaoServico.DESTINOS),
                Campo.opcao(PosicaoServico.CAMPO_PISTA, "PISTA",
                        v -> PosicaoServico.pistas(v.get(PosicaoServico.CAMPO_DESTINO))));
    }

    @Override
    public Map<String, String> valoresNovos() {
        return Map.of(PosicaoServico.CAMPO_VELOCIDADE, "250", PosicaoServico.CAMPO_DISTANCIA, "10",
                PosicaoServico.CAMPO_ALTITUDE, "8000", PosicaoServico.CAMPO_PROA, "148",
                PosicaoServico.CAMPO_DESTINO, PosicaoServico.DESTINOS.get(0), PosicaoServico.CAMPO_PISTA, "15");
    }

    @Override
    public Map<String, String> valores(Posicao p) {
        return Map.of(
                PosicaoServico.CAMPO_NOME, p.nome(),
                PosicaoServico.CAMPO_VELOCIDADE, String.valueOf(p.velocidadeKt()),
                PosicaoServico.CAMPO_DISTANCIA, formatar(p.distanciaNm()),
                PosicaoServico.CAMPO_ALTITUDE, String.valueOf(p.altitudeFt()),
                PosicaoServico.CAMPO_PROA, String.valueOf(p.proaGraus()),
                PosicaoServico.CAMPO_DESTINO, p.destino(),
                PosicaoServico.CAMPO_PISTA, p.pista());
    }

    @Override
    public Posicao salvar(Posicao item, Map<String, String> v) {
        Conversao c = new Conversao(v);
        int velocidade = c.inteiro(PosicaoServico.CAMPO_VELOCIDADE);
        double distancia = c.decimal(PosicaoServico.CAMPO_DISTANCIA);
        int altitude = c.inteiro(PosicaoServico.CAMPO_ALTITUDE);
        int proa = c.inteiro(PosicaoServico.CAMPO_PROA);
        c.lancarSeHouver();
        return posicoes.salvar(new Posicao(item == null ? null : item.id(), c.texto(PosicaoServico.CAMPO_NOME), altitude,
                velocidade, proa, distancia, c.texto(PosicaoServico.CAMPO_DESTINO), c.texto(PosicaoServico.CAMPO_PISTA)));
    }

    @Override
    public String perguntaExclusao(Posicao p) {
        return "EXCLUIR A POSICAO " + p.nome() + "?";
    }

    @Override
    public String excluir(Posicao p) {
        posicoes.excluir(p.id());
        return "POSICAO EXCLUIDA";
    }

    /** 10.0 -> "10", 5.5 -> "5.5" */
    public static String formatar(double nm) {
        return nm == Math.rint(nm) ? String.valueOf((long) nm) : String.valueOf(nm);
    }
}
