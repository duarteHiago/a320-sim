package br.a320sim.dados;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Migrador simples: cada script {@code db/V<n>__descricao.sql} roda uma vez só, em ordem.
 * A tabela {@code versao_esquema} guarda os que já rodaram.
 * <p>
 * Os scripts ficam listados em {@code db/migracoes.txt}, porque de dentro de um .jar não dá para
 * listar uma pasta. Para mudar o esquema: crie o próximo V<n> e acrescente uma linha no índice.
 */
public final class Migrador {

    static final String INDICE = "db/migracoes.txt";

    private Migrador() {
    }

    /** @return quantos scripts foram aplicados agora */
    public static int migrar(Connection conexao) throws SQLException {
        try (Statement st = conexao.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS versao_esquema ("
                    + "versao INT PRIMARY KEY, script VARCHAR(100) NOT NULL, "
                    + "aplicado_em TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL)");
        }
        int atual = versaoAtual(conexao);
        int aplicados = 0;
        for (String script : lerLinhas(INDICE)) {
            int versao = versaoDe(script);
            if (versao <= atual) {
                continue;
            }
            aplicar(conexao, versao, script);
            atual = versao;
            aplicados++;
        }
        return aplicados;
    }

    public static int versaoAtual(Connection conexao) throws SQLException {
        try (Statement st = conexao.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(versao), 0) FROM versao_esquema")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** "V2__seed.sql" -> 2 */
    static int versaoDe(String script) {
        if (!script.matches("V\\d+__.+\\.sql")) {
            throw new IllegalArgumentException("Nome de migracao invalido: " + script + " (use V<n>__descricao.sql)");
        }
        return Integer.parseInt(script.substring(1, script.indexOf("__")));
    }

    /** Roda o script inteiro numa transação e registra a versão; se algo falha, nada fica pela metade. */
    private static void aplicar(Connection conexao, int versao, String script) throws SQLException {
        boolean auto = conexao.getAutoCommit();
        conexao.setAutoCommit(false);
        try (Statement st = conexao.createStatement()) {
            for (String comando : comandos(ler("db/" + script))) {
                st.execute(comando);
            }
            try (PreparedStatement ps = conexao.prepareStatement(
                    "INSERT INTO versao_esquema (versao, script) VALUES (?, ?)")) {
                ps.setInt(1, versao);
                ps.setString(2, script);
                ps.executeUpdate();
            }
            conexao.commit();
        } catch (SQLException e) {
            conexao.rollback();
            throw new SQLException("Migracao " + script + " falhou: " + e.getMessage(), e);
        } finally {
            conexao.setAutoCommit(auto);
        }
    }

    /** Separa o script em comandos por ";" no fim da linha, ignorando linhas de comentário "--". */
    static List<String> comandos(String sql) {
        List<String> comandos = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        for (String linha : sql.split("\\R")) {
            String limpa = linha.strip();
            if (limpa.isEmpty() || limpa.startsWith("--")) {
                continue;
            }
            atual.append(linha).append('\n');
            if (limpa.endsWith(";")) {
                String comando = atual.toString().strip();
                comandos.add(comando.substring(0, comando.length() - 1));
                atual.setLength(0);
            }
        }
        if (!atual.toString().isBlank()) {
            comandos.add(atual.toString().strip());
        }
        return comandos;
    }

    private static List<String> lerLinhas(String recurso) {
        return ler(recurso).lines().map(String::strip).filter(l -> !l.isEmpty() && !l.startsWith("#")).toList();
    }

    private static String ler(String recurso) {
        try (InputStream in = Migrador.class.getClassLoader().getResourceAsStream(recurso)) {
            if (in == null) {
                throw new IllegalStateException("Recurso nao encontrado: " + recurso);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
