package br.a320sim.cockpit.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.utils.Disposable;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Um único canal de som tocando em fila, como um chip de som antigo.
 * Se o computador não tiver saída de áudio, o simulador segue em silêncio.
 */
public final class SoundFx implements Disposable {

    private static final int MAX_QUEUED = 6;

    private final BlockingQueue<float[]> queue = new LinkedBlockingQueue<>();
    private final AudioDevice device;
    private final Thread worker;
    private volatile boolean running = true;

    public SoundFx() {
        AudioDevice opened = null;
        try {
            opened = Gdx.audio.newAudioDevice(Synth.RATE, true);
        } catch (RuntimeException e) {
            Gdx.app.error("A320SIM", "Sem dispositivo de áudio, seguindo em silêncio", e);
        }
        device = opened;
        worker = new Thread(this::loop, "sound-fx");
        worker.setDaemon(true);
        if (device != null) {
            worker.start();
        }
    }

    public void play(Sfx sfx) {
        if (device != null && queue.size() < MAX_QUEUED) {
            queue.offer(sfx.samples());
        }
    }

    private void loop() {
        while (running) {
            try {
                float[] samples = queue.poll(100, TimeUnit.MILLISECONDS);
                if (samples != null) {
                    device.writeSamples(samples, 0, samples.length);
                }
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    @Override
    public void dispose() {
        running = false;
        worker.interrupt();
        if (device != null) {
            try {
                worker.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            device.dispose();
        }
    }
}
