package br.a320sim.cockpit.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;

/**
 * Pilotagem pelo teclado. O sidestick cresce suavemente enquanto a tecla está apertada
 * e volta sozinho ao centro quando ela é solta, como o sidestick de verdade.
 * A manete fica onde foi deixada.
 */
public final class KeyboardInput implements InputDevice {

    private static final float STICK_RATE = 2.5f;
    private static final float CENTER_RATE = 4f;
    private static final float THRUST_RATE = 0.35f;
    /** IDLE, CL, FLX, TOGA. */
    private static final float[] DETENTS = {0f, 0.85f, 0.95f, 1f};
    private static final int MAX_FLAPS = 4;

    private float pitch;
    private float roll;
    private float yaw;
    private float thrust;
    private int flaps;
    private boolean gearDown = true;
    private boolean apDisconnect;

    @Override
    public void poll(float delta) {
        pitch = axis(pitch, held(Keys.S, Keys.DOWN), held(Keys.W, Keys.UP), delta);
        roll = axis(roll, held(Keys.D, Keys.RIGHT), held(Keys.A, Keys.LEFT), delta);
        yaw = axis(yaw, held(Keys.E), held(Keys.Q), delta);

        if (held(Keys.SHIFT_LEFT, Keys.SHIFT_RIGHT)) {
            thrust = Math.min(1, thrust + THRUST_RATE * delta);
        }
        if (held(Keys.CONTROL_LEFT, Keys.CONTROL_RIGHT)) {
            thrust = Math.max(0, thrust - THRUST_RATE * delta);
        }
        for (int i = 0; i < DETENTS.length; i++) {
            if (Gdx.input.isKeyJustPressed(Keys.NUM_1 + i)) {
                thrust = DETENTS[i];
            }
        }
        if (Gdx.input.isKeyJustPressed(Keys.F)) {
            flaps = Math.min(MAX_FLAPS, flaps + 1);
        }
        if (Gdx.input.isKeyJustPressed(Keys.V)) {
            flaps = Math.max(0, flaps - 1);
        }
        if (Gdx.input.isKeyJustPressed(Keys.G)) {
            gearDown = !gearDown;
        }
        apDisconnect = Gdx.input.isKeyJustPressed(Keys.SPACE);
    }

    @Override
    public ControlInputs read() {
        return new ControlInputs(pitch, roll, yaw, thrust, flaps, gearDown, held(Keys.B) ? 1 : 0, apDisconnect);
    }

    private static boolean held(int... keys) {
        for (int key : keys) {
            if (Gdx.input.isKeyPressed(key)) {
                return true;
            }
        }
        return false;
    }

    private static float axis(float value, boolean positive, boolean negative, float delta) {
        float target = positive == negative ? 0 : positive ? 1 : -1;
        float rate = target == 0 ? CENTER_RATE : STICK_RATE;
        return value < target
                ? Math.min(target, value + rate * delta)
                : Math.max(target, value - rate * delta);
    }
}
