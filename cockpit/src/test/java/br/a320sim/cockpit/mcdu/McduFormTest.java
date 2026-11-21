package br.a320sim.cockpit.mcdu;

import br.a320sim.cockpit.crud.FormModel;
import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import br.a320sim.dados.servico.ValidacaoException;
import br.a320sim.flightmodel.world.Runway;
import br.a320sim.flightmodel.world.World;
import br.a320sim.host.plan.FlightPlan;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McduFormTest {

    private final DataBus bus = new DataBus();

    /** Avião na cabeceira de Guarulhos, sessão com destino Congonhas 17R. */
    private void atGuarulhosToCongonhas() {
        Runway gru = World.runway("SBGR", "10L").orElseThrow();
        bus.publish(Labels.TRUE_POS_X, gru.xNm());
        bus.publish(Labels.TRUE_POS_Y, gru.yNm());
        bus.publish(Labels.SIM_DEST_RUNWAY, "SBSP 17R");
    }

    private static void type(FormModel form, String field, String value) {
        assertTrue(form.irPara(field), field);
        for (int i = form.valor(field).length(); i > 0; i--) {
            form.digitar('\b');
        }
        value.chars().forEach(c -> form.digitar((char) c));
    }

    private static Map<String, String> validValues() {
        Map<String, String> v = new HashMap<>();
        v.put(FlightPlan.FIELD_ORIGIN, "SBKP");
        v.put(FlightPlan.FIELD_DESTINATION, "SBGR");
        v.put(FlightPlan.FIELD_RUNWAY, "10L");
        v.put(FlightPlan.FIELD_FL, "100");
        v.put(FlightPlan.FIELD_ZFW, "60000");
        v.put(FlightPlan.FIELD_FUEL, "8000");
        v.put(FlightPlan.FIELD_V1, "140");
        v.put(FlightPlan.FIELD_VR, "143");
        v.put(FlightPlan.FIELD_V2, "148");
        return v;
    }

    @Test
    void comecaComOrigemPertoEDestinoDaSessao() {
        atGuarulhosToCongonhas();
        FormModel form = McduForm.form(bus);
        assertEquals("SBGR", form.valor(FlightPlan.FIELD_ORIGIN), "aeroporto mais perto");
        assertEquals("SBSP", form.valor(FlightPlan.FIELD_DESTINATION));
        assertEquals("17R", form.valor(FlightPlan.FIELD_RUNWAY));
        assertEquals("", form.valor(FlightPlan.FIELD_FL), "o resto o aluno preenche");
    }

    @Test
    void reabrirMostraOPlanoJaInserido() {
        FlightPlan plan = McduForm.toPlan(validValues());
        plan.publish(bus);
        FormModel form = McduForm.form(bus);
        assertEquals("10L", form.valor(FlightPlan.FIELD_RUNWAY));
        assertEquals("148", form.valor(FlightPlan.FIELD_V2));
    }

    @Test
    void pesoDeDecolagemESugestaoSaoCalculados() {
        FormModel form = McduForm.form(bus);
        type(form, FlightPlan.FIELD_ZFW, "60000");
        type(form, FlightPlan.FIELD_FUEL, "8000");
        assertEquals("68000", form.valor(McduForm.TOW));
        assertEquals("140/143/148", form.valor(McduForm.SUGGESTION));
        assertFalse(form.irPara(McduForm.TOW), "so leitura");
    }

    @Test
    void numeroInvalidoERegraDoPlanoVaoParaOCampo() {
        Map<String, String> v = validValues();
        v.put(FlightPlan.FIELD_FL, "abc");
        assertTrue(assertThrows(ValidacaoException.class, () -> McduForm.toPlan(v)).erros()
                .containsKey(FlightPlan.FIELD_FL));
        v.put(FlightPlan.FIELD_FL, "100");
        v.put(FlightPlan.FIELD_V1, "150");
        assertEquals("V1 nao pode passar de VR",
                assertThrows(ValidacaoException.class, () -> McduForm.toPlan(v)).erros().get(FlightPlan.FIELD_V1));
    }

    @Test
    void inserirPublicaOPlanoNoBarramento() {
        McduPanel mcdu = new McduPanel();
        mcdu.open(bus);
        FormModel form = mcdu.form();
        assertFalse(mcdu.insert(bus), "vazio nao insere");
        assertFalse(bus.getBoolean(Labels.PANEL_PLAN_OK));

        validValues().forEach((field, value) -> {
            if (form.campos().stream().anyMatch(c -> c.chave().equals(field)
                    && c.tipo() != br.a320sim.cockpit.crud.Campo.Tipo.OPCAO)) {
                type(form, field, value);
            }
        });
        form.irPara(FlightPlan.FIELD_DESTINATION);
        while (!form.valor(FlightPlan.FIELD_DESTINATION).equals("SBGR")) {
            form.opcaoSeguinte();
        }
        assertTrue(mcdu.insert(bus));
        FlightPlan plan = FlightPlan.fromBus(bus).orElseThrow();
        assertEquals("SBGR", plan.destination());
        assertEquals("10L", plan.runway(), "a primeira pista do destino");
        assertEquals(68_000, plan.towKg());
    }
}
