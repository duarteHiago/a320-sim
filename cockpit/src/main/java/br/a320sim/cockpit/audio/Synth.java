package br.a320sim.cockpit.audio;

/** Sintetizador de onda quadrada, no espírito dos chips de som dos consoles 8-bit. */
final class Synth {

    static final int RATE = 22050;
    private static final float VOLUME = 0.22f;

    private Synth() {
    }

    static float[] tone(double freq, double seconds, boolean decay) {
        int n = (int) (seconds * RATE);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            double phase = (i * freq / RATE) % 1.0;
            float envelope = decay ? 1f - (float) i / n : 1f;
            out[i] = (phase < 0.5 ? VOLUME : -VOLUME) * envelope;
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

    static float[] arpeggio(double noteSeconds, double... freqs) {
        float[][] notes = new float[freqs.length][];
        for (int i = 0; i < freqs.length; i++) {
            notes[i] = tone(freqs[i], noteSeconds, false);
        }
        return concat(notes);
    }
}
