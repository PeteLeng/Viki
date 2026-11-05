package showroom.cli.nlu;

import com.google.gson.Gson;
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
import showroom.ndialog.dialogue.UserAct;

/**
 * Hydra-backed interpreter: text in, user acts out. The server returns the
 * prediction verbatim (intent, confidence, slots with extracted text); this
 * adapter owns the hydra dialect end-to-end — the wire schema below, the
 * request body, and the prediction → act translation.
 */
public final class HydraNlu implements Nlu {
    private static final String DEFAULT_URL = "http://127.0.0.1:8756";
    private static final int CONNECT_TIMEOUT_MS = 2000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final String START_COMMAND =
            "cd D:\\yleng\\proj\\hydra\\notebooks && uv run --locked --offline --env-file local.env"
                    + " python m3_training/serve.py --port 8756";
    private static final Gson GSON = new Gson();

    /** The prediction's wire schema, mirrored for Gson; start/end offsets are not consumed. */
    private static final class PredictionJson {
        String intent;
        Double confidence;
        List<SlotJson> slots;
    }

    private static final class SlotJson {
        String type;
        String text;
    }

    private final String url;
    private final List<Feature> catalog;
    private boolean debug;

    public HydraNlu(String url, List<Feature> catalog) throws IOException {
        this.url = url == null || url.trim().isEmpty() ? DEFAULT_URL : url.trim();
        this.catalog = catalog;
        probe();
    }

    @Override public List<UserAct> interpret(String text) throws IOException {
        String body = GSON.toJson(Collections.singletonMap("text", text));
        String prediction = post(body);
        if (debug) System.err.println("[debug] nlu: " + prediction);
        return toActs(prediction, text, catalog);
    }

    @Override public void setDebug(boolean on) { this.debug = on; }

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
    static List<UserAct> toActs(String json, String utterance, List<Feature> catalog) throws IOException {
        try {
            PredictionJson prediction = GSON.fromJson(json, PredictionJson.class);
            if (prediction == null || prediction.intent == null) {
                throw new IllegalArgumentException("Expected a prediction with an intent.");
            }
            List<SlotJson> slots = prediction.slots == null ? Collections.<SlotJson>emptyList() : prediction.slots;
            Double confidence = prediction.confidence;

            List<UserAct> acts = new ArrayList<>();
            if (prediction.intent.equals("greet")) {
                acts.add(new UserAct(UserAct.Type.GREET, Collections.emptyMap(), confidence));
                String name = firstSlotText(slots, "name");
                if (name != null) {
                    acts.add(new UserAct(UserAct.Type.INFORM,
                            Collections.singletonMap("name", name), confidence));
                }
            } else if (prediction.intent.equals("affirm")) {
                acts.add(new UserAct(UserAct.Type.AFFIRM, Collections.emptyMap(), confidence));
            } else if (prediction.intent.equals("deny")) {
                acts.add(new UserAct(UserAct.Type.DENY, Collections.emptyMap(), confidence));
            } else if (prediction.intent.equals("inform")) {
                for (SlotJson slot : slots) {
                    String value = value(slot.type, slot.text, catalog);
                    if (value != null && !value.isEmpty()) {
                        acts.add(new UserAct(UserAct.Type.INFORM,
                                Collections.singletonMap(ndialogSlot(slot.type), value), confidence));
                    }
                }
            } else if (prediction.intent.equals("request_flow")) {
                Map<String, String> arguments = new LinkedHashMap<>();
                arguments.put("task", "flow");
                for (SlotJson slot : slots) {
                    String key = slot.type.equals("flow_ref") ? "flow_id" : slot.type;
                    if ((key.equals("flow_id") || key.equals("part") || key.equals("theme"))
                            && !arguments.containsKey(key)) {
                        arguments.put(key, value(slot.type, slot.text, catalog));
                    }
                }
                acts.add(new UserAct(UserAct.Type.REQUEST, arguments, confidence));
            } else if (prediction.intent.equals("request_stop") || prediction.intent.equals("request_resume")
                    || prediction.intent.equals("request_quit")) {
                acts.add(new UserAct(UserAct.Type.REQUEST,
                        Collections.singletonMap("task", prediction.intent.substring("request_".length())), confidence));
            } else {
                // UNK and any unknown intent: the raw utterance binds nowhere, and the engine says so.
                acts.add(new UserAct(UserAct.Type.INFORM,
                        Collections.singletonMap("text", utterance), confidence));
            }
            if (acts.isEmpty()) {
                acts.add(new UserAct(UserAct.Type.INFORM,
                        Collections.singletonMap("text", utterance), confidence));
            }
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

    private static String firstSlotText(List<SlotJson> slots, String type) {
        String text = null;
        for (SlotJson slot : slots) {
            if (type.equals(slot.type) && text == null) text = slot.text;
        }
        return text == null ? null : text.trim().isEmpty() ? null : text.trim();
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
