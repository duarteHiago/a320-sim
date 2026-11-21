package br.a320sim.cockpit.crud;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Um campo do formulário. Três tipos:
 * <ul>
 *   <li>TEXTO: digitado (só ASCII, como o TextInput), até {@code max} caracteres;</li>
 *   <li>OPCAO: escolhido com esquerda/direita entre as opções;</li>
 *   <li>LEITURA: só mostrado (ex.: dados que vêm da leitura do cartão).</li>
 * </ul>
 * As opções podem depender dos outros valores (ex.: as pistas do aeroporto escolhido).
 * Um campo pode ficar bloqueado conforme os outros valores (ex.: altitude de posição no chão);
 * bloqueado, ele mostra o valor fixo calculado e o cursor pula por cima.
 */
public record Campo(String chave, String rotulo, Tipo tipo, int max,
                    Function<Map<String, String>, List<String>> opcoesDe,
                    Predicate<Map<String, String>> bloqueadoSe,
                    Function<Map<String, String>, String> valorBloqueadoDe) {

    public enum Tipo { TEXTO, OPCAO, LEITURA }

    public Campo {
        opcoesDe = opcoesDe == null ? valores -> List.of() : opcoesDe;
        bloqueadoSe = bloqueadoSe == null ? valores -> false : bloqueadoSe;
        valorBloqueadoDe = valorBloqueadoDe == null ? valores -> null : valorBloqueadoDe;
    }

    public static Campo texto(String chave, String rotulo, int max) {
        return new Campo(chave, rotulo, Tipo.TEXTO, max, null, null, null);
    }

    public static Campo opcao(String chave, String rotulo, List<String> opcoes) {
        List<String> fixas = List.copyOf(opcoes);
        return new Campo(chave, rotulo, Tipo.OPCAO, 0, valores -> fixas, null, null);
    }

    /** Opções que mudam conforme os outros campos (ex.: pistas do destino). */
    public static Campo opcao(String chave, String rotulo, Function<Map<String, String>, List<String>> opcoes) {
        return new Campo(chave, rotulo, Tipo.OPCAO, 0, opcoes, null, null);
    }

    public static Campo leitura(String chave, String rotulo) {
        return new Campo(chave, rotulo, Tipo.LEITURA, 0, null, null, null);
    }

    /** O mesmo campo, bloqueado com um valor fixo quando a condição sobre os valores for verdadeira. */
    public Campo bloqueadoQuando(Predicate<Map<String, String>> condicao, String valor) {
        return bloqueadoQuando(condicao, valores -> valor);
    }

    /** O mesmo campo, bloqueado com um valor calculado dos outros (ex.: elevação do destino). */
    public Campo bloqueadoQuando(Predicate<Map<String, String>> condicao, Function<Map<String, String>, String> valor) {
        return new Campo(chave, rotulo, tipo, max, opcoesDe, condicao, valor);
    }

    public List<String> opcoes(Map<String, String> valores) {
        return opcoesDe.apply(valores);
    }

    public boolean bloqueado(Map<String, String> valores) {
        return bloqueadoSe.test(valores);
    }

    public String valorBloqueado(Map<String, String> valores) {
        return valorBloqueadoDe.apply(valores);
    }
}
