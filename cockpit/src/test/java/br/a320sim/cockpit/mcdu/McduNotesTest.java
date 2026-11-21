package br.a320sim.cockpit.mcdu;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McduNotesTest {

    @Test
    void todosOsExemplosSaoPlanosValidos() {
        for (McduNotes.Example e : McduNotes.examples()) {
            assertEquals(Map.of(), e.plan().validate(), e.name());
        }
    }

    @Test
    void textoCabeNoBlocoESoTemAscii() {
        for (String line : McduNotes.lines()) {
            assertTrue(line.length() <= McduNotes.WIDTH_CHARS, "longa demais: " + line);
            assertTrue(line.chars().allMatch(c -> c >= 32 && c < 127), "a fonte pixel so tem ASCII: " + line);
        }
    }

    @Test
    void exemploVooCurtoComAsVelocidadesSugeridas() {
        assertEquals("  V1/VR/V2 138/141/146", McduNotes.lines().get(3), "66 t de peso de decolagem");
    }
}
