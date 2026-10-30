package br.a320sim.cockpit.session;

import br.a320sim.core.bus.DataBus;
import br.a320sim.host.Host;
import br.a320sim.host.command.InstructorCommand;

import java.util.stream.Collectors;

/** Modo SOLO: o host roda no mesmo processo do cockpit. */
public final class LocalSession implements SimSession {

    private final Host host;

    public LocalSession(Host host) {
        this.host = host;
    }

    @Override
    public DataBus bus() {
        return host.bus();
    }

    @Override
    public void update(float delta) {
        host.advance(delta);
    }

    @Override
    public boolean acceptsCommands() {
        return true;
    }

    @Override
    public void execute(InstructorCommand command) {
        host.execute(command);
    }

    @Override
    public String rackText() {
        return host.rack().slots().values().stream()
                .map(c -> c.manifest().partNumber())
                .collect(Collectors.joining("  "));
    }

    @Override
    public String status() {
        return null;
    }

    @Override
    public void close() {
        host.powerOff();
    }
}
