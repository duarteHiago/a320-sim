package br.a320sim.cockpit.session;

import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import br.a320sim.host.command.InstructorCommand;
import br.a320sim.net.SimClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Modo ALUNO: o barramento local é um espelho do barramento do instrutor.
 * A cada quadro mandamos os comandos do piloto e aplicamos a última cópia recebida.
 */
public final class RemoteSession implements SimSession {

    private static final long STALE_MS = 1500;

    private final SimClient client;
    private final DataBus mirror = new DataBus();

    public RemoteSession(SimClient client) {
        this.client = client;
    }

    @Override
    public DataBus bus() {
        return mirror;
    }

    @Override
    public void update(float delta) {
        Map<String, Object> controls = new HashMap<>();
        mirror.snapshot().forEach((label, value) -> {
            if (Labels.isPilotInput(label)) {
                controls.put(label, value);
            }
        });
        client.sendControls(controls);
        client.pollSnapshot().ifPresent(snapshot -> {
            snapshot.putAll(controls);
            mirror.replaceAll(snapshot);
        });
    }

    @Override
    public boolean acceptsCommands() {
        return false;
    }

    @Override
    public void execute(InstructorCommand command) {
        // quem comanda a sessão é o instrutor
    }

    @Override
    public String rackText() {
        return mirror.getString(Labels.IOS_RACK, "");
    }

    @Override
    public String status() {
        if (!client.isOpen()) {
            return "SEM CONEXAO COM O INSTRUTOR  -  ESC para sair";
        }
        if (client.millisSinceLastSnapshot() > STALE_MS) {
            return "AGUARDANDO O INSTRUTOR LIGAR O AVIAO...";
        }
        return null;
    }

    @Override
    public void close() {
        client.close();
    }
}
