package br.a320sim.cockpit.audio;

import java.util.Random;

/** Sintetizador no espírito dos chips de som dos consoles: onda quadrada, triângulo, ruído e varreduras. */
final class Synth {

    static final int RATE = 22050;
    private static final float VOLUME = 0.22f;
    private static final Random NOISE = new Random(320);

    private Synth() {
    }

    static float[] tone(double freq, double seconds, boolean decay) {
        int n = (int) (seconds * RATE);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            double phase = (i * freq / RATE) % 1.0;
            float envelope = decay ? 1f - (float) i / n : 1f;
            out[i] = (phase < 0.5 ? VOLUME : -VOLUME) * envelope * attack(i);
        }
        return out;
    }

    /** Onda triangular: mais macia, boa para baixos e melodias. */
    static float[] triangle(double freq, double seconds, boolean decay) {
        int n = (int) (seconds * RATE);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            double phase = (i * freq / RATE) % 1.0;
            double wave = phase < 0.5 ? phase * 4 - 1 : 3 - phase * 4;
            float envelope = decay ? 1f - (float) i / n : 1f;
            out[i] = (float) wave * VOLUME * 1.3f * envelope * attack(i);
        }
        return out;
    }

    /** Varredura de frequência (onda quadrada), ex.: "piu" de subida ou descida. */
    static float[] sweep(double fromHz, double toHz, double seconds) {
        int n = (int) (seconds * RATE);
        float[] out = new float[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            phase = (phase + (fromHz + (toHz - fromHz) * t) / RATE) % 1.0;
            out[i] = (phase < 0.5 ? VOLUME : -VOLUME) * (float) (1 - t * 0.6) * attack(i);
        }
        return out;
    }

    /** Ruído com decaimento; {@code smooth} (0..1) abafa os agudos, como um baque. */
    static float[] noise(double seconds, double smooth) {
        int n = (int) (seconds * RATE);
        float[] out = new float[n];
        float last = 0;
        for (int i = 0; i < n; i++) {
            float white = (NOISE.nextFloat() * 2 - 1) * VOLUME * 1.4f;
            last = (float) (last * smooth + white * (1 - smooth));
            out[i] = last * (1f - (float) i / n);
        }
        return out;
    }

    static float[] silence(double seconds) {
        return new float[(int) (seconds * RATE)];
    }

    static float[] concat(float[]... parts) {
        int total = 0;
        for (float[] p : parts) {
            total += p.length;
        }
        float[] out = new float[total];
        int pos = 0;
        for (float[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }

    /** Toca as partes ao mesmo tempo (acorde, ou melodia + baixo). */
    static float[] layer(float[]... parts) {
        int total = 0;
        for (float[] p : parts) {
            total = Math.max(total, p.length);
        }
        float[] out = new float[total];
        for (float[] p : parts) {
            for (int i = 0; i < p.length; i++) {
                out[i] += p[i] * 0.7f;
            }
        }
        return out;
    }

    static float[] arpeggio(double noteSeconds, double... freqs) {
        float[][] notes = new float[freqs.length][];
        for (int i = 0; i < freqs.length; i++) {
            notes[i] = tone(freqs[i], noteSeconds, false);
        }
        return concat(notes);
    }

    /** Melodia: pares (frequência, duração em segundos); frequência 0 é pausa. */
    static float[] melody(boolean triangle, double... notes) {
        float[][] parts = new float[notes.length / 2][];
        for (int i = 0; i < parts.length; i++) {
            double freq = notes[i * 2];
            double seconds = notes[i * 2 + 1];
            parts[i] = freq <= 0 ? silence(seconds)
                    : triangle ? triangle(freq, seconds, true) : tone(freq, seconds * 0.9, true);
            if (!triangle && freq > 0) {
                parts[i] = concat(parts[i], silence(seconds * 0.1));
            }
        }
        return concat(parts);
    }

    /** Rampa de 3 ms no início de cada som, para não estalar. */
    private static float attack(int i) {
        return Math.min(1f, i / (RATE * 0.003f));
    }
}
