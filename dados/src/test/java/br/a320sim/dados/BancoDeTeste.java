package br.a320sim.dados;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/** Um banco H2 em memória novo para cada teste, com o seed e um relógio parado. */
public final class BancoDeTeste {

    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    public static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 21, 10, 30, 0);
    public static final Clock RELOGIO = Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO);

    private BancoDeTeste() {
    }

    public static Banco banco() {
        try {
            return Banco.memoria("teste-" + UUID.randomUUID());
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Dados dados() {
        return new Dados(banco(), RELOGIO);
    }

    /** Relógio parado num instante qualquer (para simular uma segunda leitura mais tarde). */
    public static Clock relogio(LocalDateTime quando) {
        Instant instante = quando.atZone(FUSO).toInstant();
        return Clock.fixed(instante, FUSO);
    }
}
