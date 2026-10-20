package showroom.cli;

import com.google.gson.JsonStreamParser;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import showroom.dialogue.Act;
import showroom.dialogue.Feature;
import showroom.dialogue.Flow;
import static org.junit.Assert.*;

public class CliTest {
    @Test public void sharedConsoleSeparatesTraceAndOutputInOrderIncludingErrorsAndCallbacks() throws Exception {
        StringWriter transcript = new StringWriter();
        PrintWriter console = new PrintWriter(transcript);
        Nlu nlu = text -> Arrays.asList(Act.request("demonstrate"), Act.inform("feature_id", "headlights"));
        new Cli(nlu, new FakeFlow(), console, console)
                .run(new StringReader("/inject invalid-json\nstart headlights\n/quit\n"));

        String[] sections = transcript.toString().replace("\r\n", "\n").split("===== TRACE =====\n");
        // One input error, one user turn, then narration, progress, and completion.
        assertEquals(6, sections.length);
        for (int i = 1; i < sections.length; i++) {
            int output = sections[i].indexOf("\n===== OUTPUT =====\n");
            assertTrue("Trace must precede its output", output > 0);
            assertTrue(JsonParser.parseString(sections[i].substring(0, output)).isJsonObject());
        }
        assertTrue(sections[1].contains("===== OUTPUT =====\nInput error:"));
        assertTrue(sections[2].contains("===== OUTPUT =====\nRequesting demo: headlights"));
        assertTrue(sections[5].contains("===== OUTPUT =====\n[fake flow] Completed Headlights"));
    }

    @Test public void injectionCompletesFakeFlowAndStillAcceptsReferenceToPreviousChoices() throws Exception {
        String input = "/inject {\"acts\":[{\"type\":\"request\",\"slots\":{\"operation\":\"demonstrate\"}}]}\n"
                + "/inject {\"acts\":[{\"type\":\"select\",\"slots\":{\"ordinal\":\"2\"}}]}\n"
                + "/inject {\"acts\":[{\"type\":\"select\",\"slots\":{\"ordinal\":\"1\"}}]}\n/state\n/quit\n";
        StringWriter output = new StringWriter();
        StringWriter traces = new StringWriter();
        Nlu unused = text -> { throw new AssertionError("Injection must bypass NLU."); };
        new Cli(unused, new FakeFlow(), new PrintWriter(output), new PrintWriter(traces)).run(new StringReader(input));
        assertTrue(output.toString().contains("Requesting demo: rear_audio"));
        assertTrue(output.toString().contains("Requesting demo: rear_displays"));
        assertTrue(output.toString().contains("Completed Rear audio"));
        assertFalse(output.toString().contains("Input error"));
        assertTrue(traces.toString().contains("Ordinal 2"));
        assertTrue(traces.toString().contains("flow:completed"));
        assertTrue(traces.toString().contains("\n  \"source\":"));
        assertFalse(traces.toString().contains("\"aliases\""));
        assertFalse(traces.toString().contains("\"parts\""));
        assertFalse(traces.toString().contains("\"themes\""));
        assertFalse(traces.toString().contains("\"description\""));
        JsonStreamParser entries = new JsonStreamParser(traces.toString());
        while (entries.hasNext()) {
            com.google.gson.JsonObject entry = entries.next().getAsJsonObject();
            com.google.gson.JsonArray choices = entry.getAsJsonObject("processing")
                    .getAsJsonObject("after").getAsJsonArray("tasks").get(0)
                    .getAsJsonObject().getAsJsonArray("choices");
            assertEquals("rear_displays", choices.get(0).getAsString());
            assertEquals("rear_audio", choices.get(1).getAsString());
        }
    }

    @Test public void normalTextPassesOnlyThatTextToNlu() throws Exception {
        List<String> calls = new ArrayList<>();
        Nlu nlu = text -> {
            calls.add(text);
            return Arrays.asList(Act.request("browse"), Act.inform("part", "back-seat"));
        };
        StringWriter output = new StringWriter();
        StringWriter traces = new StringWriter();
        new Cli(nlu, new FakeFlow(), new PrintWriter(output), new PrintWriter(traces))
                .run(new StringReader("What is in the back?\n/quit\n"));
        assertEquals(Arrays.asList("What is in the back?"), calls);
        assertTrue(output.toString().contains("Rear displays"));
        assertFalse(output.toString().contains("Headlights ["));
        assertTrue(traces.toString().contains("What is in the back?"));
    }

    @Test public void malformedInputIsTracedAndDoesNotEndTheChat() throws Exception {
        StringWriter output = new StringWriter();
        StringWriter traces = new StringWriter();
        Nlu unused = text -> { throw new IOException("No model configured"); };
        new Cli(unused, new FakeFlow(), new PrintWriter(output), new PrintWriter(traces))
                .run(new StringReader("/inject not-json\nhello\n/inject {\"acts\":[{\"type\":\"request\",\"slots\":{\"operation\":\"browse\"}}]}\n/quit\n"));
        assertTrue(output.toString().contains("Invalid NLU/act JSON"));
        assertTrue(output.toString().contains("No model configured"));
        assertTrue(output.toString().contains("Available features:"));
        assertTrue(traces.toString().contains("\"error\""));
    }

