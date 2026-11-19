package br.a320sim.cockpit.input;

import br.a320sim.core.bus.DataBus;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerAdapter;
import com.badlogic.gdx.controllers.Controllers;

/**
 * Escolhe sozinho entre teclado e controle: plugou um controle, ele assume no modo simplificado;
 * desconectou, o teclado volta no modo completo. A manete, os flaps e o trem passam de um para o outro
 * sem pulo, então dá para trocar até em voo.
 */
public final class AutoInput implements InputDevice {

    private final KeyboardInput keyboard = new KeyboardInput();
    private final FlightAssist assist = new FlightAssist();
    private ControlInputs lastOutput;
    private final ControllerAdapter listener = new ControllerAdapter() {
        @Override
        public void connected(Controller controller) {
            pendingConnect = controller;
        }

        @Override
        public void disconnected(Controller controller) {
            if (gamepad != null && gamepad.controller() == controller) {
                pendingDisconnect = true;
            }
        }
    };
    private volatile Controller pendingConnect;
    private volatile boolean pendingDisconnect;
    private GamepadInput gamepad;
    private InputDevice active = keyboard;
    private String notice;

    public AutoInput() {
        Controller current = firstController();
        if (current != null) {
            gamepad = new GamepadInput(current);
            active = gamepad;
            notice = "CONTROLE: " + current.getName() + "  -  MODO SIMPLIFICADO";
        }
        try {
            Controllers.addListener(listener);
        } catch (RuntimeException | LinkageError e) {
            System.err.println("[CONTROLE] sem suporte a controles: " + e.getMessage());
        }
    }

    @Override
    public void poll(float delta) {
        Controller connected = pendingConnect;
        if (connected != null && gamepad == null) {
            switchTo(new GamepadInput(connected));
            notice = "CONTROLE CONECTADO: " + connected.getName() + "  -  MODO SIMPLIFICADO";
        }
        pendingConnect = null;
        if (pendingDisconnect) {
            pendingDisconnect = false;
            gamepad = null;
            switchTo(keyboard);
            notice = "CONTROLE DESCONECTADO  -  TECLADO NO MODO COMPLETO";
        }
        active.poll(delta);
    }

    private void switchTo(InputDevice next) {
        ControlInputs current = lastOutput != null ? lastOutput : active.read();
        next.takeOver(current);
        if (next instanceof GamepadInput) {
            assist.sync(current);
        }
        active = next;
        if (next instanceof GamepadInput pad) {
            gamepad = pad;
        }
    }

    @Override
    public ControlInputs read() {
        return active.read();
    }

    /** O que vai para o avião: com o controle, o assistente decide flaps e trem. */
    public ControlInputs read(DataBus bus) {
        ControlInputs raw = active.read();
        lastOutput = simplified() ? assist.apply(raw, bus) : raw;
        return lastOutput;
    }

    @Override
    public boolean viewToggled() {
        return active.viewToggled();
    }

    /** Controle no comando: só acelerar e direção, com o {@link FlightAssist} cuidando do resto. */
    public boolean simplified() {
        return active == gamepad && gamepad != null;
    }

    /** Aviso de conexão/desconexão para mostrar na tela (lido uma vez). */
    public String takeNotice() {
        String n = notice;
        notice = null;
        return n;
    }

    public void close() {
        try {
            Controllers.removeListener(listener);
        } catch (RuntimeException | LinkageError ignored) {
            // sem suporte a controles: nada para remover
        }
    }

    private static Controller firstController() {
        try {
            Controller current = Controllers.getCurrent();
            if (current != null) {
                return current;
            }
            return Controllers.getControllers().size > 0 ? Controllers.getControllers().first() : null;
        } catch (RuntimeException | LinkageError e) {
            System.err.println("[CONTROLE] sem suporte a controles: " + e.getMessage());
            return null;
        }
    }
}
