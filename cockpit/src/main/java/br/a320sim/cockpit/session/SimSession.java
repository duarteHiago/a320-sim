package br.a320sim.cockpit.session;

import br.a320sim.core.bus.DataBus;
import br.a320sim.host.command.InstructorCommand;

/**
 * De onde o cockpit tira o avião. No modo SOLO o host roda aqui mesmo;
 * no modo ALUNO ele está no PC do instrutor e o barramento chega pela rede.
 * O cockpit não sabe a diferença: só lê e escreve no barramento.
 */
public interface SimSession {

    DataBus bus();

    /** Avança um quadro: roda o host local, ou troca mensagens com o remoto. */
    void update(float delta);

    /** No modo SOLO o próprio aluno pode pausar, reposicionar etc. No modo ALUNO, só o instrutor. */
    boolean acceptsCommands();

    void execute(InstructorCommand command);

    String rackText();

    /** Aviso para mostrar na tela (ex.: conexão perdida), ou null se está tudo bem. */
    String status();

    void close();
}
