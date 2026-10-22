package showroom.cli;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import showroom.ndialog.bridge.Feature;
import showroom.ndialog.dialogue.Act;

final class JsonCodec {
    static final Gson GSON = new GsonBuilder().serializeNulls().create();
    static final Gson DIAGNOSTIC_GSON = GSON.newBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Feature.class,
                    (JsonSerializer<Feature>) (feature, type, context) -> new JsonPrimitive(feature.id))
            .create();

    /** User input carries one string value per slot; arrays are multi-value input and are rejected. */
    static List<Act> acts(String json) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.size() != 1 || !root.has("acts")) throw new IllegalArgumentException("Expected an acts object.");
            JsonArray array = root.getAsJsonArray("acts");
            if (array.size() == 0) throw new IllegalArgumentException("At least one act is required.");
            List<Act> acts = new ArrayList<Act>();
            for (JsonElement item : array) {
                JsonObject object = item.getAsJsonObject();
                for (String key : object.keySet()) {
                    if (!key.equals("type") && !key.equals("slots")) throw new IllegalArgumentException("Unknown act field: " + key);
                }
                Act.Type type = Act.Type.valueOf(string(object, "type").toUpperCase(java.util.Locale.ROOT));
                Map<String, List<String>> slots = new LinkedHashMap<String, List<String>>();
                if (object.has("slots")) {
                    for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("slots").entrySet()) {
                        JsonElement value = entry.getValue();
                        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                            throw new IllegalArgumentException("Slot values must be single strings; multi-value input is not implemented.");
                        }
                        slots.put(entry.getKey(), Collections.singletonList(value.getAsString()));
                    }
                }
                validateShape(type, slots);
                acts.add(new Act(type, slots));
            }
            return acts;
        } catch (RuntimeException e) {
            throw new IOException("Invalid act JSON: " + e.getMessage(), e);
        }
    }

    private static void validateShape(Act.Type type, Map<String, List<String>> slots) {
        if (type == Act.Type.GREET || type == Act.Type.AFFIRM || type == Act.Type.DENY) {
            if (!slots.isEmpty()) throw new IllegalArgumentException(type + " takes no slots.");
        } else if (type == Act.Type.REQUEST) {
            if (!slots.containsKey("task")) throw new IllegalArgumentException("A request needs a task.");
        } else if (type == Act.Type.INFORM) {
            if (slots.size() != 1) throw new IllegalArgumentException("An inform carries one named value.");
        }
    }

    static List<Feature> catalog(String json) throws IOException {
        try {
            List<Feature> result = new ArrayList<Feature>();
            for (JsonElement item : JsonParser.parseString(json).getAsJsonArray()) {
                JsonObject feature = item.getAsJsonObject();
                result.add(new Feature(string(feature, "id"), string(feature, "name"),
                        strings(feature, "aliases"), strings(feature, "parts"), strings(feature, "themes"),
                        string(feature, "description")));
            }
            return result;
        } catch (RuntimeException e) {
            throw new IOException("Invalid catalog: " + e.getMessage(), e);
        }
    }

    private static List<String> strings(JsonObject object, String name) {
        List<String> values = new ArrayList<String>();
        for (JsonElement value : object.getAsJsonArray(name)) values.add(value.getAsString());
        return values;
    }

    private static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Expected string field: " + name);
        }
        return value.getAsString();
    }
}
