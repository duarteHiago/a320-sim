package br.a320sim.host.command;

import br.a320sim.core.bus.Labels;
import br.a320sim.host.Host;

/** Texto que aparece na tela do aluno. Viaja pelo barramento, como tudo o mais. */
public record SendMessageCommand(String text) implements InstructorCommand {

    @Override
    public String description() {
        return "Mensagem: " + text;
    }

    @Override
    public void execute(Host host) {
        host.bus().publish(Labels.IOS_MESSAGE, text);
        host.bus().publish(Labels.IOS_MESSAGE_ID, host.bus().getDouble(Labels.IOS_MESSAGE_ID, 0) + 1);
    }
}
