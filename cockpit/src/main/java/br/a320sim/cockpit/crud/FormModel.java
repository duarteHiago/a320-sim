package br.a320sim.cockpit.crud;

import br.a320sim.dados.DadosException;
import br.a320sim.dados.servico.RegraException;
import br.a320sim.dados.servico.ValidacaoException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A lógica do formulário, sem nada de LibGDX (para poder testar): valores, campo atual,
 * troca de campo, opções, digitação e os erros de validação de cada campo.
 * <p>
 * A tela só traduz teclas em chamadas: setas cima/baixo → {@link #anterior()}/{@link #proximo()},
 * esquerda/direita → {@link #opcaoAnterior()}/{@link #opcaoSeguinte()}, letras → {@link #digitar(char)},
 * ENTER → {@link #salvar}.
 */
public final class FormModel {

    /** O que fazer com os valores ao salvar (normalmente: converter e chamar o serviço). */
    @FunctionalInterface
    public interface Acao {
        void salvar(Map<String, String> valores);
    }

    private final String titulo;
    private final List<Campo> campos;
    private final Map<String, String> valores = new LinkedHashMap<>();
    private final Map<String, String> erros = new HashMap<>();
    private String erroGeral;
    private int atual;

    public FormModel(String titulo, List<Campo> campos, Map<String, String> iniciais) {
        this.titulo = titulo;
        this.campos = List.copyOf(campos);
        for (Campo campo : this.campos) {
            String valor = iniciais.getOrDefault(campo.chave(), "");
            valores.put(campo.chave(), valor == null ? "" : valor);
        }
        ajustarOpcoes();
        atual = -1;
        proximo();
    }

    public String titulo() {
        return titulo;
    }

    public List<Campo> campos() {
        return campos;
    }

    /** @return o campo com o cursor, ou null se nenhum é editável */
    public Campo atual() {
        return atual >= 0 && atual < campos.size() ? campos.get(atual) : null;
    }

    /** Pode receber o cursor: não é só leitura e não está bloqueado agora. */
    public boolean editavel(Campo campo) {
        return campo.tipo() != Campo.Tipo.LEITURA && !campo.bloqueado(valores);
    }

    /** Próximo campo editável (para no último). */
    public void proximo() {
        for (int i = atual + 1; i < campos.size(); i++) {
            if (editavel(campos.get(i))) {
                atual = i;
                return;
            }
        }
        if (atual() == null || !editavel(atual())) {
            atual = -1; // nenhum campo editável
        }
    }

    /** Campo editável anterior (para no primeiro). */
    public void anterior() {
        for (int i = atual - 1; i >= 0; i--) {
            if (editavel(campos.get(i))) {
                atual = i;
                return;
            }
        }
    }

    /** Põe o cursor num campo pela chave; false se ele não existe ou não é editável agora. */
    public boolean irPara(String chave) {
        for (int i = 0; i < campos.size(); i++) {
            if (campos.get(i).chave().equals(chave) && editavel(campos.get(i))) {
                atual = i;
                return true;
            }
        }
        return false;
    }

    public void opcaoSeguinte() {
        girarOpcao(1);
    }

    public void opcaoAnterior() {
        girarOpcao(-1);
    }

    private void girarOpcao(int direcao) {
        Campo campo = atual();
        if (campo == null || campo.tipo() != Campo.Tipo.OPCAO || campo.opcoes(valores).isEmpty()) {
            return;
        }
        List<String> opcoes = campo.opcoes(valores);
        int i = Math.max(0, opcoes.indexOf(valores.get(campo.chave())));
        valores.put(campo.chave(), opcoes.get(Math.floorMod(i + direcao, opcoes.size())));
        erros.remove(campo.chave());
        ajustarOpcoes();
    }

    /**
     * Campo de opção com um valor que não está entre as opções (ex.: trocou o destino e a pista antiga
     * não existe no aeroporto novo) passa para a primeira opção.
     */
    private void ajustarOpcoes() {
        for (Campo campo : campos) {
            if (campo.tipo() == Campo.Tipo.OPCAO) {
                List<String> opcoes = campo.opcoes(valores);
                if (!opcoes.contains(valores.get(campo.chave()))) {
                    valores.put(campo.chave(), opcoes.isEmpty() ? "" : opcoes.get(0));
                }
            }
        }
    }

    /** Uma tecla digitada: letras e números ASCII entram, '\b' apaga. Só vale em campo de texto. */
    public void digitar(char c) {
        Campo campo = atual();
        if (campo == null || campo.tipo() != Campo.Tipo.TEXTO) {
            return;
        }
        String texto = valores.get(campo.chave());
        if (c == '\b') {
            if (!texto.isEmpty()) {
                valores.put(campo.chave(), texto.substring(0, texto.length() - 1));
            }
        } else if (c >= 32 && c < 127 && texto.length() < campo.max()) {
            valores.put(campo.chave(), texto + c);
        } else {
            return;
        }
        erros.remove(campo.chave());
    }

    /** O que aparece no campo: o valor digitado, ou o valor fixo se ele estiver bloqueado. */
    public String valor(String chave) {
        for (Campo campo : campos) {
            if (campo.chave().equals(chave) && campo.bloqueado(valores) && campo.valorBloqueado(valores) != null) {
                return campo.valorBloqueado(valores);
            }
        }
        return valores.getOrDefault(chave, "");
    }

    /** Os valores como serão salvos (campos bloqueados já com o valor fixo). */
    public Map<String, String> valores() {
        Map<String, String> copia = new LinkedHashMap<>();
        for (Campo campo : campos) {
            copia.put(campo.chave(), valor(campo.chave()));
        }
        return copia;
    }

    public String erro(String chave) {
        return erros.get(chave);
    }

    /** Erro que não é de um campo só (ex.: regra de negócio, banco fora do ar). */
    public String erroGeral() {
        return erroGeral;
    }

    /**
     * Roda a ação com os valores. Se der erro de validação, cada mensagem vai para o seu campo
     * (e o cursor para o primeiro campo com erro).
     *
     * @return true se salvou
     */
    public boolean salvar(Acao acao) {
        erros.clear();
        erroGeral = null;
        try {
            acao.salvar(valores());
            return true;
        } catch (ValidacaoException e) {
            e.erros().forEach((chave, mensagem) -> {
                if (campos.stream().anyMatch(c -> c.chave().equals(chave))) {
                    erros.put(chave, mensagem);
                } else {
                    erroGeral = mensagem;
                }
            });
            for (int i = 0; i < campos.size(); i++) {
                if (erros.containsKey(campos.get(i).chave()) && editavel(campos.get(i))) {
                    atual = i;
                    break;
                }
            }
        } catch (RegraException | DadosException e) {
            erroGeral = e.getMessage();
        }
        return false;
    }
}
