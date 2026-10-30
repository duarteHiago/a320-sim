package br.a320sim.net;

import java.util.Map;

/** Tudo o que trafega entre instrutor e aluno. Cada tipo de mensagem é um record. */
public sealed interface Message {

    /** Aluno → instrutor, ao conectar. */
    record Hello(String name) implements Message {
    }

    /** Instrutor → aluno, ~30 vezes por segundo: uma cópia de todo o barramento. */
    record BusSnapshot(Map<String, Object> values) implements Message {
    }

    /** Aluno → instrutor, a cada quadro: manche, manete, alavancas e FCU. */
    record ControlUpdate(Map<String, Object> values) implements Message {
    }
}
