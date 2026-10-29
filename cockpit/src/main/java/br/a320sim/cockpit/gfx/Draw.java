package br.a320sim.cockpit.gfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;

import java.text.Normalizer;

/**
 * Pincel único para formas e textos. Alterna sozinho entre ShapeRenderer e SpriteBatch,
 * assim quem desenha não precisa se preocupar com begin/end.
 */
public final class Draw implements Disposable {

    public enum Align { LEFT, CENTER, RIGHT }

    private enum Mode { NONE, FILLED, LINE, TEXT }

    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout layout = new GlyphLayout();
    private Mode mode = Mode.NONE;

    public Draw() {
        font.getRegion().getTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        font.setUseIntegerPositions(true);
    }

    public void begin(Matrix4 projection) {
        shapes.setProjectionMatrix(projection);
        batch.setProjectionMatrix(projection);
    }

    public void end() {
        switchTo(Mode.NONE);
    }

    public ShapeRenderer filled() {
        switchTo(Mode.FILLED);
        return shapes;
    }

    public ShapeRenderer lines() {
        switchTo(Mode.LINE);
        return shapes;
    }

    public void rect(float x, float y, float w, float h, Color color) {
        filled().setColor(color);
        shapes.rect(x, y, w, h);
    }

    public void frame(float x, float y, float w, float h, Color color) {
        lines().setColor(color);
        shapes.rect(x, y, w, h);
    }

    public void line(float x1, float y1, float x2, float y2, Color color) {
        lines().setColor(color);
        shapes.line(x1, y1, x2, y2);
    }

    public void triangle(float x1, float y1, float x2, float y2, float x3, float y3, Color color) {
        filled().setColor(color);
        shapes.triangle(x1, y1, x2, y2, x3, y3);
    }

    public void circle(float x, float y, float radius, Color color) {
        lines().setColor(color);
        shapes.circle(x, y, radius, 48);
    }

    /** Arco em graus (0 = direita, sentido anti-horário), sem as linhas até o centro. */
    public void arc(float cx, float cy, float radius, float fromDeg, float toDeg, Color color) {
        lines().setColor(color);
        int segments = Math.max(4, (int) Math.abs(toDeg - fromDeg) / 6);
        float step = (toDeg - fromDeg) / segments;
        for (int i = 0; i < segments; i++) {
            double a1 = Math.toRadians(fromDeg + step * i);
            double a2 = Math.toRadians(fromDeg + step * (i + 1));
            shapes.line(cx + radius * (float) Math.cos(a1), cy + radius * (float) Math.sin(a1),
                    cx + radius * (float) Math.cos(a2), cy + radius * (float) Math.sin(a2));
        }
    }

    public void image(Texture texture, float x, float y, float w, float h) {
        switchTo(Mode.TEXT);
        batch.setColor(Color.WHITE);
        batch.draw(texture, x, y, w, h);
    }

    /** Escreve texto centralizado verticalmente em {@code y}. */
    public void text(String text, float x, float y, Color color, Align align) {
        text(text, x, y, color, align, 1);
    }

    public void text(String text, float x, float y, Color color, Align align, int scale) {
        switchTo(Mode.TEXT);
        font.getData().setScale(scale);
        font.setColor(color);
        layout.setText(font, plain(text));
        float dx = switch (align) {
            case LEFT -> 0;
            case CENTER -> -layout.width / 2;
            case RIGHT -> -layout.width;
        };
        font.draw(batch, layout, Math.round(x + dx), Math.round(y + font.getCapHeight() / 2));
        font.getData().setScale(1);
    }

    /** Limita o desenho a um retângulo (ex.: a fita de velocidade). */
    public void clip(float x, float y, float w, float h) {
        switchTo(Mode.NONE);
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor((int) x, (int) y, (int) w, (int) h);
    }

    public void unclip() {
        switchTo(Mode.NONE);
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
    }

    private void switchTo(Mode next) {
        if (mode == next) {
            return;
        }
        switch (mode) {
            case FILLED, LINE -> shapes.end();
            case TEXT -> batch.end();
            default -> {
            }
        }
        switch (next) {
            case FILLED -> {
                Gdx.gl.glEnable(GL20.GL_BLEND);
                Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
                shapes.begin(ShapeRenderer.ShapeType.Filled);
            }
            case LINE -> {
                Gdx.gl.glEnable(GL20.GL_BLEND);
                shapes.begin(ShapeRenderer.ShapeType.Line);
            }
            case TEXT -> batch.begin();
            default -> {
            }
        }
        mode = next;
    }

    /** A fonte bitmap padrão não tem acentos: "Propulsão" vira "Propulsao". */
    private static String plain(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}
