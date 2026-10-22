package showroom.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.List;
import showroom.ndialog.bridge.AppPort;
import showroom.ndialog.bridge.Feature;
import showroom.ndialog.dialogue.DialogueAct;
import showroom.ndialog.dialogue.DialogState;
import showroom.ndialog.dialogue.NDialogCore;
import showroom.ndialog.nlu.Segment;

public final class Cli {
    private final Nlu nlu;
    private final AppPort app;
    private final NDialogCore core;
    private final List<Feature> catalog;
    private final PrintWriter out;
    private DialogState state = new DialogState();

    public Cli(Nlu nlu, AppPort app, PrintWriter out) {
        this.nlu = nlu;
        this.app = app;
        this.catalog = app.list();
        this.core = new NDialogCore(catalog);
        this.out = out;
    }

    public void run(Reader input) throws IOException {
        out.println("ndialog CLI. text, /inject JSON, /state, /quit");
        BufferedReader reader = new BufferedReader(input);
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.equals("/quit")) break;
            if (line.equals("/state")) { out.println(JsonCodec.DIAGNOSTIC_GSON.toJson(state)); continue; }
            try {
                List<Segment> segments;
                if (line.startsWith("/inject ")) segments = JsonCodec.segments(line.substring(8));
                else if (line.startsWith("/")) throw new IOException("Unknown CLI command.");
                else segments = nlu.interpret(line);
                accept(core.handleUserTurn(state, segments));
            } catch (IOException | IllegalArgumentException e) {
                out.println("Input error: " + e.getMessage());
            }
        }
    }

    private void accept(DialogState after) {
        state = after;
        for (DialogueAct act : state.acts()) render(act);
        for (DialogState.Start start : state.starts()) {
            out.println("Requesting flow: " + start.flowId);
            app.start(start.flowId, event -> onEvent(start, event));
        }
    }

    private void onEvent(DialogState.Start start, AppPort.Event event) {
        state = core.onFlowEvent(state, start.agentId, event);
        out.println("flow: " + event.kind + " " + start.flowId);
        for (DialogueAct act : state.acts()) render(act);
    }

    private void render(DialogueAct act) {
        switch (act.type) {
            case GREET:
                out.println("Hello!");
                break;
            case REQUEST:
                out.println("name".equals(act.slots.get("slot")) ? "What is your name?" : "Which flow would you like?");
                break;
            case SELECT:
                out.println("Which flow would you like?");
                for (int i = 0; i < act.choices.size(); i++) {
                    out.println((i + 1) + ". " + flowName(act.choices.get(i)) + " [" + act.choices.get(i) + "]");
                }
                break;
            case CONFIRM:
                out.println(act.slots.containsKey("task") ? "Would you like to see a demo?"
                        : "Did you mean " + flowName(act.slots.get("id")) + "?");
                break;
            case INFORM:
                if (act.slots.containsKey("description")) out.println(act.slots.get("description"));
                else if (act.slots.containsKey("execution_status")) {
                    out.println(flowName(act.slots.get("flow_id")) + ": " + act.slots.get("execution_status"));
                } else out.println(act.slots);
                break;
            default:
                throw new IllegalStateException("Unrendered act: " + act.type);
        }
    }

    private String flowName(String id) {
        for (Feature feature : catalog) if (feature.id.equals(id)) return feature.name;
        return id;
    }
}
