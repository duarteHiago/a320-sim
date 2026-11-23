package br.a320sim.cockpit.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.utils.Disposable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Mixer de som: vários efeitos ao mesmo tempo, mais três "canais contínuos" sintetizados na hora:
 * motor (ronco do fan + assobio da turbina, conforme o N1), vento (cresce com a velocidade)
 * e trepidação das rodas na pista. Se o computador não tiver saída de áudio, segue em silêncio.
 */
public final class SoundFx implements Disposable {

    private static final int BLOCK = 512;
    private static final int MAX_VOICES = 8;
    private static final float MASTER = 0.9f;

    private final ConcurrentLinkedQueue<float[]> incoming = new ConcurrentLinkedQueue<>();
    private final AudioDevice device;
    private final Thread worker;
    private volatile boolean running = true;

    // Alvos dos canais contínuos (escritos pela tela, lidos pelo mixer)
    private volatile float engineTarget;
    private volatile float windTarget;
    private volatile float rumbleTarget;

    public SoundFx() {
        AudioDevice opened = null;
        try {
            opened = Gdx.audio.newAudioDevice(Synth.RATE, true);
        } catch (RuntimeException e) {
            Gdx.app.error("AVION", "Sem dispositivo de áudio, seguindo em silêncio", e);
        }
        device = opened;
        worker = new Thread(this::loop, "sound-mixer");
        worker.setDaemon(true);
        if (device != null) {
            worker.start();
        }
    }

    public void play(Sfx sfx) {
        if (device != null && incoming.size() < MAX_VOICES) {
            incoming.offer(sfx.samples());
        }
    }

    /**
     * Ambiente da cabine.
     *
     * @param engine N1 médio, 0 a 1 (0 = motores parados)
     * @param wind   0 a 1, conforme a velocidade
     * @param rumble 0 a 1, rodas rolando na pista
     */
    public void ambience(double engine, double wind, double rumble) {
        engineTarget = (float) clamp(engine);
        windTarget = (float) clamp(wind);
        rumbleTarget = (float) clamp(rumble);
    }

    public void silenceAmbience() {
        ambience(0, 0, 0);
    }

    private void loop() {
        List<int[]> positions = new ArrayList<>();
        List<float[]> voices = new ArrayList<>();
        float[] block = new float[BLOCK];
        Random random = new Random(1);
        float engine = 0, wind = 0, rumble = 0;
        double fanPhase = 0, whinePhase = 0;
        float windLow = 0, rumbleLow = 0, hiss = 0;

        while (running) {
            float[] next;
            while ((next = incoming.poll()) != null) {
                if (voices.size() >= MAX_VOICES) {
                    voices.remove(0);
                    positions.remove(0);
                }
                voices.add(next);
                positions.add(new int[]{0});
            }
            boolean ambient = engineTarget > 0 || windTarget > 0 || rumbleTarget > 0
                    || engine > 0.001f || wind > 0.001f || rumble > 0.001f;
            if (voices.isEmpty() && !ambient) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    return;
                }
                continue;
            }

            for (int i = 0; i < BLOCK; i++) {
                // Os canais contínuos andam devagar até o alvo (sem degraus no som)
                engine += (engineTarget - engine) * 0.0004f;
                wind += (windTarget - wind) * 0.0004f;
                rumble += (rumbleTarget - rumble) * 0.002f;
                float white = random.nextFloat() * 2 - 1;
                float sample = 0;
                if (engine > 0.001f) {
                    // Ronco grave do fan (triângulo) + assobio agudo da turbina (seno) + chiado
                    fanPhase = (fanPhase + (35 + 95 * engine) / Synth.RATE) % 1.0;
                    double fan = fanPhase < 0.5 ? fanPhase * 4 - 1 : 3 - fanPhase * 4;
                    whinePhase = (whinePhase + (700 + 2600 * engine) / Synth.RATE) % 1.0;
                    hiss = hiss * 0.7f + white * 0.3f;
                    sample += (float) (fan * 0.07 * (0.5 + engine)
                            + Math.sin(whinePhase * 2 * Math.PI) * 0.018 * engine
                            + hiss * 0.05 * engine);
                }
                if (wind > 0.001f) {
                    windLow = windLow * 0.985f + white * 0.015f;
                    sample += windLow * 2.2f * wind;
                }
                if (rumble > 0.001f) {
                    rumbleLow = rumbleLow * 0.93f + white * 0.07f;
                    sample += rumbleLow * 0.9f * rumble * (random.nextFloat() < 0.003f ? 3 : 1);
                }
                for (int v = 0; v < voices.size(); v++) {
                    int[] pos = positions.get(v);
                    float[] samples = voices.get(v);
                    if (pos[0] < samples.length) {
                        sample += samples[pos[0]++];
                    }
                }
                // Limitador suave: vários sons juntos não estouram
                block[i] = (float) Math.tanh(sample * MASTER * 1.5) / 1.1f;
            }
            Iterator<float[]> vi = voices.iterator();
            Iterator<int[]> pi = positions.iterator();
            while (vi.hasNext()) {
                float[] samples = vi.next();
                int[] pos = pi.next();
                if (pos[0] >= samples.length) {
                    vi.remove();
                    pi.remove();
                }
            }
            device.writeSamples(block, 0, BLOCK);
        }
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
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
