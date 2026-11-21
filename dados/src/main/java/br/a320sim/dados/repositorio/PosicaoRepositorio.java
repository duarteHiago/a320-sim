package br.a320sim.dados.repositorio;

import br.a320sim.dados.DadosException;
import br.a320sim.dados.modelo.Posicao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Tabela {@code posicao}. */
public class PosicaoRepositorio {

    private static final String COLUNAS = "id, nome, altitude_ft, velocidade_kt, proa_graus, distancia_nm, destino, pista";

    private final Connection conexao;

    public PosicaoRepositorio(Connection conexao) {
        this.conexao = conexao;
    }

    /** Na ordem em que foram cadastradas (a do seed: pista, em voo, na final). */
    public List<Posicao> listar() {
        try (PreparedStatement ps = conexao.prepareStatement("SELECT " + COLUNAS + " FROM posicao ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            List<Posicao> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(ler(rs));
            }
            return lista;
        } catch (SQLException e) {
            throw new DadosException("Erro ao listar posicoes", e);
        }
    }

    public Optional<Posicao> buscar(long id) {
        try (PreparedStatement ps = conexao.prepareStatement("SELECT " + COLUNAS + " FROM posicao WHERE id = ?")) {
            ps.setLong(1, id);
            return primeiro(ps);
        } catch (SQLException e) {
            throw new DadosException("Erro ao buscar posicao", e);
        }
    }

    public Optional<Posicao> buscarPorNome(String nome) {
        try (PreparedStatement ps = conexao.prepareStatement(
                "SELECT " + COLUNAS + " FROM posicao WHERE UPPER(nome) = UPPER(?)")) {
            ps.setString(1, nome);
            return primeiro(ps);
        } catch (SQLException e) {
            throw new DadosException("Erro ao buscar posicao", e);
        }
    }

    public Posicao inserir(Posicao p) {
        String sql = "INSERT INTO posicao (nome, altitude_ft, velocidade_kt, proa_graus, distancia_nm, destino, pista) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conexao.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            preencher(ps, p);
            ps.executeUpdate();
            return p.comId(Jdbc.idGerado(ps));
        } catch (SQLException e) {
            throw new DadosException("Erro ao inserir posicao", e);
        }
    }

    public void atualizar(Posicao p) {
        String sql = "UPDATE posicao SET nome = ?, altitude_ft = ?, velocidade_kt = ?, proa_graus = ?, "
                + "distancia_nm = ?, destino = ?, pista = ? WHERE id = ?";
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            preencher(ps, p);
            ps.setLong(8, p.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DadosException("Erro ao atualizar posicao", e);
        }
    }

    public void excluir(long id) {
        try (PreparedStatement ps = conexao.prepareStatement("DELETE FROM posicao WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DadosException("Erro ao excluir posicao", e);
        }
    }

    public int contarSessoes(long id) {
        try (PreparedStatement ps = conexao.prepareStatement("SELECT COUNT(*) FROM sessao WHERE posicao_id = ?")) {
            ps.setLong(1, id);
            return Jdbc.contar(ps);
        } catch (SQLException e) {
            throw new DadosException("Erro ao contar sessoes", e);
        }
    }

    private static void preencher(PreparedStatement ps, Posicao p) throws SQLException {
        ps.setString(1, p.nome());
        ps.setInt(2, p.altitudeFt());
        ps.setInt(3, p.velocidadeKt());
        ps.setInt(4, p.proaGraus());
        ps.setDouble(5, p.distanciaNm());
        ps.setString(6, p.destino());
        ps.setString(7, p.pista());
    }

    private static Optional<Posicao> primeiro(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.of(ler(rs)) : Optional.empty();
        }
    }

    private static Posicao ler(ResultSet rs) throws SQLException {
        return new Posicao(rs.getLong("id"), rs.getString("nome"), rs.getInt("altitude_ft"),
                rs.getInt("velocidade_kt"), rs.getInt("proa_graus"), rs.getDouble("distancia_nm"),
                rs.getString("destino").strip(), rs.getString("pista"));
    }
}
