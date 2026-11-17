package br.a320sim.cockpit.input;

import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerMapping;

/**
 * Controle de videogame no modo simplificado: só acelerar e dar direção.
 * <ul>
 *   <li>Analógico esquerdo: direção. Para os lados inclina as asas (e vira a roda do nariz no chão);
 *       puxar para trás sobe o nariz, empurrar desce, como um manche.</li>
 *   <li>Gatilho direito: acelera. Gatilho esquerdo: reduz e, com a manete toda atrás, freia.</li>
 *   <li>A: desliga o piloto automático. Y: alterna PAINEL/JANELA.</li>
 * </ul>
 * Flaps e trem ficam por conta do {@link FlightAssist}.
 */
public final class GamepadInput implements InputDevice {

    private static final float DEADZONE = 0.12f;
    private static final float THRUST_RATE = 0.45f;
    /** Eixos dos gatilhos no SDL (0 a 1); controles genéricos às vezes os mandam como botões. */
    private static final int AXIS_TRIGGER_LEFT = 4;
    private static final int AXIS_TRIGGER_RIGHT = 5;

    private final Controller controller;
    private final ControllerMapping map;
    private float pitch;
    private float roll;
    private float thrust;
    private float brakes;
    private int flaps = 1;
    private boolean gearDown = true;
    private boolean apDisconnect;
    private boolean viewToggle;
    private boolean aWasDown;
    private boolean yWasDown;

    public GamepadInput(Controller controller) {
        this.controller = controller;
        this.map = controller.getMapping();
    }

    public Controller controller() {
        return controller;
    }

    @Override
    public void poll(float delta) {
        roll = deadzone(axis(map.axisLeftX));
        pitch = deadzone(axis(map.axisLeftY)); // no SDL o eixo Y é positivo para baixo: puxar = cabrar

        float accelerate = trigger(AXIS_TRIGGER_RIGHT, map.buttonR2);
        float reduce = trigger(AXIS_TRIGGER_LEFT, map.buttonL2);
        thrust = Math.max(0, Math.min(1, thrust + (accelerate - reduce) * THRUST_RATE * delta));
        brakes = thrust <= 0.001f ? reduce : 0;

        boolean aDown = button(map.buttonA);
        boolean yDown = button(map.buttonY);
        apDisconnect = aDown && !aWasDown;
        viewToggle = yDown && !yWasDown;
        aWasDown = aDown;
        yWasDown = yDown;
    }

    @Override
    public ControlInputs read() {
        // No modo simplificado o leme acompanha a direção: curva coordenada no ar, roda do nariz no chão
        return new ControlInputs(pitch, roll, roll, thrust, flaps, gearDown, brakes, apDisconnect);
    }

    @Override
    public void takeOver(ControlInputs last) {
        thrust = (float) last.thrustLever();
        flaps = last.flaps();
        gearDown = last.gearDown();
    }

    @Override
    public boolean viewToggled() {
        return viewToggle;
    }

    private float axis(int index) {
        return index == ControllerMapping.UNDEFINED ? 0 : controller.getAxis(index);
    }

    private boolean button(int index) {
        return index != ControllerMapping.UNDEFINED && controller.getButton(index);
    }

    private float trigger(int axisIndex, int buttonIndex) {
        float value = axisIndex < controller.getAxisCount() ? Math.max(0, controller.getAxis(axisIndex)) : 0;
        return Math.max(value, button(buttonIndex) ? 1 : 0);
    }

    private static float deadzone(float value) {
        float magnitude = Math.abs(value);
        if (magnitude < DEADZONE) {
            return 0;
        }
        return Math.signum(value) * Math.min(1, (magnitude - DEADZONE) / (1 - DEADZONE));
    }
}
