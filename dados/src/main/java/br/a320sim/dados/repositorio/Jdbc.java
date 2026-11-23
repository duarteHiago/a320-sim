package br.a320sim.dados.repositorio;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;

/** Pequenos atalhos de JDBC para colunas que aceitam NULL. */
final class Jdbc {

    private Jdbc() {
    }

    static void texto(PreparedStatement ps, int i, String valor) throws SQLException {
        if (valor == null) {
            ps.setNull(i, Types.VARCHAR);
        } else {
            ps.setString(i, valor);
        }
    }

    static void longo(PreparedStatement ps, int i, Long valor) throws SQLException {
        if (valor == null) {
            ps.setNull(i, Types.BIGINT);
        } else {
            ps.setLong(i, valor);
        }
    }

    static void decimal(PreparedStatement ps, int i, Double valor) throws SQLException {
        if (valor == null) {
            ps.setNull(i, Types.DOUBLE);
        } else {
            ps.setDouble(i, valor);
        }
    }

    static void instante(PreparedStatement ps, int i, LocalDateTime valor) throws SQLException {
        if (valor == null) {
            ps.setNull(i, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(i, Timestamp.valueOf(valor));
        }
    }

    static Long longo(ResultSet rs, String coluna) throws SQLException {
        long v = rs.getLong(coluna);
        return rs.wasNull() ? null : v;
    }

    static Double decimal(ResultSet rs, String coluna) throws SQLException {
        double v = rs.getDouble(coluna);
        return rs.wasNull() ? null : v;
    }

    static LocalDateTime instante(ResultSet rs, String coluna) throws SQLException {
        Timestamp t = rs.getTimestamp(coluna);
        return t == null ? null : t.toLocalDateTime();
    }

    /** O id gerado pelo último INSERT (o PreparedStatement precisa ter sido criado com RETURN_GENERATED_KEYS). */
    static long idGerado(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.getGeneratedKeys()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    static int contar(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
