package showroom.cli;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import showroom.dialogue.DialogueAct;
import static org.junit.Assert.*;

public class LlmNluTest {
    @Test public void httpAdapterIsStatelessAndPreservesUnresolvedReferences() throws Exception {
        List<JsonObject> requests = Collections.synchronizedList(new ArrayList<>());
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat", exchange -> {
            requests.add(JsonParser.parseString(LlmNlu.read(exchange.getRequestBody())).getAsJsonObject());
            JsonObject message = new JsonObject();
            message.addProperty("content", "{\"acts\":[{\"type\":\"inform\",\"slots\":{\"choice\":\"2\"}}]}");
            JsonObject choice = new JsonObject();
            choice.add("message", message);
            com.google.gson.JsonArray choices = new com.google.gson.JsonArray();
            choices.add(choice);
            JsonObject response = new JsonObject();
            response.add("choices", choices);
            byte[] bytes = JsonCodec.GSON.toJson(response).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            LlmNlu nlu = new LlmNlu("http://127.0.0.1:" + server.getAddress().getPort() + "/chat",
                    "test-model", null, new FakeFlow().list());
            List<DialogueAct> acts = nlu.interpret("The second one");
            nlu.interpret("第二个");
            assertEquals(DialogueAct.Type.INFORM, acts.get(0).type);
            assertEquals("2", acts.get(0).slots.get("choice"));
            assertFalse(acts.get(0).slots.containsKey("flow_id"));
            assertEquals(2, requests.size());
            for (JsonObject request : requests) {
                assertEquals(2, request.getAsJsonArray("messages").size());
                String prompt = request.getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString();
                assertTrue(prompt.contains("\"aliases\""));
                assertTrue(prompt.contains("\"description\""));
            }
            assertEquals("第二个", requests.get(1).getAsJsonArray("messages").get(1).getAsJsonObject().get("content").getAsString());
            assertFalse(requests.get(1).toString().contains("The second one"));
        } finally {
            server.stop(0);
        }
    }

    @Test public void endpointFailureIsReportedWithoutTreatingErrorBodyAsActs() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat", exchange -> { exchange.sendResponseHeaders(503, -1); exchange.close(); });
        server.start();
        try {
            LlmNlu nlu = new LlmNlu("http://127.0.0.1:" + server.getAddress().getPort() + "/chat",
                    "test-model", null, Collections.emptyList());
            try {
                nlu.interpret("start a demo");
                fail("Expected HTTP error.");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("503"));
            }
        } finally {
            server.stop(0);
        }
    }
}
