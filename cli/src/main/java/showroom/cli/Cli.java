package showroom.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import showroom.dialogue.DialogueAct;
import showroom.dialogue.DialogueCore;
import showroom.dialogue.DialogueResult;
import showroom.dialogue.DialogueState;
import showroom.dialogue.Feature;
import showroom.dialogue.Flow;
import showroom.dialogue.FlowCommand;

public final class Cli {
    private final Nlu nlu;
    private final Flow flow;
    private final DialogueCore core;
    private final List<Feature> catalog;
    private final PrintWriter output;
    private final PrintWriter diagnostics;
    private DialogueState state = new DialogueState();
    private Object lastTrace;

    public Cli(Nlu nlu, Flow flow, PrintWriter output, PrintWriter diagnostics) {
        this.nlu = nlu;
        this.flow = flow;
        this.catalog = flow.list();
        this.core = new DialogueCore(catalog);
        this.output = output;
        this.diagnostics = diagnostics;
    }

    public void run(Reader input) throws IOException {
        output.println("Showroom CLI. /inject JSON, /state, /trace, /quit");
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
                boolean injected = line.startsWith("/inject ");
                List<DialogueAct> acts;
                if (injected) acts = JsonCodec.acts(line.substring(line.indexOf(' ') + 1));
                else if (line.startsWith("/")) throw new IOException("Unknown CLI command.");
                else acts = nlu.interpret(line);
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

    private synchronized void userTurn(String source, List<DialogueAct> acts, long started) {
        DialogueResult result = core.handleUserTurn(state, acts);
        accept(source, result, System.nanoTime() - started);
    }

    private synchronized void flowEvent(FlowCommand request, Flow.Event event) {
        accept("flow:" + event.kind, core.handleFlowEvent(state, request, event), null);
    }

    private void accept(String source, DialogueResult result, Long elapsedNanos) {
        state = result.state;
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("source", source);
        entry.put("processing", result.trace);
        if (elapsedNanos != null) entry.put("totalElapsedNanos", elapsedNanos);
        trace(entry);
        if (!result.acts.isEmpty() || !result.commands.isEmpty()) section(output, "OUTPUT");
        for (DialogueAct act : result.acts) render(act);
        for (FlowCommand command : result.commands) {
            output.println("Requesting flow: " + command.flowId);
            output.flush();
            try {
                flow.start(command.flowId, event -> flowEvent(command, event));
            } catch (RuntimeException e) {
                flowEvent(command, new Flow.Event("failed", "Flow start failed: " + e.getMessage()));
            }
        }
    }

    private void render(DialogueAct act) {
        switch (act.type) {
            case GREET:
                output.println("Hello!");
                break;
            case REQUEST:
                output.println("name".equals(act.slots.get("slot")) ? "What is your name?" : "Which flow would you like?");
                break;
            case SELECT:
                output.println("Which flow would you like?");
                for (int i = 0; i < act.choices.size(); i++) {
                    String id = act.choices.get(i);
                    output.println((i + 1) + ". " + flowName(id) + " [" + id + "]");
                }
                break;
            case CONFIRM:
                output.println(act.slots.containsKey("task") ? "Would you like to see a demo?"
                        : "Did you mean " + flowName(act.slots.get("flow_id")) + "?");
                break;
            case INFORM:
                if (act.slots.containsKey("description")) output.println(act.slots.get("description"));
                else if (act.slots.containsKey("execution_status")) {
                    output.println(flowName(act.slots.get("flow_id")) + ": " + act.slots.get("execution_status"));
                } else output.println(act.slots);
                break;
            case AFFIRM: output.println("Yes."); break;
            case DENY: output.println("No."); break;
            default: throw new IllegalStateException("Unrendered act: " + act.type);
        }
        output.flush();
    }

    private String flowName(String id) {
        for (Feature feature : catalog) if (feature.id.equals(id)) return feature.name;
        return id;
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
