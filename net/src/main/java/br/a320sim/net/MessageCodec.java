package br.a320sim.net;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Map;

/** Converte mensagens em uma linha JSON ({"type": ..., "data": ...}) e de volta. */
public final class MessageCodec {

    private static final Map<String, Class<? extends Message>> TYPES = Map.of(
            "hello", Message.Hello.class,
            "snapshot", Message.BusSnapshot.class,
            "controls", Message.ControlUpdate.class);

    private final Gson gson = new GsonBuilder().serializeSpecialFloatingPointValues().create();

    public String encode(Message message) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("type", typeOf(message));
        envelope.add("data", gson.toJsonTree(message));
        return gson.toJson(envelope);
    }

    public Message decode(String line) {
        JsonObject envelope = JsonParser.parseString(line).getAsJsonObject();
        String type = envelope.get("type").getAsString();
        Class<? extends Message> target = TYPES.get(type);
        if (target == null) {
            throw new IllegalArgumentException("Tipo de mensagem desconhecido: " + type);
        }
        return gson.fromJson(envelope.get("data"), target);
    }

    private static String typeOf(Message message) {
        return switch (message) {
            case Message.Hello h -> "hello";
            case Message.BusSnapshot s -> "snapshot";
            case Message.ControlUpdate c -> "controls";
        };
    }
}
