package showroom.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import showroom.dialogue.Act;
import showroom.dialogue.DialogueCore;
import showroom.dialogue.DialogueResult;
import showroom.dialogue.DialogueState;
import showroom.dialogue.Feature;
import showroom.dialogue.Flow;
import showroom.dialogue.SystemAction;

public final class Cli {
    private final Nlu nlu;
    private final Flow flow;
    private final DialogueCore core;
    private final PrintWriter output;
    private final PrintWriter diagnostics;
    private DialogueState state = new DialogueState();
    private Object lastTrace;

    public Cli(Nlu nlu, Flow flow, PrintWriter output, PrintWriter diagnostics) {
        this.nlu = nlu;
        this.flow = flow;
        this.core = new DialogueCore(flow.list());
        this.output = output;
        this.diagnostics = diagnostics;
    }

    public void run(Reader input) throws IOException {
        output.println("Showroom CLI. /inject JSON, /aside JSON, /replace JSON, /state, /trace, /quit");
        output.flush();
        BufferedReader reader = new BufferedReader(input);
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.equals("/quit")) break;
            if (line.equals("/state")) { section(output, "STATE"); output.println(JsonCodec.DIAGNOSTIC_GSON.toJson(state)); output.flush(); continue; }
            if (line.equals("/trace")) { section(output, "TRACE"); output.println(JsonCodec.DIAGNOSTIC_GSON.toJson(lastTrace)); output.flush(); continue; }
            long started = System.nanoTime();
            try {
                boolean aside = line.startsWith("/aside ");
                boolean replace = line.startsWith("/replace ");
                boolean injected = line.startsWith("/inject ") || aside || replace;
                List<Act> acts;
                if (injected) acts = JsonCodec.acts(line.substring(line.indexOf(' ') + 1));
                else if (line.startsWith("/")) throw new IOException("Unknown CLI command.");
                else acts = nlu.interpret(line);
                if (aside || replace) acts = withRelation(acts, aside ? "aside" : "replace");
                userTurn(line, acts, started);
            } catch (IOException | IllegalArgumentException e) {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("source", line);
                error.put("error", e.getMessage());
                error.put("elapsedNanos", System.nanoTime() - started);
                trace(error);
                section(output, "OUTPUT");
                output.println("Input error: " + e.getMessage());
                output.flush();
            }
        }
    }

    private static List<Act> withRelation(List<Act> acts, String relation) {
        List<Act> qualified = new ArrayList<>();
        boolean found = false;
        for (Act act : acts) {
            if (act.type != Act.Type.REQUEST) {
                qualified.add(act);
                continue;
            }
            Map<String, String> slots = new LinkedHashMap<>(act.slots);
            String explicit = slots.get("relation");
            if (explicit != null && !explicit.equals(relation)) {
                throw new IllegalArgumentException("The shortcut conflicts with the request's relation.");
            }
            slots.put("relation", relation);
            qualified.add(new Act(act.type, slots));
            found = true;
        }
        if (!found) throw new IllegalArgumentException("The shortcut requires a request act.");
        return qualified;
    }

    private synchronized void userTurn(String source, List<Act> acts, long started) {
        DialogueResult result = core.handleUserTurn(state, acts);
        accept(source, result, System.nanoTime() - started);
    }

    private synchronized void flowEvent(SystemAction request, Flow.Event event) {
        accept("flow:" + event.kind, core.handleFlowEvent(state, request, event), null);
    }

    private void accept(String source, DialogueResult result, Long elapsedNanos) {
        state = result.state;
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("source", source);
        entry.put("processing", result.trace);
        if (elapsedNanos != null) entry.put("totalElapsedNanos", elapsedNanos);
        trace(entry);
        if (!result.actions.isEmpty()) section(output, "OUTPUT");
        for (SystemAction action : result.actions) {
            if (action.kind == SystemAction.Kind.START_DEMO) {
                output.println("Requesting demo: " + action.featureId);
                output.flush();
                try {
                    flow.start(action.featureId, event -> flowEvent(action, event));
                } catch (RuntimeException e) {
                    flowEvent(action, new Flow.Event("failed", "Flow start failed: " + e.getMessage()));
                }
            } else {
                output.println(action.text);
                for (int i = 0; i < action.choices.size(); i++) {
                    Feature feature = action.choices.get(i);
                    output.println((i + 1) + ". " + feature.name + " [" + feature.id + "]");
                }
                output.flush();
            }
        }
    }

    private void trace(Object entry) {
        lastTrace = entry;
        if (diagnostics == output) section(diagnostics, "TRACE");
        diagnostics.println(JsonCodec.DIAGNOSTIC_GSON.toJson(entry));
        diagnostics.flush();
    }

    private void section(PrintWriter writer, String label) {
        writer.println();
        writer.println("===== " + label + " =====");
    }
}
