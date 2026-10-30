package br.a320sim.core.bus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Barramento de dados do avião, inspirado no ARINC 429.
 * As caixas nunca se conhecem: cada uma só publica e lê "labels" aqui.
 */
public final class DataBus {

    private final Map<String, Object> values = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<Object>>> listeners = new ConcurrentHashMap<>();

    public void publish(String label, Object value) {
        values.put(label, value);
        listeners.getOrDefault(label, List.of()).forEach(listener -> listener.accept(value));
    }

    public void subscribe(String label, Consumer<Object> listener) {
        listeners.computeIfAbsent(label, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public boolean has(String label) {
        return values.containsKey(label);
    }

    public double getDouble(String label, double fallback) {
        return values.get(label) instanceof Number n ? n.doubleValue() : fallback;
    }

    public boolean getBoolean(String label) {
        return values.get(label) instanceof Boolean b && b;
    }

    @SuppressWarnings("unchecked")
    public List<String> getList(String label) {
        return values.get(label) instanceof List<?> list ? (List<String>) list : List.of();
    }

    public String getString(String label, String fallback) {
        Object value = values.get(label);
        return value != null ? value.toString() : fallback;
    }

    /** Cópia de todos os valores, para mandar pela rede. */
    public Map<String, Object> snapshot() {
        return new HashMap<>(values);
    }

    /** Substitui tudo pelo conteúdo recebido (o cockpit remoto espelha o barramento do host). */
    public void replaceAll(Map<String, Object> snapshot) {
        values.clear();
        values.putAll(snapshot);
    }

    /** Apaga todos os labels de um prefixo, ex.: quando uma caixa é desligada. */
    public void clear(String prefix) {
        values.keySet().removeIf(label -> label.startsWith(prefix));
    }
}
