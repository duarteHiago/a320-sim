package br.a320sim.dados;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * A conexão com o H2 embutido. Ao abrir, roda o {@link Migrador}, então o banco está sempre na versão
 * do programa. Em arquivo (o normal) ou em memória (testes).
 * <p>
 * O H2 em arquivo só aceita um processo por vez: um segundo simulador no mesmo PC não consegue abrir.
 */
public final class Banco implements AutoCloseable {

    private final Connection conexao;
    private final boolean memoria;

    private Banco(Connection conexao, boolean memoria) {
        this.conexao = conexao;
        this.memoria = memoria;
    }

    /** @param caminho sem extensão: "dados/avion" vira dados/avion.mv.db */
    public static Banco arquivo(Path caminho) throws SQLException {
        return abrir("jdbc:h2:file:" + caminho.toAbsolutePath(), false);
    }

    /** Banco só em memória, que some quando é fechado. Cada nome é um banco separado. */
    public static Banco memoria(String nome) throws SQLException {
        return abrir("jdbc:h2:mem:" + nome + ";DB_CLOSE_DELAY=-1", true);
    }

    private static Banco abrir(String url, boolean memoria) throws SQLException {
        Connection conexao = DriverManager.getConnection(url, "sa", "");
        try {
            Migrador.migrar(conexao);
        } catch (SQLException | RuntimeException e) {
            conexao.close();
            throw e;
        }
        return new Banco(conexao, memoria);
    }

    public Connection conexao() {
        return conexao;
    }

    /** Fecha a conexão; num banco em memória, também apaga tudo. */
    @Override
    public void close() {
        try {
            if (memoria) {
                try (Statement st = conexao.createStatement()) {
                    st.execute("SHUTDOWN");
                }
            }
            conexao.close();
        } catch (SQLException e) {
            System.err.println("[BANCO] erro ao fechar: " + e.getMessage());
        }
    }
}
