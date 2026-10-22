package showroom.cli;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonStreamParser;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import showroom.dialogue.DialogueAct;
import showroom.dialogue.Feature;
import showroom.dialogue.Flow;
import static org.junit.Assert.*;

public class CliTest {
    @Test public void greetingHandoffAndFlowCallbacksWorkThroughInjection() throws Exception {
        String input = "/inject {\"acts\":[{\"type\":\"greet\"}]}\n"
                + "/inject {\"acts\":[{\"type\":\"inform\",\"slots\":{\"name\":\"Sam\"}}]}\n"
                + "/inject {\"acts\":[{\"type\":\"affirm\"}]}\n"
                + "/inject {\"acts\":[{\"type\":\"inform\",\"slots\":{\"part\":\"back-seat\"}}]}\n"
                + "/inject {\"acts\":[{\"type\":\"inform\",\"slots\":{\"choice\":\"2\"}}]}\n/state\n/quit\n";
        StringWriter output = new StringWriter();
        StringWriter traces = new StringWriter();
        Nlu unused = text -> { throw new AssertionError("Injection must bypass NLU."); };
        new Cli(unused, new FakeFlow(), new PrintWriter(output), new PrintWriter(traces)).run(new StringReader(input));
        assertTrue(output.toString().contains("What is your name?"));
        assertTrue(output.toString().contains("Would you like to see a demo?"));
        assertTrue(output.toString().contains("Requesting flow: rear_audio"));
        assertTrue(output.toString().contains("Completed Rear audio"));
        assertTrue(output.toString().contains("\"userName\": \"Sam\""));
        assertTrue(output.toString().contains("\"pendingInteraction\": null"));
        assertFalse(output.toString().contains("Input error"));
        JsonStreamParser entries = new JsonStreamParser(traces.toString());
        JsonObject last = null;
        while (entries.hasNext()) last = entries.next().getAsJsonObject();
        assertEquals("flow:completed", last.get("source").getAsString());
        JsonObject after = last.getAsJsonObject("processing").getAsJsonObject("after");
        assertEquals(1, after.getAsJsonArray("tasks").size());
        assertEquals("PLAY", after.getAsJsonArray("tasks").get(0).getAsJsonObject().get("kind").getAsString());
        assertTrue(after.get("pendingInteraction").isJsonNull());
    }

    @Test public void directPlayForgetsAnsweredChoicesRatherThanStartingAgain() throws Exception {
        String input = "/inject {\"acts\":[{\"type\":\"request\",\"slots\":{\"task\":\"play\"}}]}\n"
                + "/inject {\"acts\":[{\"type\":\"inform\",\"slots\":{\"choice\":\"2\"}}]}\n"
                + "/inject {\"acts\":[{\"type\":\"inform\",\"slots\":{\"choice\":\"1\"}}]}\n/quit\n";
        StringWriter output = new StringWriter();
        StringWriter traces = new StringWriter();
        new Cli(text -> { throw new AssertionError(); }, new FakeFlow(), new PrintWriter(output), new PrintWriter(traces))
                .run(new StringReader(input));
        assertTrue(output.toString().contains("Requesting flow: rear_audio"));
        assertFalse(output.toString().contains("Requesting flow: rear_displays"));
        assertFalse(output.toString().contains("What is your name?"));
        assertTrue(traces.toString().contains("unresolved_reference"));
    }

    @Test public void sharedConsoleTracesBeforeOutputIncludingSynchronousCallbacks() throws Exception {
        StringWriter transcript = new StringWriter();
        PrintWriter console = new PrintWriter(transcript);
        Nlu nlu = text -> JsonCodec.acts("{\"acts\":[{\"type\":\"request\",\"slots\":{\"task\":\"play\",\"flow_id\":\"headlights\"}}]}");
        new Cli(nlu, new FakeFlow(), console, console)
                .run(new StringReader("/inject invalid-json\nstart headlights\n/quit\n"));
        String[] sections = transcript.toString().replace("\r\n", "\n").split("===== TRACE =====\n");
        assertEquals(6, sections.length); // Error, turn, narration, progress, completion.
        for (int i = 1; i < sections.length; i++) {
            int output = sections[i].indexOf("\n===== OUTPUT =====\n");
            assertTrue("Trace must precede output", output > 0);
            assertTrue(JsonParser.parseString(sections[i].substring(0, output)).isJsonObject());
        }
        assertTrue(sections[2].contains("\"progress\": \"START_REQUESTED\""));
        assertTrue(sections[2].contains("===== OUTPUT =====\nRequesting flow: headlights"));
        assertTrue(sections[5].contains("\"progress\": \"COMPLETED\""));
    }

