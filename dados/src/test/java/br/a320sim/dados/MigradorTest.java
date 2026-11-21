package br.a320sim.dados;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigradorTest {

    @TempDir
    Path temp;

    @Test
    void bancoNovoRecebeTodasAsMigracoesESeed() throws SQLException {
        try (Banco banco = Banco.memoria("migrador-novo")) {
            assertEquals(4, Migrador.versaoAtual(banco.conexao()));
            assertEquals(4, contar(banco.conexao(), "pessoa"));
            assertEquals(6, contar(banco.conexao(), "cartao"));
            assertEquals(7, contar(banco.conexao(), "posicao"), "3 de SBKP + 4 de GRU e CGH (V3)");
        }
    }

    @Test
    void cadaMigracaoRodaUmaVezSo() throws SQLException {
        try (Banco banco = Banco.memoria("migrador-repetido")) {
            assertEquals(0, Migrador.migrar(banco.conexao()), "nada novo para aplicar");
            assertEquals(4, contar(banco.conexao(), "pessoa"), "o seed não pode duplicar");
        }
    }

    @Test
    void bancoEmArquivoGuardaOsDadosEntreAberturas() throws SQLException {
        Path caminho = temp.resolve("avion");
        try (Banco banco = Banco.arquivo(caminho); Statement st = banco.conexao().createStatement()) {
            st.execute("DELETE FROM cartao");
        }
        try (Banco banco = Banco.arquivo(caminho)) {
            assertEquals(0, contar(banco.conexao(), "cartao"), "reabrir não roda o seed de novo");
            assertEquals(4, Migrador.versaoAtual(banco.conexao()));
        }
        assertTrue(caminho.resolveSibling("avion.mv.db").toFile().isFile());
    }

    @Test
    void separaComandosIgnorandoComentarios() {
        String sql = """
                -- comentario
                CREATE TABLE a (x INT);

                INSERT INTO a VALUES
                    (1),
                    (2);
                """;
        assertEquals(List.of("CREATE TABLE a (x INT)", "INSERT INTO a VALUES\n    (1),\n    (2)"),
                Migrador.comandos(sql));
    }

    @Test
    void nomeDeScriptPrecisaTerVersao() {
        assertEquals(12, Migrador.versaoDe("V12__mais_campos.sql"));
        assertThrows(IllegalArgumentException.class, () -> Migrador.versaoDe("schema.sql"));
    }

    private static int contar(Connection conexao, String tabela) throws SQLException {
        try (Statement st = conexao.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + tabela)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
