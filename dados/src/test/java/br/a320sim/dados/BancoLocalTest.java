package br.a320sim.dados;

import br.a320sim.host.cartridge.CardReading;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** O simulador tem que funcionar sem banco: só não grava. */
class BancoLocalTest {

    @TempDir
    Path temp;

    @Test
    void bancoQueNaoAbreDeixaOSimuladorSeguirSemGravar() throws IOException {
        // Um arquivo comum no lugar da pasta: o H2 não consegue criar o banco ali
        Path arquivo = Files.writeString(temp.resolve("nao-e-pasta"), "x");
        try (BancoLocal banco = BancoLocal.emArquivo(arquivo.resolve("avion"))) {
            assertTrue(banco.abrir().isEmpty());
            assertFalse(banco.disponivel());
            assertNotNull(banco.erro(), "o erro fica guardado para o console");

            // Cartão nenhum é recusado por falta de cadastro
            banco.politica().check(new CardReading("PLT-A320-CEO", "1234-5678", "ab".repeat(32), null), true);
        }
    }

    @Test
    void tentaDeNovoNaProximaVez() {
        int[] tentativas = {0};
        BancoLocal banco = new BancoLocal(() -> {
            tentativas[0]++;
            if (tentativas[0] == 1) {
                throw new SQLException("Database may be already in use");
            }
            return BancoDeTeste.dados();
        });
        assertTrue(banco.abrir().isEmpty());
        assertEquals("Database may be already in use", banco.erro());
        assertTrue(banco.abrir().isPresent(), "o outro simulador fechou");
        assertTrue(banco.disponivel());
        banco.abrir();
        assertEquals(2, tentativas[0], "aberto, nao abre de novo");
        banco.close();
        assertFalse(banco.disponivel());
    }

    @Test
    void naoAbreSozinho() {
        BancoLocal banco = new BancoLocal(() -> {
            throw new AssertionError("ninguem pediu o banco");
        });
        assertTrue(banco.dados().isEmpty());
        assertFalse(banco.disponivel());
        banco.politica().check(new CardReading("PLT-A320-CEO", "X", "ab".repeat(32), null), true);
    }

    @Test
    void emArquivoAbreECriaOEsquema() {
        try (BancoLocal banco = BancoLocal.emArquivo(temp.resolve("avion"))) {
            assertEquals(4, banco.abrir().orElseThrow().pessoas().listar().size());
        }
    }
}
