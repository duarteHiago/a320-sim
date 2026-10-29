package br.a320sim.cockpit.input;

/**
 * Estado dos comandos do piloto num instante.
 *
 * @param pitch        sidestick longitudinal, -1 (empurra/pica) a +1 (puxa/cabra)
 * @param roll         sidestick lateral, -1 (esquerda) a +1 (direita)
 * @param yaw          pedais, -1 a +1
 * @param thrustLever  manete, 0 (IDLE) a 1 (TOGA)
 * @param flaps        alavanca de flaps, 0 a 4 (FULL)
 * @param gearDown     alavanca do trem
 * @param brakes       freios, 0 a 1
 * @param apDisconnect botão vermelho do sidestick apertado neste instante
 */
public record ControlInputs(
        double pitch,
        double roll,
        double yaw,
        double thrustLever,
        int flaps,
        boolean gearDown,
        double brakes,
        boolean apDisconnect) {
}
