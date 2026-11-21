package br.a320sim.cockpit.mcdu;

import br.a320sim.cockpit.crud.Campo;
import br.a320sim.cockpit.crud.Conversao;
import br.a320sim.cockpit.crud.FormModel;
import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import br.a320sim.dados.servico.ValidacaoException;
import br.a320sim.flightmodel.world.Airport;
import br.a320sim.flightmodel.world.Runway;
import br.a320sim.flightmodel.world.World;
import br.a320sim.host.plan.FlightPlan;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A página INIT/PERF do MCDU, sem nada de LibGDX: os campos do plano de voo, os valores iniciais
 * (origem = aeroporto mais perto, destino = o da sessão, ou o plano já inserido) e a conversão para
 * um {@link FlightPlan} validado. Reaproveita o {@link FormModel} dos cadastros.
 */
public final class McduForm {

    /** Campos só de leitura, calculados dos outros. */
    static final String TOW = "tow";
    static final String SUGGESTION = "sugestao";

    private static final List<String> AIRPORTS = World.AIRPORTS.stream().map(Airport::icao).toList();

    private McduForm() {
    }

    public static List<Campo> campos() {
        return List.of(
                Campo.opcao(FlightPlan.FIELD_ORIGIN, "ORIGEM", AIRPORTS),
                Campo.opcao(FlightPlan.FIELD_DESTINATION, "DESTINO", AIRPORTS),
                Campo.opcao(FlightPlan.FIELD_RUNWAY, "PISTA DESTINO", v -> World.airport(
                        v.getOrDefault(FlightPlan.FIELD_DESTINATION, "")).map(Airport::designators).orElse(List.of())),
                Campo.texto(FlightPlan.FIELD_FL, "FL CRUZEIRO", 3),
                Campo.texto(FlightPlan.FIELD_ZFW, "PESO S/ COMB (KG)", 5),
                Campo.texto(FlightPlan.FIELD_FUEL, "COMBUSTIVEL (KG)", 5),
                Campo.texto(TOW, "PESO DECOLAGEM", 6).bloqueadoQuando(v -> true, McduForm::tow),
                Campo.texto(FlightPlan.FIELD_V1, "V1 (KT)", 3),
                Campo.texto(FlightPlan.FIELD_VR, "VR (KT)", 3),
                Campo.texto(FlightPlan.FIELD_V2, "V2 (KT)", 3),
                Campo.texto(SUGGESTION, "SUGESTAO V1/VR/V2", 11).bloqueadoQuando(v -> true, McduForm::suggestion));
    }

    /** O formulário pronto: com o plano já inserido, ou com valores iniciais a partir do barramento. */
    public static FormModel form(DataBus bus) {
        Map<String, String> values = new HashMap<>();
        Optional<FlightPlan> inserted = FlightPlan.fromBus(bus);
        if (inserted.isPresent()) {
            FlightPlan p = inserted.get();
            values.put(FlightPlan.FIELD_ORIGIN, p.origin());
            values.put(FlightPlan.FIELD_DESTINATION, p.destination());
            values.put(FlightPlan.FIELD_RUNWAY, p.runway());
            values.put(FlightPlan.FIELD_FL, String.valueOf(p.cruiseFl()));
            values.put(FlightPlan.FIELD_ZFW, String.valueOf(p.zfwKg()));
            values.put(FlightPlan.FIELD_FUEL, String.valueOf(p.fuelKg()));
            values.put(FlightPlan.FIELD_V1, String.valueOf(p.v1()));
            values.put(FlightPlan.FIELD_VR, String.valueOf(p.vr()));
            values.put(FlightPlan.FIELD_V2, String.valueOf(p.v2()));
        } else {
            Airport here = World.nearest(bus.getDouble(Labels.TRUE_POS_X, 0), bus.getDouble(Labels.TRUE_POS_Y, 0));
            values.put(FlightPlan.FIELD_ORIGIN, here.icao());
            String[] dest = bus.getString(Labels.SIM_DEST_RUNWAY, "").split(" ");
            Optional<Runway> destination = dest.length == 2 ? World.runway(dest[0], dest[1]) : Optional.empty();
            values.put(FlightPlan.FIELD_DESTINATION, destination.map(Runway::airport).orElse(here.icao()));
            values.put(FlightPlan.FIELD_RUNWAY, destination.map(Runway::designator).orElse(""));
        }
        return new FormModel("INIT / PERF", campos(), values);
    }

    /**
     * Converte e valida.
     *
     * @throws ValidacaoException erros por campo (número inválido ou regra do plano)
     */
    public static FlightPlan toPlan(Map<String, String> values) {
        Conversao c = new Conversao(values);
        FlightPlan plan = new FlightPlan(c.texto(FlightPlan.FIELD_ORIGIN), c.texto(FlightPlan.FIELD_DESTINATION),
                c.texto(FlightPlan.FIELD_RUNWAY), c.inteiro(FlightPlan.FIELD_FL), c.inteiro(FlightPlan.FIELD_ZFW),
                c.inteiro(FlightPlan.FIELD_FUEL), c.inteiro(FlightPlan.FIELD_V1), c.inteiro(FlightPlan.FIELD_VR),
                c.inteiro(FlightPlan.FIELD_V2));
        c.lancarSeHouver();
        Map<String, String> errors = plan.validate();
        if (!errors.isEmpty()) {
            throw new ValidacaoException(errors);
        }
        return plan;
    }

    /** Peso de decolagem, se os dois pesos já forem números. */
    static String tow(Map<String, String> v) {
        return weight(v).map(String::valueOf).orElse("-");
    }

    /** As velocidades sugeridas para o peso de decolagem. */
    static String suggestion(Map<String, String> v) {
        return weight(v).map(tow -> FlightPlan.suggestedV1(tow) + "/" + FlightPlan.suggestedVr(tow) + "/"
                + FlightPlan.suggestedV2(tow)).orElse("-");
    }

    private static Optional<Integer> weight(Map<String, String> v) {
        try {
            return Optional.of(Integer.parseInt(v.getOrDefault(FlightPlan.FIELD_ZFW, "").strip())
                    + Integer.parseInt(v.getOrDefault(FlightPlan.FIELD_FUEL, "").strip()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
