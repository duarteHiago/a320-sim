package br.a320sim.cockpit.screen;

import br.a320sim.cockpit.SimApp;
import br.a320sim.cockpit.gfx.Draw;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;

/**
 * Base de todas as telas (Template Method): cada uma só implementa update() e paint().
 * Tudo é desenhado numa "tela virtual" de 640x360 e ampliado em múltiplos inteiros,
 * o que dá o visual de pixels grandes de console antigo.
 */
public abstract class RetroScreen extends ScreenAdapter {

    public static final int WIDTH = 640;
    public static final int HEIGHT = 360;

    protected final SimApp app;
    private final Draw draw = new Draw();
    private final FrameBuffer frameBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, WIDTH, HEIGHT, false);
    private final SpriteBatch screenBatch = new SpriteBatch();
    private final ShapeRenderer overlay = new ShapeRenderer();
    private final Matrix4 virtualProjection = new Matrix4().setToOrtho2D(0, 0, WIDTH, HEIGHT);
    private final Vector2 mouse = new Vector2();
    private float viewX;
    private float viewY;
    private float scale = 1;
    private boolean screenshotRequested;
    private float age;
    /** Modo de verificação: -Dsim.shot=<segundos> [-Dsim.shotScreen=RackScreen] tira um screenshot e fecha. */
    private float screenshotAt = getClass().getSimpleName().equals(System.getProperty("sim.shotScreen", "CockpitScreen"))
            ? Float.parseFloat(System.getProperty("sim.shot", "-1")) : -1;

    protected RetroScreen(SimApp app) {
        this.app = app;
        frameBuffer.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
    }

    protected abstract void update(float delta);

    protected abstract void paint(Draw d);

    /** Telas com tema CRT ligam as linhas de varredura. */
    protected boolean scanlines() {
        return false;
    }

    /** Posição do mouse em coordenadas da tela virtual. */
    protected Vector2 mouse() {
        return mouse.set((Gdx.input.getX() - viewX) / scale,
                (Gdx.graphics.getHeight() - Gdx.input.getY() - viewY) / scale);
    }

    protected void requestScreenshot() {
        screenshotRequested = true;
    }

    @Override
    public final void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.F12)) {
            requestScreenshot();
        }
        age += delta;
        if (screenshotAt == 0) {
            Gdx.app.exit();
        } else if (screenshotAt > 0 && age >= screenshotAt) {
            screenshotAt = 0;
            requestScreenshot();
        }
        update(Math.min(delta, 0.1f));
        if (app.getScreen() != this) {
            return;
        }

        frameBuffer.begin();
        ScreenUtils.clear(Color.BLACK);
        draw.begin(virtualProjection);
        paint(draw);
        draw.end();
        frameBuffer.end();

        present();
        if (screenshotRequested) {
            screenshotRequested = false;
            saveScreenshot();
        }
    }

    private void present() {
        int w = Gdx.graphics.getWidth();
        int h = Gdx.graphics.getHeight();
        scale = Math.min(w / (float) WIDTH, h / (float) HEIGHT);
        if (scale >= 1) {
            scale = (float) Math.floor(scale);
        }
        viewX = (w - WIDTH * scale) / 2;
        viewY = (h - HEIGHT * scale) / 2;

        ScreenUtils.clear(Color.BLACK);
        screenBatch.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
        screenBatch.begin();
        screenBatch.draw(frameBuffer.getColorBufferTexture(), viewX, viewY, WIDTH * scale, HEIGHT * scale,
                0, 0, 1, 1);
        screenBatch.end();

        if (scanlines() && scale >= 2) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            overlay.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
            overlay.begin(ShapeRenderer.ShapeType.Filled);
            overlay.setColor(0, 0, 0, 0.28f);
            for (int row = 0; row < HEIGHT; row++) {
                overlay.rect(viewX, viewY + row * scale, WIDTH * scale, Math.max(1, scale / 3));
            }
            overlay.end();
        }
    }

    private void saveScreenshot() {
        int w = Gdx.graphics.getBackBufferWidth();
        int h = Gdx.graphics.getBackBufferHeight();
        byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
        for (int i = 3; i < pixels.length; i += 4) {
            pixels[i] = (byte) 255;
        }
        Pixmap pixmap = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        BufferUtils.copy(pixels, 0, pixmap.getPixels(), pixels.length);
        String file = "screenshots/a320sim-" + System.currentTimeMillis() + ".png";
        PixmapIO.writePNG(Gdx.files.local(file), pixmap);
        pixmap.dispose();
        Gdx.app.log("A320SIM", "Screenshot salvo em " + file);
    }

    @Override
    public void hide() {
        dispose();
    }

    @Override
    public void dispose() {
        draw.dispose();
        frameBuffer.dispose();
        screenBatch.dispose();
        overlay.dispose();
    }
}
