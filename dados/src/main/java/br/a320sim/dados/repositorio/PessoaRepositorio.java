package br.a320sim.dados.repositorio;

import br.a320sim.dados.DadosException;
import br.a320sim.dados.modelo.Perfil;
import br.a320sim.dados.modelo.Pessoa;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Tabela {@code pessoa}: só SQL, sem regra de negócio (essa fica no PessoaServico). */
public class PessoaRepositorio {

    private static final String COLUNAS = "id, nome, matricula, perfil, email, ativo";

    private final Connection conexao;

    public PessoaRepositorio(Connection conexao) {
        this.conexao = conexao;
    }

    /** Alunos primeiro, depois instrutores; cada grupo em ordem de nome. */
    public List<Pessoa> listar() {
        return consultar("SELECT " + COLUNAS + " FROM pessoa ORDER BY perfil, nome", null);
    }

    public List<Pessoa> listarAtivos(Perfil perfil) {
        return consultar("SELECT " + COLUNAS + " FROM pessoa WHERE ativo AND perfil = ? ORDER BY nome", perfil.name());
    }

    public Optional<Pessoa> buscar(long id) {
        try (PreparedStatement ps = conexao.prepareStatement("SELECT " + COLUNAS + " FROM pessoa WHERE id = ?")) {
            ps.setLong(1, id);
            return primeiro(ps);
        } catch (SQLException e) {
            throw new DadosException("Erro ao buscar pessoa", e);
        }
    }

    public Optional<Pessoa> buscarPorMatricula(String matricula) {
        try (PreparedStatement ps = conexao.prepareStatement(
                "SELECT " + COLUNAS + " FROM pessoa WHERE UPPER(matricula) = UPPER(?)")) {
            ps.setString(1, matricula);
            return primeiro(ps);
        } catch (SQLException e) {
            throw new DadosException("Erro ao buscar matricula", e);
        }
    }

    public Pessoa inserir(Pessoa p) {
        String sql = "INSERT INTO pessoa (nome, matricula, perfil, email, ativo) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conexao.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            preencher(ps, p);
            ps.executeUpdate();
            return p.comId(Jdbc.idGerado(ps));
        } catch (SQLException e) {
            throw new DadosException("Erro ao inserir pessoa", e);
        }
    }

    public void atualizar(Pessoa p) {
        String sql = "UPDATE pessoa SET nome = ?, matricula = ?, perfil = ?, email = ?, ativo = ? WHERE id = ?";
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            preencher(ps, p);
            ps.setLong(6, p.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DadosException("Erro ao atualizar pessoa", e);
        }
    }

    public void excluir(long id) {
        try (PreparedStatement ps = conexao.prepareStatement("DELETE FROM pessoa WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DadosException("Erro ao excluir pessoa", e);
        }
    }

    /** Em quantas sessões a pessoa aparece, como aluno ou como instrutor. */
    public int contarSessoes(long id) {
        try (PreparedStatement ps = conexao.prepareStatement(
                "SELECT COUNT(*) FROM sessao WHERE aluno_id = ? OR instrutor_id = ?")) {
            ps.setLong(1, id);
            ps.setLong(2, id);
            return Jdbc.contar(ps);
        } catch (SQLException e) {
            throw new DadosException("Erro ao contar sessoes", e);
        }
    }

    private static void preencher(PreparedStatement ps, Pessoa p) throws SQLException {
        ps.setString(1, p.nome());
        ps.setString(2, p.matricula());
        ps.setString(3, p.perfil().name());
        Jdbc.texto(ps, 4, p.email());
        ps.setBoolean(5, p.ativo());
    }

    private List<Pessoa> consultar(String sql, String parametro) {
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            if (parametro != null) {
                ps.setString(1, parametro);
            }
            List<Pessoa> lista = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(ler(rs));
                }
            }
            return lista;
        } catch (SQLException e) {
            throw new DadosException("Erro ao listar pessoas", e);
        }
    }

    private static Optional<Pessoa> primeiro(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.of(ler(rs)) : Optional.empty();
        }
    }

    private static Pessoa ler(ResultSet rs) throws SQLException {
        return new Pessoa(rs.getLong("id"), rs.getString("nome"), rs.getString("matricula"),
                Perfil.valueOf(rs.getString("perfil")), rs.getString("email"), rs.getBoolean("ativo"));
    }
}
