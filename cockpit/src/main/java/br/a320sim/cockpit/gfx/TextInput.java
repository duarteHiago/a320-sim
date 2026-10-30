package br.a320sim.cockpit.gfx;

import com.badlogic.gdx.InputAdapter;

/** Campo de texto simples: recebe as letras digitadas (via InputProcessor do LibGDX). */
public final class TextInput extends InputAdapter {

    private final StringBuilder text;
    private final int maxLength;
    private boolean active;

    public TextInput(String initial, int maxLength) {
        this.text = new StringBuilder(initial);
        this.maxLength = maxLength;
    }

    @Override
    public boolean keyTyped(char character) {
        if (!active) {
            return false;
        }
        if (character == '\b') {
            if (!text.isEmpty()) {
                text.deleteCharAt(text.length() - 1);
            }
        } else if (character >= 32 && character < 127 && text.length() < maxLength) {
            text.append(character);
        }
        return true;
    }

    public String text() {
        return text.toString();
    }

    public void clear() {
        text.setLength(0);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
