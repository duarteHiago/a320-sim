package br.a320sim.dados.repositorio;

import br.a320sim.dados.DadosException;
import br.a320sim.dados.modelo.Cartao;
import br.a320sim.dados.modelo.StatusCartao;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Tabela {@code cartao}. */
public class CartaoRepositorio {

    private static final String COLUNAS = "id, etiqueta, part_number, volume_id, sha256, gravado_em, "
            + "ultima_leitura, leituras, status, observacao";

    private final Connection conexao;

    public CartaoRepositorio(Connection conexao) {
        this.conexao = conexao;
    }

    /** Etiquetados primeiro, em ordem de etiqueta; os sem etiqueta (lidos e não etiquetados) no fim. */
    public List<Cartao> listar() {
        String sql = "SELECT " + COLUNAS + " FROM cartao ORDER BY etiqueta IS NULL, etiqueta, part_number, id";
        try (PreparedStatement ps = conexao.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            List<Cartao> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(ler(rs));
            }
            return lista;
        } catch (SQLException e) {
            throw new DadosException("Erro ao listar cartoes", e);
        }
    }

    public Optional<Cartao> buscar(long id) {
        return um("SELECT " + COLUNAS + " FROM cartao WHERE id = ?", ps -> ps.setLong(1, id));
    }

    public Optional<Cartao> buscarPorEtiqueta(String etiqueta) {
        return um("SELECT " + COLUNAS + " FROM cartao WHERE UPPER(etiqueta) = UPPER(?)",
                ps -> ps.setString(1, etiqueta));
    }

    public Optional<Cartao> buscarPorVolume(String volumeId) {
        return um("SELECT " + COLUNAS + " FROM cartao WHERE volume_id = ?", ps -> ps.setString(1, volumeId));
    }

    /** Cadastro manual ainda não ligado a um cartão lido (o mais antigo, se houver mais de um). */
    public Optional<Cartao> buscarManualSemVolume(String partNumber) {
        return um("SELECT " + COLUNAS + " FROM cartao WHERE volume_id IS NULL AND part_number = ? ORDER BY id",
                ps -> ps.setString(1, partNumber));
    }

    public Cartao inserir(Cartao c) {
        String sql = "INSERT INTO cartao (etiqueta, part_number, volume_id, sha256, gravado_em, ultima_leitura, "
                + "leituras, status, observacao) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conexao.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            preencher(ps, c);
            ps.executeUpdate();
            return c.comId(Jdbc.idGerado(ps));
        } catch (SQLException e) {
            throw new DadosException("Erro ao inserir cartao", e);
        }
    }

    public void atualizar(Cartao c) {
        String sql = "UPDATE cartao SET etiqueta = ?, part_number = ?, volume_id = ?, sha256 = ?, gravado_em = ?, "
                + "ultima_leitura = ?, leituras = ?, status = ?, observacao = ? WHERE id = ?";
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            preencher(ps, c);
            ps.setLong(10, c.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DadosException("Erro ao atualizar cartao", e);
        }
    }

    public void excluir(long id) {
        try (PreparedStatement ps = conexao.prepareStatement("DELETE FROM cartao WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DadosException("Erro ao excluir cartao", e);
        }
    }

    private static void preencher(PreparedStatement ps, Cartao c) throws SQLException {
        Jdbc.texto(ps, 1, c.etiqueta());
        ps.setString(2, c.partNumber());
        Jdbc.texto(ps, 3, c.volumeId());
        Jdbc.texto(ps, 4, c.sha256());
        if (c.gravadoEm() == null) {
            ps.setNull(5, Types.DATE);
        } else {
            ps.setDate(5, Date.valueOf(c.gravadoEm()));
        }
        Jdbc.instante(ps, 6, c.ultimaLeitura());
        ps.setInt(7, c.leituras());
        ps.setString(8, c.status().name());
        Jdbc.texto(ps, 9, c.observacao());
    }

    private interface Parametros {
        void aplicar(PreparedStatement ps) throws SQLException;
    }

    private Optional<Cartao> um(String sql, Parametros parametros) {
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            parametros.aplicar(ps);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(ler(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DadosException("Erro ao buscar cartao", e);
        }
    }

    private static Cartao ler(ResultSet rs) throws SQLException {
        Date gravado = rs.getDate("gravado_em");
        return new Cartao(rs.getLong("id"), rs.getString("etiqueta"), rs.getString("part_number"),
                rs.getString("volume_id"), rs.getString("sha256"), gravado == null ? null : gravado.toLocalDate(),
                Jdbc.instante(rs, "ultima_leitura"), rs.getInt("leituras"), StatusCartao.valueOf(rs.getString("status")),
                rs.getString("observacao"));
    }
}
