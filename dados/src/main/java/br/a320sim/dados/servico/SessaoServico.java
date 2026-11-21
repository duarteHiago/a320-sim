package br.a320sim.dados.servico;

import br.a320sim.dados.modelo.EventoSessao;
import br.a320sim.dados.modelo.PlanoVoo;
import br.a320sim.dados.modelo.Sessao;
import br.a320sim.dados.repositorio.SessaoRepositorio;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Sessões de treino. Quem abre, grava os eventos e fecha é o simulador; no cadastro só dá para ver,
 * avaliar (nota e observação) e excluir.
 */
public class SessaoServico {

    public static final String CAMPO_NOTA = "nota";
    public static final String CAMPO_OBSERVACAO = "observacao";
    public static final int OBSERVACAO_MAX = 60;
    public static final int DESCRICAO_MAX = 120;

    private final SessaoRepositorio repositorio;
    private final Clock relogio;

    public SessaoServico(SessaoRepositorio repositorio, Clock relogio) {
        this.repositorio = repositorio;
        this.relogio = relogio;
    }

    public List<Sessao> listar() {
        return repositorio.listar();
    }

    public Optional<Sessao> buscar(long id) {
        return repositorio.buscar(id);
    }

    public List<EventoSessao> eventos(long id) {
        return repositorio.eventos(id);
    }

    /** @return o id da sessão aberta agora */
    public long abrir(long alunoId, Long instrutorId, String modo, Long posicaoId, String cartuchos) {
        return repositorio.inserir(Sessao.nova(alunoId, instrutorId, modo, posicaoId, cartuchos, agora()));
    }

    /** Descrições longas demais são cortadas, para um evento nunca derrubar a gravação. */
    public void registrarEventos(long id, List<EventoSessao> eventos) {
        repositorio.inserirEventos(id, eventos.stream()
                .map(e -> e.descricao().length() <= DESCRICAO_MAX ? e
                        : new EventoSessao(e.tempoS(), e.descricao().substring(0, DESCRICAO_MAX)))
                .toList());
    }

    /** O plano de voo que o aluno inseriu (o último vale). */
    public void registrarPlano(long id, PlanoVoo plano) {
        repositorio.registrarPlano(id, plano);
    }

    public void fechar(long id, double duracaoS, boolean crash, String cartuchos) {
        repositorio.fechar(id, agora(), duracaoS, crash, cartuchos);
    }

    /**
     * @param nota de 0 a 10 com no máximo uma casa decimal; nula = ainda não avaliada
     * @throws ValidacaoException nota fora da faixa ou observação longa demais
     */
    public void avaliar(long id, Double nota, String observacao) {
        String obs = Erros.limpo(observacao);
        Erros erros = new Erros();
        erros.se(nota != null && (nota < 0 || nota > 10), CAMPO_NOTA, "de 0 a 10");
        erros.se(nota != null && Math.abs(nota * 10 - Math.round(nota * 10)) > 1e-9, CAMPO_NOTA,
                "no maximo uma casa decimal (ex.: 7.5)");
        erros.se(obs != null && obs.length() > OBSERVACAO_MAX, CAMPO_OBSERVACAO,
                "no maximo " + OBSERVACAO_MAX + " caracteres");
        erros.lancarSeHouver();
        repositorio.avaliar(id, nota, obs);
    }

    public void excluir(long id) {
        repositorio.excluir(id);
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(relogio).withNano(0);
    }
}
