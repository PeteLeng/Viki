package showroom.cli;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import showroom.dialogue.Act;
import showroom.dialogue.Feature;

/** Optional OpenAI-compatible chat-completions adapter; no SDK or conversation memory. */
public final class LlmNlu implements Nlu {
    private final URL endpoint;
    private final String model;
    private final String apiKey;
    private final String prompt;

    public LlmNlu(String endpoint, String model, String apiKey, List<Feature> catalog) throws IOException {
        this.endpoint = new URL(endpoint);
        this.model = model;
        this.apiKey = apiKey;
        this.prompt = "Parse only the supplied ASR text. Return JSON, with no markdown: "
                + "{\"acts\":[{\"type\":\"request\",\"slots\":{\"operation\":\"demonstrate\"}}]}. "
                + "Acts: request, inform, select, affirm, deny. Slots are string values. "
                + "Request operations: browse, explain, demonstrate. "
                + "Slots: feature_id (a known ID), feature (an unresolved name), part, theme, ordinal. "
                + "A request may also have relation=aside for explicit 'before that/then return' wording, "
                + "or relation=replace for explicit 'forget that/instead' wording. Otherwise omit relation. "
                + "Put arguments of a requested goal in that request's slots. Separate inform/select/affirm/deny acts "
                + "answer existing conversational context; never merge those answers into a different requested goal. "
                + "Use select with ordinal for 'the second one' (ordinal \"2\"); do not resolve it. "
                + "For 'start the second one', put operation=demonstrate and ordinal=2 in the request. "
                + "Use affirm/deny with empty slots for yes/no. "
                + "'No, the front seats' is deny plus inform(part=front-seat). "
                + "Keep multiple acts and all supplied values associated; do not discard conflicting values. "
                + "Use separate inform acts if several values for the same filter are expressed. "
                + "Never invent Task IDs, Task state, choices, confidence, or stack operations. "
                + "You have no conversation history. Static catalog vocabulary: " + JsonCodec.GSON.toJson(catalog);
    }

    @Override public List<Act> interpret(String text) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        JsonArray messages = new JsonArray();
        messages.add(message("system", prompt));
        messages.add(message("user", text));
        body.add("messages", messages);
        JsonObject format = new JsonObject();
        format.addProperty("type", "json_object");
        body.add("response_format", format);

        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        if (apiKey != null && !apiKey.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        try {
            byte[] request = JsonCodec.GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) { output.write(request); }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("NLU endpoint returned HTTP " + status);
            String response;
            try (InputStream input = connection.getInputStream()) { response = read(input); }
            try {
                String content = JsonParser.parseString(response).getAsJsonObject()
                        .getAsJsonArray("choices").get(0).getAsJsonObject()
                        .getAsJsonObject("message").get("content").getAsString();
                return JsonCodec.acts(content);
            } catch (RuntimeException e) {
                throw new IOException("NLU endpoint did not return a chat-completions message.", e);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

    static String read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
