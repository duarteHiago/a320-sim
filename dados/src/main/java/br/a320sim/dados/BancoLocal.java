package br.a320sim.dados;

import br.a320sim.core.AppPaths;
import br.a320sim.host.cartridge.CardPolicy;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Optional;

/**
 * O banco deste PC, que pode não abrir (ex.: outro simulador já está com o arquivo aberto).
 * Sem banco o simulador funciona normalmente, só não grava: as telas mostram "SEM BANCO",
 * a política de cartões aceita todos e as sessões não são gravadas.
 * <p>
 * O banco só é aberto quando alguém pede ({@link #abrir()}); o modo ALUNO nunca pede.
 * {@code -Dsim.db=caminho} troca o arquivo (padrão: {@link AppPaths#database()}).
 */
public final class BancoLocal implements AutoCloseable {


    /** Abre o banco de verdade; nos testes pode ser qualquer coisa que lance exceção. */
    public interface Abridor {
        Dados abrir() throws SQLException;
    }

    private final Abridor abridor;
    private final CardPolicy politica = (leitura, novaInsercao) -> dados().ifPresent(
            d -> new CartaoPolicyBanco(d.cartoes()).check(leitura, novaInsercao));
    private Dados dados;
    private String erro;

    public BancoLocal(Abridor abridor) {
        this.abridor = abridor;
    }

    public static BancoLocal emArquivo(Path caminho) {
        return new BancoLocal(() -> new Dados(Banco.arquivo(caminho)));
    }

    /** O banco deste PC: dados/avion em desenvolvimento, %APPDATA%\AVION instalado, ou -Dsim.db. */
    public static BancoLocal doSistema() {
        return emArquivo(AppPaths.current().database());
    }

    /**
     * Tenta abrir (se ainda não abriu). Se falhar, registra o erro no console e segue sem banco;
     * a próxima chamada tenta de novo (o outro programa pode ter fechado).
     */
    public synchronized Optional<Dados> abrir() {
        if (dados == null) {
            try {
                dados = abridor.abrir();
                erro = null;
            } catch (SQLException | RuntimeException e) {
                erro = e.getMessage();
                System.err.println("[BANCO] SEM BANCO, o simulador segue sem gravar: " + e.getMessage());
            }
        }
        return Optional.ofNullable(dados);
    }

    /** O banco se já estiver aberto, sem tentar abrir. */
    public synchronized Optional<Dados> dados() {
        return Optional.ofNullable(dados);
    }

    public synchronized boolean disponivel() {
        return dados != null;
    }

    /** Mensagem da última falha ao abrir, ou nula. */
    public synchronized String erro() {
        return erro;
    }

    /** Política de cartões que consulta o cadastro quando há banco, e aceita todos quando não há. */
    public CardPolicy politica() {
        return politica;
    }

    /** Fecha (o modo ALUNO fecha para outro simulador no mesmo PC poder abrir). */
    @Override
    public synchronized void close() {
        if (dados != null) {
            dados.close();
            dados = null;
        }
    }
}
