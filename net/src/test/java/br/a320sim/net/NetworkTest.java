package br.a320sim.net;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkTest {

    @Test
    void codecRoundTripKeepsNumbersBooleansAndLists() {
        MessageCodec codec = new MessageCodec();
        Message original = new Message.BusSnapshot(Map.of(
                "ADR.IAS", 145.5, "SIM.ON_GROUND", true, "FWC.WARNINGS", List.of("STALL"), "DMC.STYLE", "LCD"));

        Message decoded = codec.decode(codec.encode(original));

        Map<String, Object> values = ((Message.BusSnapshot) decoded).values();
        assertEquals(145.5, values.get("ADR.IAS"));
        assertEquals(true, values.get("SIM.ON_GROUND"));
        assertEquals(List.of("STALL"), values.get("FWC.WARNINGS"));
        assertEquals("LCD", values.get("DMC.STYLE"));
    }

    @Test
    void studentAndInstructorExchangeMessages() throws Exception {
        try (SimServer server = new SimServer(0);
             SimClient client = SimClient.connect("127.0.0.1", server.port(), "aluno")) {

            client.sendControls(Map.of("SIM.THRUST_LEVER", 1.0));
            Map<String, Object> controls = waitFor(server::pollControls);
            assertEquals(1.0, controls.get("SIM.THRUST_LEVER"));
            assertTrue(server.hasStudent());
            assertTrue(server.studentDescription().startsWith("aluno"));

            server.send(new Message.BusSnapshot(Map.of("ADR.ALT", 8000.0)));
            Map<String, Object> snapshot = waitFor(client::pollSnapshot);
            assertEquals(8000.0, snapshot.get("ADR.ALT"));
        }
    }

    private static <T> T waitFor(java.util.function.Supplier<Optional<T>> poll) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            Optional<T> value = poll.get();
            if (value.isPresent()) {
                return value.get();
            }
            Thread.sleep(20);
        }
        throw new AssertionError("nada chegou pela rede");
    }
}
