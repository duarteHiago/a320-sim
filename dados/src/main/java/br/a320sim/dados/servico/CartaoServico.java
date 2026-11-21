package br.a320sim.dados.servico;

import br.a320sim.dados.modelo.Cartao;
import br.a320sim.dados.modelo.StatusCartao;
import br.a320sim.dados.repositorio.CartaoRepositorio;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Cadastro dos cartões SD físicos e o registro das leituras feitas pelo simulador.
 * <p>
 * No formulário só se edita etiqueta, part number, status e observação; o resto (identificador do
 * volume, hash, datas, contador) vem das leituras.
 */
public class CartaoServico {

    public static final String CAMPO_ETIQUETA = "etiqueta";
    public static final String CAMPO_PART_NUMBER = "partNumber";
    public static final String CAMPO_STATUS = "status";
    public static final String CAMPO_OBSERVACAO = "observacao";

    public static final int ETIQUETA_MAX = 10;
    public static final int PART_NUMBER_MAX = 30;
    public static final int OBSERVACAO_MAX = 60;

    private final CartaoRepositorio repositorio;
    private final Clock relogio;

    public CartaoServico(CartaoRepositorio repositorio, Clock relogio) {
        this.repositorio = repositorio;
        this.relogio = relogio;
    }

    public List<Cartao> listar() {
        return repositorio.listar();
    }

    public Optional<Cartao> buscar(long id) {
        return repositorio.buscar(id);
    }

    /**
     * Salva o que veio do formulário. Etiqueta e part number ficam em maiúsculas.
     *
     * @throws ValidacaoException part number vazio, etiqueta longa demais ou repetida
     */
    public Cartao salvar(Cartao cartao) {
        Cartao c = new Cartao(cartao.id(), maiusculo(cartao.etiqueta()), maiusculo(cartao.partNumber()),
                cartao.volumeId(), cartao.sha256(), cartao.gravadoEm(), cartao.ultimaLeitura(), cartao.leituras(),
                cartao.status(), Erros.limpo(cartao.observacao()));
        validar(c);
        if (c.id() == null) {
            return repositorio.inserir(c);
        }
        repositorio.atualizar(c);
        return c;
    }

    private void validar(Cartao c) {
        Erros erros = new Erros();
        erros.se(c.partNumber() == null, CAMPO_PART_NUMBER, "obrigatorio");
        erros.se(c.partNumber() != null && c.partNumber().length() > PART_NUMBER_MAX, CAMPO_PART_NUMBER,
                "no maximo " + PART_NUMBER_MAX + " caracteres");
        erros.se(c.etiqueta() != null && c.etiqueta().length() > ETIQUETA_MAX, CAMPO_ETIQUETA,
                "no maximo " + ETIQUETA_MAX + " caracteres");
        if (c.etiqueta() != null && !erros.tem(CAMPO_ETIQUETA)) {
            Optional<Cartao> dono = repositorio.buscarPorEtiqueta(c.etiqueta());
            erros.se(dono.isPresent() && !dono.get().id().equals(c.id()), CAMPO_ETIQUETA, "ja usada por outro cartao");
        }
        erros.se(c.status() == null, CAMPO_STATUS, "obrigatorio");
        erros.se(c.observacao() != null && c.observacao().length() > OBSERVACAO_MAX, CAMPO_OBSERVACAO,
                "no maximo " + OBSERVACAO_MAX + " caracteres");
        erros.lancarSeHouver();
    }

    public void excluir(long id) {
        repositorio.excluir(id);
    }

    /**
     * O cadastro de um cartão lido: pelo identificador do volume ou, se ele ainda não foi lido nenhuma vez,
     * um cadastro manual com o mesmo part number e o identificador vazio.
     */
    public Optional<Cartao> identificar(String volumeId, String partNumber) {
        return repositorio.buscarPorVolume(volumeId).or(() -> repositorio.buscarManualSemVolume(partNumber));
    }

    /**
     * Uma leitura com sucesso. Cartão desconhecido é cadastrado sozinho (ATIVO, sem etiqueta);
     * cadastro manual com o mesmo part number e sem identificador é adotado em vez de duplicar.
     * Atualiza última leitura, número de leituras, hash e data de gravação.
     */
    public Cartao registrarLeitura(String volumeId, String partNumber, String sha256, LocalDate gravadoEm) {
        LocalDateTime agora = LocalDateTime.now(relogio).withNano(0);
        Optional<Cartao> conhecido = identificar(volumeId, partNumber);
        if (conhecido.isEmpty()) {
            return repositorio.inserir(new Cartao(null, null, partNumber, volumeId, sha256, gravadoEm, agora, 1,
                    StatusCartao.ATIVO, null));
        }
        Cartao c = conhecido.get();
        Cartao lido = new Cartao(c.id(), c.etiqueta(), c.partNumber(), volumeId, sha256, gravadoEm, agora,
                c.leituras() + 1, c.status(), c.observacao());
        repositorio.atualizar(lido);
        return lido;
    }

    private static String maiusculo(String texto) {
        String t = Erros.limpo(texto);
        return t == null ? null : t.toUpperCase(Locale.ROOT);
    }
}
