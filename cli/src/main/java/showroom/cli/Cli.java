package showroom.cli;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import showroom.cli.json.UserActJson;
import showroom.cli.nlu.Nlu;
import showroom.ndialog.bridge.AppPort;
import showroom.ndialog.bridge.Feature;
import showroom.ndialog.dialogue.DialogState;
import showroom.ndialog.dialogue.NDialogCore;
import showroom.ndialog.dialogue.SysAct;
import showroom.ndialog.dialogue.Turn;
import showroom.ndialog.dialogue.UserAct;

public final class Cli {
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final Gson DIAGNOSTIC_GSON = GSON.newBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Feature.class,
                    (JsonSerializer<Feature>) (feature, type, context) -> new JsonPrimitive(feature.id))
            .create();

    private final Nlu nlu;
    private final AppPort app;
    private final NDialogCore core;
    private final List<Feature> catalog;
    private final PrintWriter out;
    private DialogState state = new DialogState();
    private boolean debug;

    public Cli(Nlu nlu, AppPort app, PrintWriter out) {
        this.nlu = nlu;
        this.app = app;
        this.catalog = app.list();
        this.core = new NDialogCore(catalog);
        this.out = out;
    }

    public void run(Reader input) throws IOException {
        out.println("ndialog CLI. text, /inject JSON, /state, /debug, /quit");
        BufferedReader reader = new BufferedReader(input);
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.equals("/quit")) break;
            if (line.equals("/debug")) {
                debug = !debug;
                nlu.setDebug(debug);
                out.println("debug: " + (debug ? "on" : "off"));
                continue;
            }
            if (line.equals("/state")) { out.println(DIAGNOSTIC_GSON.toJson(state)); continue; }
            try {
                List<UserAct> acts;
                if (line.startsWith("/inject ")) acts = UserActJson.parse(line.substring(8));
                else if (line.startsWith("/")) throw new IOException("Unknown CLI command.");
                else acts = nlu.interpret(line);
                if (debug) out.println("[debug] acts: " + GSON.toJson(acts));
                accept(core.handleInput(state, acts));
            } catch (IOException | IllegalArgumentException e) {
                out.println("Input error: " + e.getMessage());
            }
        }
    }

    private void accept(Turn turn) {
        state = turn.state();
        for (Turn.Output output : turn.outputs()) {
            if (output.isUtter()) render(output.act);
            else launch(output);
        }
    }

    private void launch(Turn.Output flow) {
        out.println("Requesting flow: " + flow.flowId);
        app.start(flow.flowId, event -> onEvent(flow, event));
    }

    private void onEvent(Turn.Output flow, AppPort.Event event) {
        Turn turn = core.onFlowEvent(state, flow.agentId, event);
        state = turn.state();
        out.println("flow: " + event.kind + " " + flow.flowId);
        for (SysAct act : turn.utters()) render(act);
    }

    private void render(SysAct act) {
        switch (act.type()) {
            case GREET:
                out.println("Hello!");
                break;
            case REQUEST:
                out.println(act.has("name") ? "What is your name?" : "Which flow would you like?");
                break;
            case SELECT:
                out.println("Which flow would you like?");
                Map<String, List<String>> offered = act.slots();
                if (offered.size() == 1) {
                    List<String> choices = act.values(offered.keySet().iterator().next());
                    for (int i = 0; i < choices.size(); i++) {
                        out.println((i + 1) + ". " + flowName(choices.get(i)) + " [" + choices.get(i) + "]");
                    }
                }
                break;
            case CONFIRM:
                out.println(act.has("task") ? "Would you like to see a demo?"
                        : "Did you mean " + flowName(act.slot("flow_id")) + "?");
                break;
            case INFORM:
                if (act.slot("description") != null) out.println(act.slot("description"));
                else if (act.slot("execution_status") != null) {
                    out.println(flowName(act.slot("flow_id")) + ": " + act.slot("execution_status"));
                } else out.println(act.slots());
                break;
            default:
                out.println(act.slots());
                break;
        }
    }

    private String flowName(String id) {
        for (Feature feature : catalog) if (feature.id.equals(id)) return feature.name;
        return id;
    }
}
