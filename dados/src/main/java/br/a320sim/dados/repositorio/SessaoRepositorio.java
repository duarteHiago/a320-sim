package br.a320sim.dados.repositorio;

import br.a320sim.dados.DadosException;
import br.a320sim.dados.modelo.EventoSessao;
import br.a320sim.dados.modelo.PlanoVoo;
import br.a320sim.dados.modelo.Sessao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Tabelas {@code sessao} e {@code evento_sessao}. */
public class SessaoRepositorio {

    /** Com os nomes do aluno, do instrutor e da posição, para a lista e os detalhes. */
    private static final String SELECT = "SELECT s.*, a.nome AS aluno_nome, i.nome AS instrutor_nome, "
            + "p.nome AS posicao_nome FROM sessao s "
            + "JOIN pessoa a ON a.id = s.aluno_id "
            + "LEFT JOIN pessoa i ON i.id = s.instrutor_id "
            + "LEFT JOIN posicao p ON p.id = s.posicao_id ";

    private final Connection conexao;

    public SessaoRepositorio(Connection conexao) {
        this.conexao = conexao;
    }

    /** Mais recentes primeiro. */
    public List<Sessao> listar() {
        try (PreparedStatement ps = conexao.prepareStatement(SELECT + "ORDER BY s.inicio DESC, s.id DESC");
             ResultSet rs = ps.executeQuery()) {
            List<Sessao> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(ler(rs));
            }
            return lista;
        } catch (SQLException e) {
            throw new DadosException("Erro ao listar sessoes", e);
        }
    }

    public Optional<Sessao> buscar(long id) {
        try (PreparedStatement ps = conexao.prepareStatement(SELECT + "WHERE s.id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(ler(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DadosException("Erro ao buscar sessao", e);
        }
    }

    /** @return o id da sessão criada */
    public long inserir(Sessao s) {
        String sql = "INSERT INTO sessao (aluno_id, instrutor_id, modo, posicao_id, cartuchos, inicio) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conexao.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, s.alunoId());
            Jdbc.longo(ps, 2, s.instrutorId());
            ps.setString(3, s.modo());
            Jdbc.longo(ps, 4, s.posicaoId());
            Jdbc.texto(ps, 5, s.cartuchos());
            Jdbc.instante(ps, 6, s.inicio());
            ps.executeUpdate();
            return Jdbc.idGerado(ps);
        } catch (SQLException e) {
            throw new DadosException("Erro ao abrir sessao", e);
        }
    }

    public void fechar(long id, LocalDateTime fim, double duracaoS, boolean crash, String cartuchos) {
        String sql = "UPDATE sessao SET fim = ?, duracao_s = ?, crash = ?, cartuchos = ? WHERE id = ?";
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            Jdbc.instante(ps, 1, fim);
            ps.setDouble(2, duracaoS);
            ps.setBoolean(3, crash);
            Jdbc.texto(ps, 4, cartuchos);
            ps.setLong(5, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DadosException("Erro ao fechar sessao", e);
        }
    }

    public void avaliar(long id, Double nota, String observacao) {
        try (PreparedStatement ps = conexao.prepareStatement("UPDATE sessao SET nota = ?, observacao = ? WHERE id = ?")) {
            Jdbc.decimal(ps, 1, nota);
            Jdbc.texto(ps, 2, observacao);
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DadosException("Erro ao avaliar sessao", e);
        }
    }

    /** Grava (ou troca) o plano de voo da sessão. */
    public void registrarPlano(long id, PlanoVoo p) {
        String sql = "UPDATE sessao SET plano_origem = ?, plano_destino = ?, plano_pista = ?, plano_fl = ?, "
                + "plano_zfw_kg = ?, plano_combustivel_kg = ?, plano_v1 = ?, plano_vr = ?, plano_v2 = ? WHERE id = ?";
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setString(1, p.origem());
            ps.setString(2, p.destino());
            ps.setString(3, p.pista());
            ps.setInt(4, p.fl());
            ps.setInt(5, p.zfwKg());
            ps.setInt(6, p.combustivelKg());
            ps.setInt(7, p.v1());
            ps.setInt(8, p.vr());
            ps.setInt(9, p.v2());
            ps.setLong(10, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DadosException("Erro ao gravar o plano de voo", e);
        }
    }

    /** Os eventos vão junto (ON DELETE CASCADE). */
    public void excluir(long id) {
        try (PreparedStatement ps = conexao.prepareStatement("DELETE FROM sessao WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DadosException("Erro ao excluir sessao", e);
        }
    }

    /** Grava um lote de eventos de uma vez (o simulador manda aos poucos durante o voo). */
    public void inserirEventos(long sessaoId, List<EventoSessao> eventos) {
        if (eventos.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO evento_sessao (sessao_id, tempo_s, descricao) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            for (EventoSessao evento : eventos) {
                ps.setLong(1, sessaoId);
                ps.setDouble(2, evento.tempoS());
                ps.setString(3, evento.descricao());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new DadosException("Erro ao gravar eventos", e);
        }
    }

    public List<EventoSessao> eventos(long sessaoId) {
        try (PreparedStatement ps = conexao.prepareStatement(
                "SELECT tempo_s, descricao FROM evento_sessao WHERE sessao_id = ? ORDER BY tempo_s, id")) {
            ps.setLong(1, sessaoId);
            List<EventoSessao> lista = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new EventoSessao(rs.getDouble("tempo_s"), rs.getString("descricao")));
                }
            }
            return lista;
        } catch (SQLException e) {
            throw new DadosException("Erro ao listar eventos", e);
        }
    }

    private static Sessao ler(ResultSet rs) throws SQLException {
        return new Sessao(rs.getLong("id"), rs.getLong("aluno_id"), rs.getString("aluno_nome"),
                Jdbc.longo(rs, "instrutor_id"), rs.getString("instrutor_nome"), rs.getString("modo"),
                Jdbc.longo(rs, "posicao_id"), rs.getString("posicao_nome"), rs.getString("cartuchos"),
                Jdbc.instante(rs, "inicio"), Jdbc.instante(rs, "fim"), Jdbc.decimal(rs, "duracao_s"),
                rs.getBoolean("crash"), Jdbc.decimal(rs, "nota"), rs.getString("observacao"), plano(rs));
    }

    private static PlanoVoo plano(ResultSet rs) throws SQLException {
        String origem = rs.getString("plano_origem");
        if (origem == null) {
            return null;
        }
        return new PlanoVoo(origem.strip(), rs.getString("plano_destino").strip(), rs.getString("plano_pista"),
                rs.getInt("plano_fl"), rs.getInt("plano_zfw_kg"), rs.getInt("plano_combustivel_kg"),
                rs.getInt("plano_v1"), rs.getInt("plano_vr"), rs.getInt("plano_v2"));
    }
}
