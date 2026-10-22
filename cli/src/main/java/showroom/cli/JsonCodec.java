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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import showroom.ndialog.Feature;
import showroom.ndialog.Segment;

final class JsonCodec {
    static final Gson GSON = new GsonBuilder().serializeNulls().create();
    static final Gson DIAGNOSTIC_GSON = GSON.newBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Feature.class,
                    (JsonSerializer<Feature>) (feature, type, context) -> new JsonPrimitive(feature.id))
            .create();

    static List<Segment> segments(String json) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.size() != 1 || !root.has("segments")) throw new IllegalArgumentException("Expected a segments object.");
            JsonArray array = root.getAsJsonArray("segments");
            if (array.size() == 0) throw new IllegalArgumentException("At least one segment is required.");
            List<Segment> segments = new ArrayList<Segment>();
            for (JsonElement item : array) {
                JsonObject object = item.getAsJsonObject();
                for (String key : object.keySet()) {
                    if (!key.equals("key") && !key.equals("slots")) throw new IllegalArgumentException("Unknown segment field: " + key);
                }
                String key = string(object, "key");
                Map<String, String> slots = new LinkedHashMap<String, String>();
                if (object.has("slots")) {
                    for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("slots").entrySet()) {
                        JsonElement value = entry.getValue();
                        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                            throw new IllegalArgumentException("Slot values must be strings.");
                        }
                        slots.put(entry.getKey(), value.getAsString());
                    }
                }
                segments.add(new Segment(key, slots));
            }
            return segments;
        } catch (RuntimeException e) {
            throw new IOException("Invalid segment JSON: " + e.getMessage(), e);
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
