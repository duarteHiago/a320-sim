package br.a320sim.cockpit.crud;

import br.a320sim.dados.modelo.Cartao;
import br.a320sim.dados.modelo.StatusCartao;
import br.a320sim.dados.servico.CartaoServico;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cadastro dos cartões SD. Etiqueta, part number, status e observação são digitados;
 * o resto vem das leituras do simulador e só aparece.
 */
public final class CartoesAdapter implements CrudAdapter<Cartao> {

    static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final CartaoServico cartoes;

    public CartoesAdapter(CartaoServico cartoes) {
        this.cartoes = cartoes;
    }

    @Override
    public String titulo() {
        return "CARTOES";
    }

    @Override
    public List<Cartao> listar() {
        return cartoes.listar();
    }

    @Override
    public String linha(Cartao c) {
        String status = c.status() == StatusCartao.ATIVO ? "" : " " + c.status();
        return String.format("%-6s %s%s", c.etiqueta() == null ? "(?)" : c.etiqueta(), c.partNumber(), status);
    }

    @Override
    public boolean apagado(Cartao c) {
        return c.status() != StatusCartao.ATIVO;
    }

    @Override
    public List<String> detalhes(Cartao c) {
        return List.of(
                "ETIQUETA|" + (c.etiqueta() == null ? "(sem etiqueta: preencha)" : c.etiqueta()),
                "PART NUMBER|" + c.partNumber(),
                "STATUS|" + c.status(),
                "VOLUME|" + traco(c.volumeId()),
                "SHA-256|" + (c.sha256() == null ? "-" : c.sha256().substring(0, 24) + "..."),
                "GRAVADO EM|" + (c.gravadoEm() == null ? "-" : DATA.format(c.gravadoEm())),
                "ULT. LEITURA|" + (c.ultimaLeitura() == null ? "nunca lido" : DATA_HORA.format(c.ultimaLeitura())),
                "LEITURAS|" + c.leituras(),
                "OBS|" + traco(c.observacao()));
    }

    @Override
    public List<Campo> campos() {
        return List.of(
                Campo.texto(CartaoServico.CAMPO_ETIQUETA, "ETIQUETA", CartaoServico.ETIQUETA_MAX),
                Campo.texto(CartaoServico.CAMPO_PART_NUMBER, "PART NUMBER", CartaoServico.PART_NUMBER_MAX),
                Campo.opcao(CartaoServico.CAMPO_STATUS, "STATUS",
                        Arrays.stream(StatusCartao.values()).map(Enum::name).toList()),
                Campo.texto(CartaoServico.CAMPO_OBSERVACAO, "OBS", CartaoServico.OBSERVACAO_MAX),
                Campo.leitura("volume", "VOLUME"),
                Campo.leitura("leituras", "LEITURAS"),
                Campo.leitura("ultima", "ULT. LEITURA"));
    }

    @Override
    public Map<String, String> valoresNovos() {
        return Map.of(CartaoServico.CAMPO_STATUS, StatusCartao.ATIVO.name(), "volume", "-", "leituras", "0",
                "ultima", "nunca lido");
    }

    @Override
    public Map<String, String> valores(Cartao c) {
        Map<String, String> v = new HashMap<>();
        v.put(CartaoServico.CAMPO_ETIQUETA, c.etiqueta() == null ? "" : c.etiqueta());
        v.put(CartaoServico.CAMPO_PART_NUMBER, c.partNumber());
        v.put(CartaoServico.CAMPO_STATUS, c.status().name());
        v.put(CartaoServico.CAMPO_OBSERVACAO, c.observacao() == null ? "" : c.observacao());
        v.put("volume", traco(c.volumeId()));
        v.put("leituras", String.valueOf(c.leituras()));
        v.put("ultima", c.ultimaLeitura() == null ? "nunca lido" : DATA_HORA.format(c.ultimaLeitura()));
        return v;
    }

    @Override
    public Cartao salvar(Cartao item, Map<String, String> v) {
        String etiqueta = v.get(CartaoServico.CAMPO_ETIQUETA);
        String pn = v.get(CartaoServico.CAMPO_PART_NUMBER);
        StatusCartao status = StatusCartao.valueOf(v.get(CartaoServico.CAMPO_STATUS));
        String obs = v.get(CartaoServico.CAMPO_OBSERVACAO);
        if (item == null) {
            return cartoes.salvar(Cartao.manual(etiqueta, pn, status, obs));
        }
        // O que veio das leituras fica como está
        return cartoes.salvar(new Cartao(item.id(), etiqueta, pn, item.volumeId(), item.sha256(), item.gravadoEm(),
                item.ultimaLeitura(), item.leituras(), status, obs));
    }

    @Override
    public String perguntaExclusao(Cartao c) {
        return "EXCLUIR O CARTAO " + c.nomeCurto() + "?";
    }

    @Override
    public String excluir(Cartao c) {
        cartoes.excluir(c.id());
        return "CARTAO EXCLUIDO";
    }

    private static String traco(String texto) {
        return texto == null ? "-" : texto;
    }
}
