package br.a320sim.cockpit.input;

/**
 * Qualquer forma de pilotar: teclado e mouse hoje, joystick amanhã.
 * O resto do simulador só enxerga {@link ControlInputs}.
 */
public interface InputDevice {

    void poll(float delta);

    ControlInputs read();
}
