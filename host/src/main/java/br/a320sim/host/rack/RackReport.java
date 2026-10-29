package br.a320sim.host.rack;

import java.util.List;

public record RackReport(List<String> problems) {

    public RackReport {
        problems = List.copyOf(problems);
    }

    public boolean ok() {
        return problems.isEmpty();
    }
}
