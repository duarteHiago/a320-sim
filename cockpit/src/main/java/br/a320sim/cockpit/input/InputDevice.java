package br.a320sim.cockpit.input;

/**
 * Qualquer forma de pilotar: teclado e mouse, ou um controle de videogame.
 * O resto do simulador só enxerga {@link ControlInputs}.
 */
public interface InputDevice {

    void poll(float delta);

    ControlInputs read();

    /** Assume o avião no estado em que o outro dispositivo deixou (manete, flaps, trem). */
    default void takeOver(ControlInputs last) {
    }

    /** O piloto pediu para alternar PAINEL/JANELA neste quadro. */
    default boolean viewToggled() {
        return false;
    }
}
