package br.a320sim.cockpit.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerMapping;
import com.badlogic.gdx.controllers.Controllers;

/**
 * Navegação dos menus pelo teclado ou pelo controle, lida uma vez por quadro.
 * <ul>
 *   <li>Mover: setas / WASD, ou direcional / analógico esquerdo (segurando, repete)</li>
 *   <li>Confirmar: ENTER, ou A / Start</li>
 *   <li>Voltar: ESC, ou B</li>
 * </ul>
 * Na cabine, Start pausa e Back sai (os botões A/B/Y ficam para pilotar).
 */
public final class MenuInput {

    private static final float STICK = 0.6f;
    private static final float REPEAT_DELAY = 0.38f;
    private static final float REPEAT_RATE = 0.12f;

    private enum Dir { NONE, UP, DOWN, LEFT, RIGHT }

    private static boolean supported = true;
    private static boolean[] previous = new boolean[0];
    private static boolean[] current = new boolean[0];
    private static Controller controller;
    private static Dir held = Dir.NONE;
    private static float heldTime;
    private static Dir fired = Dir.NONE;

    private MenuInput() {
    }

    /** Chamado pela tela uma vez por quadro, antes de ela ler os comandos. */
    public static void update(float delta) {
        controller = currentController();
        int count = controller == null ? 0 : Math.min(64, controller.getMaxButtonIndex() + 1);
        if (current.length != count) {
            current = new boolean[count];
            previous = new boolean[count];
        }
        System.arraycopy(current, 0, previous, 0, count);
        for (int i = 0; i < count; i++) {
            current[i] = controller.getButton(i);
        }

        Dir dir = padDirection();
        fired = Dir.NONE;
        if (dir != held) {
            held = dir;
            heldTime = 0;
            fired = dir;
        } else if (dir != Dir.NONE) {
            heldTime += delta;
            if (heldTime >= REPEAT_DELAY) {
                heldTime -= REPEAT_RATE;
                fired = dir;
            }
        }
    }

    public static boolean up() {
        return key(Keys.UP, Keys.W) || fired == Dir.UP;
    }

    public static boolean down() {
        return key(Keys.DOWN, Keys.S) || fired == Dir.DOWN;
    }

    public static boolean left() {
        return key(Keys.LEFT, Keys.A) || fired == Dir.LEFT;
    }

    public static boolean right() {
        return key(Keys.RIGHT, Keys.D) || fired == Dir.RIGHT;
    }

    public static boolean confirm() {
        return key(Keys.ENTER) || pressed(map().buttonA) || pressed(map().buttonStart);
    }

    public static boolean back() {
        return key(Keys.ESCAPE) || pressed(map().buttonB);
    }

    /** Qualquer tecla ou botão (pular animações). */
    public static boolean any() {
        if (Gdx.input.isKeyJustPressed(Keys.ANY_KEY)) {
            return true;
        }
        for (int i = 0; i < current.length; i++) {
            if (current[i] && !previous[i]) {
                return true;
            }
        }
        return false;
    }

    /** Cabine: Start pausa. */
    public static boolean pause() {
        return pressed(map().buttonStart);
    }

    /** Cabine: Back sai para o rack (ou desconecta, no modo ALUNO). */
    public static boolean exit() {
        return pressed(map().buttonBack);
    }

    public static boolean hasController() {
        return controller != null;
    }

    private static boolean key(int... keys) {
        for (int k : keys) {
            if (Gdx.input.isKeyJustPressed(k)) {
                return true;
            }
        }
        return false;
    }

    private static boolean pressed(int button) {
        return button >= 0 && button < current.length && current[button] && !previous[button];
    }

    private static ControllerMapping map() {
        return controller != null ? controller.getMapping() : NO_MAPPING;
    }

    private static Dir padDirection() {
        if (controller == null) {
            return Dir.NONE;
        }
        ControllerMapping m = controller.getMapping();
        float x = m.axisLeftX >= 0 ? controller.getAxis(m.axisLeftX) : 0;
        float y = m.axisLeftY >= 0 ? controller.getAxis(m.axisLeftY) : 0;
        if (button(m.buttonDpadUp) || y < -STICK) {
            return Dir.UP;
        }
        if (button(m.buttonDpadDown) || y > STICK) {
            return Dir.DOWN;
        }
        if (button(m.buttonDpadLeft) || x < -STICK) {
            return Dir.LEFT;
        }
        if (button(m.buttonDpadRight) || x > STICK) {
            return Dir.RIGHT;
        }
        return Dir.NONE;
    }

    private static boolean button(int index) {
        return index >= 0 && index < current.length && current[index];
    }

    private static Controller currentController() {
        if (!supported) {
            return null;
        }
        try {
            Controller c = Controllers.getCurrent();
            if (c == null && Controllers.getControllers().size > 0) {
                c = Controllers.getControllers().first();
            }
            return c;
        } catch (RuntimeException | LinkageError e) {
            supported = false;
            return null;
        }
    }

    /** Mapeamento "vazio" para quando não há controle: todo botão é UNDEFINED. */
    private static final ControllerMapping NO_MAPPING = new ControllerMapping(
            ControllerMapping.UNDEFINED, ControllerMapping.UNDEFINED, ControllerMapping.UNDEFINED,
            ControllerMapping.UNDEFINED, ControllerMapping.UNDEFINED, ControllerMapping.UNDEFINED,
            ControllerMapping.UNDEFINED, ControllerMapping.UNDEFINED, ControllerMapping.UNDEFINED,
            ControllerMapping.UNDEFINED, ControllerMapping.UNDEFINED, ControllerMapping.UNDEFINED,
            ControllerMapping.UNDEFINED, ControllerMapping.UNDEFINED, ControllerMapping.UNDEFINED,
            ControllerMapping.UNDEFINED, ControllerMapping.UNDEFINED, ControllerMapping.UNDEFINED,
            ControllerMapping.UNDEFINED, ControllerMapping.UNDEFINED) {
    };
}