    @Test public void normalTextPassesOnlyCurrentTextToNlu() throws Exception {
        List<String> calls = new ArrayList<>();
        Nlu nlu = text -> {
            calls.add(text);
            return JsonCodec.acts("{\"acts\":[{\"type\":\"request\",\"slots\":{\"task\":\"play\",\"part\":\"back-seat\"}}]}");
        };
        StringWriter output = new StringWriter();
        new Cli(nlu, new FakeFlow(), new PrintWriter(output), new PrintWriter(new StringWriter()))
                .run(new StringReader("Play something in the back\n/quit\n"));
        assertEquals(Arrays.asList("Play something in the back"), calls);
        assertTrue(output.toString().contains("Rear displays"));
        assertFalse(output.toString().contains("Headlights ["));
    }

    @Test public void malformedInputIsTracedAndDoesNotEndTheChat() throws Exception {
        StringWriter output = new StringWriter();
        StringWriter traces = new StringWriter();
        new Cli(text -> { throw new IOException("No model configured"); }, new FakeFlow(), new PrintWriter(output), new PrintWriter(traces))
                .run(new StringReader("/inject not-json\nhello\n/inject {\"acts\":[{\"type\":\"greet\"}]}\n/quit\n"));
        assertTrue(output.toString().contains("Invalid NLU/act JSON"));
        assertTrue(output.toString().contains("No model configured"));
        assertTrue(output.toString().contains("What is your name?"));
        assertTrue(traces.toString().contains("\"error\""));
    }

    @Test public void thrownStartErrorHasAnOwnerBecauseStateWasAcceptedBeforeDispatch() throws Exception {
        FakeFlow catalog = new FakeFlow();
        Flow broken = new Flow() {
            public List<Feature> list() { return catalog.list(); }
            public void start(String id, Listener listener) { throw new IllegalStateException("Disconnected"); }
            public void stop() { }
        };
        StringWriter output = new StringWriter();
        StringWriter traces = new StringWriter();
        new Cli(text -> JsonCodec.acts("{\"acts\":[{\"type\":\"request\",\"slots\":{\"task\":\"play\",\"flow_id\":\"headlights\"}}]}"),
                broken, new PrintWriter(output), new PrintWriter(traces))
                .run(new StringReader("start headlights\n/state\n/quit\n"));
        assertTrue(output.toString().contains("Flow start failed: Disconnected"));
        assertTrue(output.toString().contains("\"progress\": \"FAILED\""));
        assertFalse(traces.toString().contains("flow.ignore"));
    }

    @Test public void adapterPreservesCompositeInputForExplicitCoreRejection() throws Exception {
        List<DialogueAct> acts = JsonCodec.acts("{\"acts\":[{\"type\":\"deny\"},{\"type\":\"inform\",\"slots\":{\"part\":\"front-seat\"}}]}");
        assertEquals(2, acts.size());
        assertEquals(DialogueAct.Type.DENY, acts.get(0).type);
        assertEquals("front-seat", acts.get(1).slots.get("part"));
    }

    @Test public void adapterRejectsArraySlotsRatherThanSilentlyChoosingAValue() throws Exception {
        try {
            JsonCodec.acts("{\"acts\":[{\"type\":\"inform\",\"slots\":{\"theme\":[\"family\",\"entertainment\"]}}]}");
            fail("Expected unsupported multi-value input.");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("multi-value"));
        }
    }
}
