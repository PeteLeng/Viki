package showroom.cli;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import showroom.ndialog.bridge.Feature;
import showroom.ndialog.dialogue.Act;

/**
 * Hydra-backed interpreter: text in, acts out. The server returns the prediction
 * verbatim (intent, confidence, slots with extracted text); this adapter owns
 * the prediction → act translation.
 */
public final class HydraNlu implements Nlu {
    private static final String DEFAULT_URL = "http://127.0.0.1:8756";
    private static final int CONNECT_TIMEOUT_MS = 2000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final String START_COMMAND =
            "cd D:\\yleng\\proj\\hydra\\notebooks && uv run --locked --offline --env-file local.env"
                    + " python m3_training/serve.py --port 8756";

    private final String url;
    private final List<Feature> catalog;

    public HydraNlu(String url, List<Feature> catalog) throws IOException {
        this.url = url == null || url.trim().isEmpty() ? DEFAULT_URL : url.trim();
        this.catalog = catalog;
        probe();
    }

    @Override public List<Act> interpret(String text) throws IOException {
        String body = JsonCodec.GSON.toJson(Collections.singletonMap("text", text));
        return toActs(post(body), text, catalog);
    }

    private void probe() throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url + "/health").openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(CONNECT_TIMEOUT_MS);
            int status = connection.getResponseCode();
            if (status / 100 != 2) throw new IOException("health check returned " + status + ".");
        } catch (IOException e) {
            throw new IOException("hydra NLU server unreachable at " + url + ". Start it first: " + START_COMMAND, e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String post(String body) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url + "/parse").openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            InputStream stream = status / 100 == 2 ? connection.getInputStream() : connection.getErrorStream();
            String response = stream == null ? "" : read(stream);
            if (status / 100 != 2) {
                throw new IOException("hydra NLU returned " + status + ": " + excerpt(response));
            }
            return response;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** The prediction → act translation; pure, so tests need no server. */
    static List<Act> toActs(String json, String utterance, List<Feature> catalog) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            String intent = string(root, "intent");
            Double confidence = root.has("confidence") && root.get("confidence").isJsonPrimitive()
                    ? root.get("confidence").getAsDouble() : null;
            JsonArray slots = root.has("slots") && root.get("slots").isJsonArray()
                    ? root.getAsJsonArray("slots") : new JsonArray();

            List<Act> acts = new ArrayList<Act>();
            if (intent.equals("greet")) {
                acts.add(Act.greet().withConfidence(confidence));
                String name = firstSlotText(slots, "name");
                if (name != null) acts.add(Act.inform("name", name).withConfidence(confidence));
            } else if (intent.equals("affirm")) {
                acts.add(Act.affirm().withConfidence(confidence));
            } else if (intent.equals("deny")) {
                acts.add(Act.deny().withConfidence(confidence));
            } else if (intent.equals("inform")) {
                for (JsonElement item : slots) {
                    JsonObject slot = item.getAsJsonObject();
                    String type = string(slot, "type");
                    String value = value(type, string(slot, "text"), catalog);
                    if (value != null && !value.isEmpty()) {
                        acts.add(Act.inform(ndialogSlot(type), value).withConfidence(confidence));
                    }
                }
            } else if (intent.equals("request_flow")) {
                Map<String, String> found = new LinkedHashMap<String, String>();
                for (JsonElement item : slots) {
                    JsonObject slot = item.getAsJsonObject();
                    String type = string(slot, "type");
                    String key = type.equals("flow_ref") ? "flow_id" : type;
                    if ((key.equals("flow_id") || key.equals("part") || key.equals("theme"))
                            && !found.containsKey(key)) {
                        found.put(key, value(type, string(slot, "text"), catalog));
                    }
                }
                Map<String, List<String>> arguments = new LinkedHashMap<String, List<String>>();
                arguments.put("task", Collections.singletonList("flow"));
                for (Map.Entry<String, String> entry : found.entrySet()) {
                    arguments.put(entry.getKey(), Collections.singletonList(entry.getValue()));
                }
                acts.add(new Act(Act.Type.REQUEST, arguments).withConfidence(confidence));
            } else if (intent.equals("request_stop") || intent.equals("request_resume")
                    || intent.equals("request_quit")) {
                acts.add(Act.goal(intent.substring("request_".length())).withConfidence(confidence));
            } else {
                // UNK and any unknown intent: the raw utterance binds nowhere, and the engine says so.
                acts.add(Act.inform("text", utterance).withConfidence(confidence));
            }
            if (acts.isEmpty()) acts.add(Act.inform("text", utterance).withConfidence(confidence));
            return acts;
        } catch (RuntimeException e) {
            throw new IOException("Invalid prediction: " + e.getMessage(), e);
        }
    }

    private static String value(String type, String text, List<Feature> catalog) {
        String trimmed = text.trim();
        if (type.equals("name")) return trimmed;
        if (type.equals("flow_ref")) return HydraValues.flow(trimmed, catalog);
        if (type.equals("part")) return HydraValues.part(trimmed);
        if (type.equals("theme")) return HydraValues.theme(trimmed);
        if (type.equals("choice")) return HydraValues.choice(trimmed);
        return null;
    }

    private static String ndialogSlot(String type) {
        return type.equals("flow_ref") ? "flow_id" : type;
    }

    private static String firstSlotText(JsonArray slots, String type) {
        String text = null;
        for (JsonElement item : slots) {
            JsonObject slot = item.getAsJsonObject();
            if (string(slot, "type").equals(type)) {
                if (text == null) text = string(slot, "text");
            }
        }
        return text == null ? null : text.trim().isEmpty() ? null : text.trim();
    }

    private static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Expected string field: " + name);
        }
        return value.getAsString();
    }

    private static String read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String excerpt(String text) {
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() <= 200 ? compact : compact.substring(0, 200) + "...";
    }
}
