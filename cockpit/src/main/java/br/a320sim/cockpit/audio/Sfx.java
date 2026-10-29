package br.a320sim.cockpit.audio;

/** Efeitos sonoros, todos gerados por código (nenhum arquivo de áudio). */
public enum Sfx {
    /** Single chime: chega uma caution. */
    CHIME(Synth.concat(Synth.tone(1046, 0.35, true))),
    /** Continuous repetitive chime: warning ativo. */
    WARNING(Synth.concat(Synth.tone(1318, 0.12, false), Synth.silence(0.05),
            Synth.tone(1318, 0.12, false), Synth.silence(0.05), Synth.tone(1318, 0.12, false))),
    /** "Cavalry charge": piloto automático desligou. */
    CAVALRY(Synth.concat(Synth.arpeggio(0.07, 392, 523, 659, 784, 1046),
            Synth.arpeggio(0.07, 392, 523, 659, 784, 1046))),
    /** "Grilo" do alarme de estol. */
    STALL(Synth.concat(Synth.tone(2800, 0.05, false), Synth.silence(0.04), Synth.tone(2800, 0.05, false),
            Synth.silence(0.04), Synth.tone(2800, 0.05, false))),
    CLICK(Synth.tone(1800, 0.02, true)),
    LOAD(Synth.tone(1400, 0.03, false)),
    ERROR(Synth.concat(Synth.tone(160, 0.15, false), Synth.silence(0.03), Synth.tone(120, 0.25, true))),
    BOOT(Synth.concat(Synth.arpeggio(0.08, 523, 659, 784), Synth.tone(1046, 0.5, true)));

    private final float[] samples;

    Sfx(float[] samples) {
        this.samples = samples;
    }

    float[] samples() {
        return samples;
    }
}