    @Test public void thrownStartErrorBecomesFailureNotification() throws Exception {
        FakeFlow catalog = new FakeFlow();
        Flow broken = new Flow() {
            public List<Feature> list() { return catalog.list(); }
            public void start(String id, Listener listener) { throw new IllegalStateException("Disconnected"); }
            public void stop() { }
        };
        StringWriter output = new StringWriter();
        StringWriter traces = new StringWriter();
        new Cli(text -> Arrays.asList(Act.request("demonstrate"), Act.inform("feature_id", "headlights")),
                broken, new PrintWriter(output), new PrintWriter(traces))
                .run(new StringReader("start headlights\n/state\n/quit\n"));
        assertTrue(output.toString().contains("Flow start failed: Disconnected"));
        assertTrue(output.toString().contains("\"demoOutstanding\": false"));
        assertTrue(traces.toString().contains("flow:failed"));
    }

    @Test public void adapterRejectsArraySlotsRatherThanSilentlyChoosingAValue() throws Exception {
        try {
            JsonCodec.acts("{\"acts\":[{\"type\":\"inform\",\"slots\":{\"theme\":[\"family\",\"entertainment\"]}}]}");
            fail("Expected unsupported multi-value input.");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("multi-value"));
        }
    }

    @Test public void asideAndReplacementShortcutsInjectRequestRelationsThroughTheSameCoreEntry() throws Exception {
        StringWriter output = new StringWriter();
        StringWriter traces = new StringWriter();
        new Cli(text -> { throw new AssertionError(); }, new FakeFlow(), new PrintWriter(output), new PrintWriter(traces))
                .run(new StringReader("/inject {\"acts\":[{\"type\":\"request\",\"slots\":{\"operation\":\"browse\"}}]}\n"
                        + "/aside {\"acts\":[{\"type\":\"request\",\"slots\":{\"operation\":\"explain\",\"feature_id\":\"headlights\"}}]}\n"
                        + "/replace {\"acts\":[{\"type\":\"request\",\"slots\":{\"operation\":\"demonstrate\",\"feature_id\":\"rear_audio\"}}]}\n/quit\n"));
        assertTrue(output.toString().contains("Headlights:"));
        assertTrue(output.toString().contains("Requesting demo: rear_audio"));
        assertFalse(output.toString().contains("Input error:"));
        assertTrue(traces.toString().contains("\"relation\": \"aside\""));
        assertTrue(traces.toString().contains("\"relation\": \"replace\""));
    }

    @Test public void oneInjectedTurnAnswersParentAndSuppliesIndependentAsideArguments() throws Exception {
        StringWriter output = new StringWriter();
        StringWriter traces = new StringWriter();
        new Cli(text -> { throw new AssertionError("Injection bypasses NLU."); }, new FakeFlow(),
                new PrintWriter(output), new PrintWriter(traces))
                .run(new StringReader("/inject {\"acts\":[{\"type\":\"request\",\"slots\":{\"operation\":\"demonstrate\",\"part\":\"back-seat\"}}]}\n"
                        + "/inject {\"acts\":[{\"type\":\"select\",\"slots\":{\"ordinal\":\"2\"}},{\"type\":\"request\",\"slots\":{\"operation\":\"explain\",\"feature_id\":\"headlights\",\"relation\":\"aside\"}}]}\n"
                        + "/state\n/quit\n"));
        assertFalse(output.toString().contains("conflicts with the current filters"));
        assertTrue(output.toString().contains("Headlights:"));
        assertTrue(output.toString().contains("Requesting demo: rear_audio"));
        assertTrue(output.toString().indexOf("Headlights:") < output.toString().indexOf("Requesting demo: rear_audio"));
        assertTrue(output.toString().contains("\"featureId\": \"rear_audio\""));
        assertTrue(output.toString().contains("\"part\": \"back-seat\""));
        boolean sawRequestedBinding = false;
        JsonStreamParser entries = new JsonStreamParser(traces.toString());
        while (entries.hasNext()) {
            for (com.google.gson.JsonElement detail : entries.next().getAsJsonObject()
                    .getAsJsonObject("processing").getAsJsonArray("binding")) {
                if (detail.getAsString().equals("requested EXPLAIN.feature_id=headlights")) sawRequestedBinding = true;
            }
        }
        assertTrue(sawRequestedBinding);
    }

    @Test public void shortcutDoesNotSilentlyOverrideAnExplicitConflictingRelation() throws Exception {
        StringWriter output = new StringWriter();
        StringWriter traces = new StringWriter();
        new Cli(text -> { throw new AssertionError(); }, new FakeFlow(), new PrintWriter(output), new PrintWriter(traces))
                .run(new StringReader("/inject {\"acts\":[{\"type\":\"request\",\"slots\":{\"operation\":\"browse\"}}]}\n"
                        + "/aside {\"acts\":[{\"type\":\"request\",\"slots\":{\"operation\":\"explain\",\"relation\":\"replace\",\"feature_id\":\"headlights\"}}]}\n"
                        + "/state\n/quit\n"));
        assertTrue(output.toString().contains("Input error: The shortcut conflicts"));
        assertTrue(output.toString().contains("\"kind\": \"BROWSE\""));
        assertFalse(output.toString().contains("Headlights:"));
    }
}
