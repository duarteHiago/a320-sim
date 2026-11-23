package br.a320sim.cockpit.mcdu;

import br.a320sim.host.plan.FlightPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * O "bloco de notas" ao lado do MCDU: exemplos de planos de voo válidos para o aluno copiar
 * e dicas de como preencher. As velocidades vêm da mesma sugestão que o MCDU mostra.
 */
public final class McduNotes {

    /** Largura do bloco em caracteres da fonte pixel. */
    public static final int WIDTH_CHARS = 44;

    /** Um exemplo com nome e o plano (sempre válido: o teste confere). */
    public record Example(String name, FlightPlan plan) {
    }

    private McduNotes() {
    }

    public static List<Example> examples() {
        return List.of(
                example("VOO CURTO", "SBKP", "SBGR", "10L", 100, 60_000, 6_000),
                example("PONTE AEREA", "SBKP", "SBSP", "17R", 120, 58_000, 5_000),
                example("CIRCUITO (TREINO)", "SBKP", "SBKP", "15", 50, 55_000, 3_000));
    }

    private static Example example(String name, String from, String to, String runway, int fl, int zfw, int fuel) {
        int tow = zfw + fuel;
        return new Example(name, new FlightPlan(from, to, runway, fl, zfw, fuel, FlightPlan.suggestedV1(tow),
                FlightPlan.suggestedVr(tow), FlightPlan.suggestedV2(tow)));
    }

    /** As linhas do bloco, já no formato da tela (ASCII, sem acento). */
    public static List<String> lines() {
        List<String> lines = new ArrayList<>();
        int n = 1;
        for (Example e : examples()) {
            FlightPlan p = e.plan();
            lines.add(n++ + " " + e.name());
            lines.add("  " + p.origin() + " > " + p.destination() + "  PISTA " + p.runway() + "  FL"
                    + String.format("%03d", p.cruiseFl()));
            lines.add("  PESO " + p.zfwKg() + "  COMB " + p.fuelKg());
            lines.add("  V1/VR/V2 " + p.v1() + "/" + p.vr() + "/" + p.v2());
            lines.add("");
        }
        lines.add("DICAS");
        lines.add("- FL = altitude / 100 (FL100 = 10000 ft)");
        lines.add("- PESO DECOLAGEM = PESO + COMB (max 78000)");
        lines.add("- V1 <= VR <= V2; use a SUGESTAO do MCDU");
        lines.add("- ESQ/DIR trocam aeroporto e pista");
        return lines;
    }
}
