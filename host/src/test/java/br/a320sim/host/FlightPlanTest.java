package br.a320sim.host;

import br.a320sim.core.bus.DataBus;
import br.a320sim.core.bus.Labels;
import br.a320sim.host.cartridge.CartridgeLibrary;
import br.a320sim.host.cartridge.CartridgeLoader;
import br.a320sim.host.cartridge.ClasspathModuleFactory;
import br.a320sim.host.cartridge.DevFolderReader;
import br.a320sim.host.plan.FlightPlan;
import br.a320sim.host.plan.FlightPlanMonitor;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightPlanTest {

    /** SBKP > SBGR 10L, FL100, 60 t + 8 t = 68 t, velocidades da sugestão. */
    private static FlightPlan valid() {
        return new FlightPlan("SBKP", "SBGR", "10L", 100, 60_000, 8_000, 140, 143, 148);
    }

    private static FlightPlan with(String field, Object value) {
        FlightPlan p = valid();
        return new FlightPlan(
                field.equals("origem") ? (String) value : p.origin(),
                field.equals("destino") ? (String) value : p.destination(),
                field.equals("pista") ? (String) value : p.runway(),
                field.equals("fl") ? (int) value : p.cruiseFl(),
                field.equals("zfw") ? (int) value : p.zfwKg(),
                field.equals("combustivel") ? (int) value : p.fuelKg(),
                field.equals("v1") ? (int) value : p.v1(),
                field.equals("vr") ? (int) value : p.vr(),
                field.equals("v2") ? (int) value : p.v2());
    }

    @Test
    void planoCompletoEValido() {
        assertEquals(Map.of(), valid().validate());
        assertEquals(68_000, valid().towKg());
        assertEquals("PLANO SBKP > SBGR 10L FL100 TOW 68000 KG V 140/143/148", valid().description());
    }

    @Test
    void destinoEPistaPrecisamExistir() {
        assertTrue(with("origem", "SBRJ").validate().containsKey(FlightPlan.FIELD_ORIGIN));
        assertTrue(with("destino", "SBRJ").validate().containsKey(FlightPlan.FIELD_DESTINATION));
        assertEquals("pista nao existe em SBGR", with("pista", "15").validate().get(FlightPlan.FIELD_RUNWAY));
    }

    @Test
    void flDe050a410DeDezEmDez() {
        assertTrue(with("fl", 40).validate().containsKey(FlightPlan.FIELD_FL));
        assertTrue(with("fl", 420).validate().containsKey(FlightPlan.FIELD_FL));
        assertTrue(with("fl", 105).validate().containsKey(FlightPlan.FIELD_FL));
        assertTrue(with("fl", 50).validate().isEmpty());
        assertTrue(with("fl", 410).validate().isEmpty());
    }

    @Test
    void pesoDeDecolagemDentroDosLimites() {
        assertTrue(with("zfw", 41_999).validate().containsKey(FlightPlan.FIELD_ZFW));
        assertTrue(with("zfw", 62_501).validate().containsKey(FlightPlan.FIELD_ZFW));
        assertTrue(with("combustivel", 500).validate().containsKey(FlightPlan.FIELD_FUEL));
        // 62,5 t + 18 t = 80,5 t: cada um no limite, mas juntos passam do peso máximo de decolagem
        FlightPlan heavy = new FlightPlan("SBKP", "SBGR", "10L", 100, 62_500, 18_000, 150, 155, 160);
        assertEquals("peso de decolagem 80500 kg passa de 78000", heavy.validate().get(FlightPlan.FIELD_FUEL));
    }

    @Test
    void v1AteVrAteV2() {
        assertEquals("V1 nao pode passar de VR", with("v1", 150).validate().get(FlightPlan.FIELD_V1));
        assertEquals("V2 nao pode ser menor que VR", with("v2", 142).validate().get(FlightPlan.FIELD_V2));
        assertTrue(with("vr", 99).validate().containsKey(FlightPlan.FIELD_VR));
        assertTrue(new FlightPlan("SBKP", "SBKP", "33", 50, 50_000, 5_000, 140, 140, 140).validate().isEmpty(),
                "iguais pode; voltar para a origem tambem (circuito)");
    }

    @Test
    void velocidadesSugeridasCrescemComOPeso() {
        assertEquals(125, FlightPlan.suggestedVr(50_000));
        assertEquals(143, FlightPlan.suggestedVr(68_000));
        assertEquals(140, FlightPlan.suggestedV1(68_000));
        assertEquals(148, FlightPlan.suggestedV2(68_000));
    }

    @Test
    void vaiEVoltaPeloBarramento() {
        DataBus empty = new DataBus();
        assertTrue(FlightPlan.fromBus(empty).isEmpty());
        valid().publish(empty);
        assertEquals(valid(), FlightPlan.fromBus(empty).orElseThrow());
        assertTrue(Labels.isPilotInput(Labels.PANEL_PLAN_OK), "vai do PC do aluno para o instrutor");
    }

    // --- no host ---

    private final Host host = new Host();
    private final DataBus bus = host.bus();

    private List<String> log() {
        return host.log().stream().map(Host.LoggedCommand::description).toList();
    }

    @Test
    void planoInseridoVaiParaOLogMudaOPesoEODestino() {
        valid().publish(bus);
        host.advance(1);
        host.advance(1);
        assertEquals(List.of(valid().description()), log(), "uma vez so");
        assertEquals(68_000, bus.getDouble(Labels.SIM_GROSS_WEIGHT, 0));
        assertEquals("SBGR 10L", bus.getString(Labels.SIM_DEST_RUNWAY, ""));
        assertTrue(host.plan().plan().isPresent());
    }

    @Test
    void planoInvalidoNoBarramentoEIgnorado() {
        with("fl", 999).publish(bus);
        host.advance(1);
        assertTrue(log().isEmpty());
        assertFalse(bus.has(Labels.SIM_GROSS_WEIGHT));
    }

    @Test
    void emVooOPesoNaoMuda() {
        valid().publish(bus);
        host.advance(0.1);
        host.flightModel().resetAirborne(8000, 250, 90, 0, -5);
        new FlightPlan("SBKP", "SBGR", "10L", 100, 50_000, 5_000, 130, 133, 138).publish(bus);
        host.advance(0.1);
        assertEquals(68_000, bus.getDouble(Labels.SIM_GROSS_WEIGHT, 0), "o peso de decolagem ficou");
    }

    @Test
    void decolarSemPlanoFicaRegistradoSemBloquear() {
        FlightPlanMonitor monitor = new FlightPlanMonitor();
        assertEquals("SEM PLANO DE VOO", monitor.onTakeoff().orElseThrow());
        valid().publish(bus);
        host.advance(0.1);
        assertTrue(host.plan().onTakeoff().isEmpty());
    }

    @Test
    void ligarOAviaoComecaSemPlano() {
        CartridgeLibrary library = new CartridgeLibrary(new DevFolderReader(Path.of("sd-dev")),
                new CartridgeLoader(new ClasspathModuleFactory()));
        for (String pn : new String[]{"PLT-A320-NEO", "PRP-A320-NEO", "CKP-A320-NEO"}) {
            host.rack().insert(library.load(library.find(pn).orElseThrow()));
        }
        valid().publish(bus);
        host.advance(0.1);
        assertTrue(host.powerOn().report().ok());
        assertTrue(FlightPlan.fromBus(bus).isEmpty());
        assertFalse(bus.has(Labels.SIM_GROSS_WEIGHT));
        assertTrue(host.plan().plan().isEmpty());
    }
}
