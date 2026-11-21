package br.a320sim.host.plan;

import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import br.a320sim.flightmodel.world.World;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * O plano de voo que o aluno preenche no MCDU antes de decolar.
 * <p>
 * Viaja no barramento como {@code PANEL.PLAN_*} (comando do piloto: no modo ALUNO vai sozinho para o PC
 * do instrutor). O combustível é só um número do plano, não é consumido; o peso total (sem combustível +
 * combustível) é o peso do avião no modelo de voo.
 *
 * @param cruiseFl nível de voo de cruzeiro (100 = FL100 = 10.000 ft)
 */
public record FlightPlan(String origin, String destination, String runway, int cruiseFl, int zfwKg, int fuelKg,
                         int v1, int vr, int v2) {

    public static final String FIELD_ORIGIN = "origem";
    public static final String FIELD_DESTINATION = "destino";
    public static final String FIELD_RUNWAY = "pista";
    public static final String FIELD_FL = "fl";
    public static final String FIELD_ZFW = "zfw";
    public static final String FIELD_FUEL = "combustivel";
    public static final String FIELD_V1 = "v1";
    public static final String FIELD_VR = "vr";
    public static final String FIELD_V2 = "v2";

    public static final int FL_MIN = 50;
    public static final int FL_MAX = 410;
    /** Peso sem combustível: avião vazio com tripulação (~42 t) até o máximo estrutural (62,5 t). */
    public static final int ZFW_MIN = 42_000;
    public static final int ZFW_MAX = 62_500;
    public static final int FUEL_MIN = 1_000;
    public static final int FUEL_MAX = 18_700;
    /** Peso máximo de decolagem. */
    public static final int TOW_MAX = 78_000;
    public static final int SPEED_MIN = 100;
    public static final int SPEED_MAX = 180;

    /** Peso de decolagem (kg). */
    public int towKg() {
        return zfwKg + fuelKg;
    }

    /** Velocidade de rotação sugerida para um peso de decolagem: ~125 kt com 50 t, ~1 kt a mais por tonelada. */
    public static int suggestedVr(int towKg) {
        return (int) Math.round(125 + (towKg - 50_000) / 1000.0);
    }

    public static int suggestedV1(int towKg) {
        return suggestedVr(towKg) - 3;
    }

    public static int suggestedV2(int towKg) {
        return suggestedVr(towKg) + 5;
    }

    /** Erros por campo (vazio = plano válido). Mensagens sem acento: aparecem na fonte pixel. */
    public Map<String, String> validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        if (World.airport(origin == null ? "" : origin).isEmpty()) {
            errors.put(FIELD_ORIGIN, "aeroporto desconhecido");
        }
        if (World.airport(destination == null ? "" : destination).isEmpty()) {
            errors.put(FIELD_DESTINATION, "aeroporto desconhecido");
        } else if (World.runway(destination, runway == null ? "" : runway).isEmpty()) {
            errors.put(FIELD_RUNWAY, "pista nao existe em " + destination);
        }
        if (cruiseFl < FL_MIN || cruiseFl > FL_MAX || cruiseFl % 10 != 0) {
            errors.put(FIELD_FL, "FL de 050 a 410, de 10 em 10");
        }
        if (zfwKg < ZFW_MIN || zfwKg > ZFW_MAX) {
            errors.put(FIELD_ZFW, "de " + ZFW_MIN + " a " + ZFW_MAX + " kg");
        }
        if (fuelKg < FUEL_MIN || fuelKg > FUEL_MAX) {
            errors.put(FIELD_FUEL, "de " + FUEL_MIN + " a " + FUEL_MAX + " kg");
        } else if (!errors.containsKey(FIELD_ZFW) && towKg() > TOW_MAX) {
            errors.put(FIELD_FUEL, "peso de decolagem " + towKg() + " kg passa de " + TOW_MAX);
        }
        checkSpeed(errors, FIELD_V1, v1);
        checkSpeed(errors, FIELD_VR, vr);
        checkSpeed(errors, FIELD_V2, v2);
        if (!errors.containsKey(FIELD_V1) && !errors.containsKey(FIELD_VR) && v1 > vr) {
            errors.put(FIELD_V1, "V1 nao pode passar de VR");
        }
        if (!errors.containsKey(FIELD_VR) && !errors.containsKey(FIELD_V2) && vr > v2) {
            errors.put(FIELD_V2, "V2 nao pode ser menor que VR");
        }
        return errors;
    }

    private static void checkSpeed(Map<String, String> errors, String field, int kt) {
        if (kt < SPEED_MIN || kt > SPEED_MAX) {
            errors.put(field, "de " + SPEED_MIN + " a " + SPEED_MAX + " kt");
        }
    }

    /** Uma linha para o log e o histórico. */
    public String description() {
        return String.format("PLANO %s > %s %s FL%03d TOW %d KG V %d/%d/%d", origin, destination, runway, cruiseFl,
                towKg(), v1, vr, v2);
    }

    /** "SBGR 10L" */
    public String destinationRunway() {
        return destination + " " + runway;
    }

    /** Publica o plano (inserido) no barramento. */
    public void publish(DataBus bus) {
        bus.publish(Labels.PANEL_PLAN_ORIGIN, origin);
        bus.publish(Labels.PANEL_PLAN_DEST, destination);
        bus.publish(Labels.PANEL_PLAN_RWY, runway);
        bus.publish(Labels.PANEL_PLAN_FL, (double) cruiseFl);
        bus.publish(Labels.PANEL_PLAN_ZFW, (double) zfwKg);
        bus.publish(Labels.PANEL_PLAN_FUEL, (double) fuelKg);
        bus.publish(Labels.PANEL_PLAN_V1, (double) v1);
        bus.publish(Labels.PANEL_PLAN_VR, (double) vr);
        bus.publish(Labels.PANEL_PLAN_V2, (double) v2);
        bus.publish(Labels.PANEL_PLAN_OK, true);
    }

    /** O plano inserido que está no barramento, se houver. */
    public static Optional<FlightPlan> fromBus(DataBus bus) {
        if (!bus.getBoolean(Labels.PANEL_PLAN_OK)) {
            return Optional.empty();
        }
        return Optional.of(new FlightPlan(bus.getString(Labels.PANEL_PLAN_ORIGIN, ""),
                bus.getString(Labels.PANEL_PLAN_DEST, ""), bus.getString(Labels.PANEL_PLAN_RWY, ""),
                (int) bus.getDouble(Labels.PANEL_PLAN_FL, 0), (int) bus.getDouble(Labels.PANEL_PLAN_ZFW, 0),
                (int) bus.getDouble(Labels.PANEL_PLAN_FUEL, 0), (int) bus.getDouble(Labels.PANEL_PLAN_V1, 0),
                (int) bus.getDouble(Labels.PANEL_PLAN_VR, 0), (int) bus.getDouble(Labels.PANEL_PLAN_V2, 0)));
    }
}
