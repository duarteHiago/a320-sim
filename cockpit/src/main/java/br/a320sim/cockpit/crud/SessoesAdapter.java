package br.a320sim.cockpit.crud;

import br.a320sim.dados.modelo.EventoSessao;
import br.a320sim.dados.modelo.Sessao;
import br.a320sim.dados.servico.SessaoServico;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Histórico de treino. As sessões são gravadas pelo simulador: aqui só se vê (com os eventos),
 * avalia (nota e observação) e exclui.
 */
public final class SessoesAdapter implements CrudAdapter<Sessao> {

    private static final DateTimeFormatter CURTA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final SessaoServico sessoes;

    public SessoesAdapter(SessaoServico sessoes) {
        this.sessoes = sessoes;
    }

    @Override
    public String titulo() {
        return "SESSOES DE TREINO";
    }

    @Override
    public List<Sessao> listar() {
        return sessoes.listar();
    }

    @Override
    public String linha(Sessao s) {
        String nota = s.nota() == null ? "  -" : String.format("%4.1f", s.nota());
        return String.format("%s %-15.15s%s%s", CURTA.format(s.inicio()), s.alunoNome(), nota, s.crash() ? " X" : "");
    }

    @Override
    public List<String> detalhes(Sessao s) {
        List<String> linhas = new ArrayList<>(List.of(
                "ALUNO|" + s.alunoNome(),
                "INSTRUTOR|" + (s.instrutorNome() == null ? "- (SOLO)" : s.instrutorNome()),
                "MODO|" + s.modo() + "   POSICAO: " + (s.posicaoNome() == null ? "-" : s.posicaoNome()),
                "INICIO|" + CartoesAdapter.DATA_HORA.format(s.inicio())
                        + (s.fim() == null ? "   EM ANDAMENTO" : "   FIM " + CartoesAdapter.DATA_HORA.format(s.fim())),
                "DURACAO|" + (s.duracaoS() == null ? "-" : tempo(s.duracaoS()) + " (simulada)")
                        + (s.crash() ? "   CRASH" : ""),
                "CARTUCHOS|" + (s.cartuchos() == null || s.cartuchos().isBlank() ? "-" : s.cartuchos()),
                "PLANO|" + (s.plano() == null ? "(sem plano de voo)" : s.plano().resumo()),
                "V1/VR/V2|" + (s.plano() == null ? "-" : s.plano().velocidades()),
                "NOTA|" + (s.nota() == null ? "(nao avaliada)" : String.format("%.1f", s.nota())),
                "OBS|" + (s.observacao() == null ? "-" : s.observacao()),
                ""));
        List<EventoSessao> eventos = sessoes.eventos(s.id());
        linhas.add("EVENTOS|" + eventos.size());
        for (EventoSessao e : eventos) {
            linhas.add(tempo(e.tempoS()) + "  " + e.descricao());
        }
        return linhas;
    }

    @Override
    public boolean podeCriar() {
        return false;
    }

    @Override
    public List<Campo> campos() {
        return List.of(
                Campo.leitura("aluno", "ALUNO"),
                Campo.leitura("inicio", "INICIO"),
                Campo.texto(SessaoServico.CAMPO_NOTA, "NOTA (0 A 10)", 4),
                Campo.texto(SessaoServico.CAMPO_OBSERVACAO, "OBS", SessaoServico.OBSERVACAO_MAX));
    }

    @Override
    public Map<String, String> valoresNovos() {
        return Map.of();
    }

    @Override
    public Map<String, String> valores(Sessao s) {
        Map<String, String> v = new HashMap<>();
        v.put("aluno", s.alunoNome());
        v.put("inicio", CartoesAdapter.DATA_HORA.format(s.inicio()));
        v.put(SessaoServico.CAMPO_NOTA, s.nota() == null ? "" : String.format(java.util.Locale.ROOT, "%.1f", s.nota()));
        v.put(SessaoServico.CAMPO_OBSERVACAO, s.observacao() == null ? "" : s.observacao());
        return v;
    }

    @Override
    public Sessao salvar(Sessao item, Map<String, String> v) {
        Conversao c = new Conversao(v);
        Double nota = c.decimalOpcional(SessaoServico.CAMPO_NOTA);
        c.lancarSeHouver();
        sessoes.avaliar(item.id(), nota, c.texto(SessaoServico.CAMPO_OBSERVACAO));
        return sessoes.buscar(item.id()).orElse(item);
    }

    @Override
    public String perguntaExclusao(Sessao s) {
        return "EXCLUIR A SESSAO DE " + s.alunoNome().toUpperCase() + " (" + CURTA.format(s.inicio()) + ")?";
    }

    @Override
    public String excluir(Sessao s) {
        sessoes.excluir(s.id());
        return "SESSAO EXCLUIDA";
    }

    /** Segundos de simulação em mm:ss (ou h:mm:ss). */
    static String tempo(double segundos) {
        int total = (int) segundos;
        int h = total / 3600;
        return h > 0 ? String.format("%d:%02d:%02d", h, total / 60 % 60, total % 60)
                : String.format("%02d:%02d", total / 60, total % 60);
    }
}
