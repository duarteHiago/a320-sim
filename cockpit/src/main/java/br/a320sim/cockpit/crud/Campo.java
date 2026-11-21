package br.a320sim.cockpit.crud;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Um campo do formulário. Três tipos:
 * <ul>
 *   <li>TEXTO: digitado (só ASCII, como o TextInput), até {@code max} caracteres;</li>
 *   <li>OPCAO: escolhido com esquerda/direita entre {@code opcoes};</li>
 *   <li>LEITURA: só mostrado (ex.: dados que vêm da leitura do cartão).</li>
 * </ul>
 * Um campo pode ficar bloqueado conforme os outros valores (ex.: altitude de posição no chão);
 * bloqueado, ele mostra {@code valorBloqueado} e o cursor pula por cima.
 */
public record Campo(String chave, String rotulo, Tipo tipo, int max, List<String> opcoes,
                    Predicate<Map<String, String>> bloqueadoSe, String valorBloqueado) {

    public enum Tipo { TEXTO, OPCAO, LEITURA }

    public Campo {
        opcoes = opcoes == null ? List.of() : List.copyOf(opcoes);
        bloqueadoSe = bloqueadoSe == null ? valores -> false : bloqueadoSe;
    }

    public static Campo texto(String chave, String rotulo, int max) {
        return new Campo(chave, rotulo, Tipo.TEXTO, max, List.of(), null, null);
    }

    public static Campo opcao(String chave, String rotulo, List<String> opcoes) {
        return new Campo(chave, rotulo, Tipo.OPCAO, 0, opcoes, null, null);
    }

    public static Campo leitura(String chave, String rotulo) {
        return new Campo(chave, rotulo, Tipo.LEITURA, 0, List.of(), null, null);
    }

    /** O mesmo campo, bloqueado quando a condição sobre os valores do formulário for verdadeira. */
    public Campo bloqueadoQuando(Predicate<Map<String, String>> condicao, String valor) {
        return new Campo(chave, rotulo, tipo, max, opcoes, condicao, valor);
    }

    public boolean bloqueado(Map<String, String> valores) {
        return bloqueadoSe.test(valores);
    }
}
