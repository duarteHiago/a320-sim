package br.a320sim.cockpit.session;

import br.a320sim.dados.BancoLocal;
import br.a320sim.dados.DadosException;
import br.a320sim.dados.SessaoStoreBanco;
import br.a320sim.dados.modelo.Posicao;
import br.a320sim.dados.modelo.Sessao;
import br.a320sim.host.Host;
import br.a320sim.host.command.ApplyPositionCommand;
import br.a320sim.host.session.SessionRecorder;

import java.util.List;

/**
 * Cuida da sessão de treino onde o host roda (SOLO ou INSTRUTOR): depois do boot aplica a posição
 * escolhida e abre a gravação; ao voltar para o rack, para o título ou ao fechar o programa, fecha.
 * Sem banco (ou sem aluno), a posição é aplicada do mesmo jeito, só não grava nada.
 */
public final class SessionKeeper {

    private final Host host;
    private final BancoLocal banco;
    private SessionRecorder recorder;
    private long sessionId = -1;

    public SessionKeeper(Host host, BancoLocal banco) {
        this.host = host;
        this.banco = banco;
    }

    /** Chamado logo depois do boot (o avião acabou de ligar). */
    public void start(SessionPlan plan, String mode) {
        finish();
        Posicao posicao = plan.posicao();
        if (posicao != null) {
            host.execute(new ApplyPositionCommand(posicao.nome(), posicao.altitudeFt(), posicao.velocidadeKt(),
                    posicao.proaGraus(), posicao.distanciaNm()));
        }
        if (plan.aluno() == null) {
            return;
        }
        banco.dados().ifPresent(dados -> {
            try {
                Long instructor = Sessao.INSTRUTOR.equals(mode) && plan.instrutor() != null ? plan.instrutor().id() : null;
                sessionId = dados.sessoes().abrir(plan.aluno().id(), instructor, mode,
                        posicao == null ? null : posicao.id(), cartridges());
                recorder = new SessionRecorder(host, new SessaoStoreBanco(dados.sessoes(), sessionId));
            } catch (DadosException e) {
                System.err.println("[SESSAO] nao abriu, o voo segue sem gravar: " + e.getMessage());
            }
        });
    }

    /** A cada quadro. */
    public void update(double delta) {
        if (recorder != null) {
            recorder.update(delta);
        }
    }

    /** Fecha a sessão aberta (se houver); pode ser chamado à vontade. */
    public void finish() {
        if (recorder != null) {
            recorder.close();
            recorder = null;
            sessionId = -1;
        }
    }

    public boolean recording() {
        return recorder != null;
    }

    /** Id da sessão gravando agora, ou -1. */
    public long sessionId() {
        return sessionId;
    }

    private String cartridges() {
        List<String> pns = host.rack().slots().values().stream().map(c -> c.manifest().partNumber()).toList();
        return String.join("  ", pns);
    }
}
