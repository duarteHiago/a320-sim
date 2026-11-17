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
    BOOT(Synth.concat(Synth.arpeggio(0.08, 523, 659, 784), Synth.tone(1046, 0.5, true))),

    /** Cursor andando no menu. */
    MOVE(Synth.tone(1250, 0.025, true)),
    /** Confirmar no menu: dois tons subindo. */
    SELECT(Synth.concat(Synth.tone(880, 0.05, false), Synth.tone(1760, 0.09, true))),
    /** Voltar: dois tons descendo. */
    BACK(Synth.concat(Synth.tone(988, 0.05, false), Synth.tone(494, 0.09, true))),
    /** Cartucho entrando no slot: baque abafado e o "clique" da trava. */
    INSERT(Synth.concat(Synth.layer(Synth.noise(0.09, 0.85), Synth.triangle(90, 0.09, true)),
            Synth.silence(0.03), Synth.tone(2200, 0.015, true), Synth.sweep(600, 1400, 0.08))),
    /** Cartucho saindo. */
    EJECT(Synth.concat(Synth.tone(2200, 0.015, true), Synth.sweep(1200, 300, 0.12),
            Synth.noise(0.06, 0.8))),
    /** Vinheta da abertura, à moda dos consoles. */
    JINGLE(Synth.layer(
            Synth.melody(false, 523, 0.12, 659, 0.12, 784, 0.12, 1046, 0.24, 0, 0.06, 784, 0.12, 1046, 0.6),
            Synth.melody(true, 131, 0.36, 196, 0.36, 262, 0.84))),
    /** "Logo" aparecendo: varredura subindo. */
    WHOOSH(Synth.layer(Synth.sweep(200, 1600, 0.35), Synth.noise(0.35, 0.6)));

    private final float[] samples;

    Sfx(float[] samples) {
        this.samples = samples;
    }

    float[] samples() {
        return samples;
    }
}
