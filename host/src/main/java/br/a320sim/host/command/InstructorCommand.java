package br.a320sim.host.command;

import br.a320sim.host.Host;

/**
 * Uma ação do instrutor sobre a sessão (padrão Command).
 * Por ser um objeto, cada comando pode ser registrado, repetido ou agendado num cenário.
 */
public interface InstructorCommand {

    String description();

    void execute(Host host);
}
