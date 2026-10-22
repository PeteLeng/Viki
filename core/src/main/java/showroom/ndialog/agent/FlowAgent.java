package showroom.ndialog.agent;

import showroom.ndialog.bridge.Feature;
import showroom.ndialog.dialogue.Act;
import showroom.ndialog.dialogue.DialogState;
import showroom.ndialog.dialogue.OpenQuestion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The flow goal: binds flow_id/part/theme values, narrows the catalog, asks
 * select/confirm, starts execution. A cleared flow_id (a denied proposal) goes
 * back to asking.
 */
public final class FlowAgent extends Agent {
    private final List<Feature> catalog;
    private String flowId;
    private String part;
    private String theme;

    public FlowAgent(List<Feature> catalog) {
        super(Agent.newId());
        this.catalog = catalog;
    }

    private FlowAgent(String id, List<Feature> catalog, String flowId, String part, String theme) {
        super(id);
        this.catalog = catalog;
        this.flowId = flowId;
        this.part = part;
        this.theme = theme;
    }

    /**
     * A goal request is always this agent's; an inform is this agent's when it carries
     * flow_id/part/theme. Other slots belong to other agents.
     */
    @Override public Bind bind(Act act, DialogState state) {
        boolean goal = act.type == Act.Type.REQUEST && "flow".equals(act.slot("task"));
        if (!goal && act.type != Act.Type.INFORM) return Bind.NOT_MINE;
        Map<String, String> supplied = new LinkedHashMap<String, String>();
        for (Map.Entry<String, List<String>> entry : act.slots.entrySet()) {
            String key = entry.getKey();
            if (key.equals("task")) continue;
            if (key.equals("flow_id") || key.equals("part") || key.equals("theme")) {
                supplied.put(key, act.slot(key));
            } else if (goal) {
                state.error("unsupported_input", "Unsupported flow argument: " + key);
                return Bind.REJECTED;
            }
        }
        if (supplied.isEmpty() && !goal) return Bind.NOT_MINE;
        return apply(supplied, state);
    }

    /** Validate a complete set of arguments before assigning any of them; a null value clears. */
    private Bind apply(Map<String, String> supplied, DialogState state) {
        String nextFlow = supplied.containsKey("flow_id") ? supplied.get("flow_id") : flowId;
        String nextPart = supplied.containsKey("part") ? supplied.get("part") : part;
        String nextTheme = supplied.containsKey("theme") ? supplied.get("theme") : theme;
        if (nextFlow != null) {
            Feature flow = feature(nextFlow);
            if (flow == null) {
                state.error("unknown_flow", "Unknown flow_id: " + nextFlow);
                return Bind.REJECTED;
            }
            if (!flow.matches(nextPart, nextTheme)) {
                state.error("conflicting_values", "The flow conflicts with the supplied part/theme.");
                return Bind.REJECTED;
            }
        }
        boolean changed = !Objects.equals(nextFlow, flowId)
                || !Objects.equals(nextPart, part) || !Objects.equals(nextTheme, theme);
        if (running() && changed) {
            state.error("unsupported_input", "Changing issued flow work needs a new request after execution ends.");
            return Bind.REJECTED;
        }
        boolean filtersChanged = !Objects.equals(nextPart, part) || !Objects.equals(nextTheme, theme);
        flowId = nextFlow;
        part = nextPart;
        theme = nextTheme;
        if (supplied.containsKey("flow_id") || filtersChanged) state.clearQuestion(this);
        return Bind.BOUND;
    }

    @Override public void execute(DialogState state) {
        if (flowId == null || status != Status.IDLE) return;
        status = Status.RUNNING;
        state.requestStart(this, flowId);
    }

    @Override public void onFocus(DialogState state) {
        if (status != Status.IDLE) return;
        if (flowId != null) {
            execute(state);
            return;
        }
        List<String> matches = new ArrayList<String>();
        for (Feature flow : catalog) {
            if (flow.matches(part, theme)) matches.add(flow.id);
        }
        if (matches.isEmpty()) {
            state.error("no_matches", "No flows match. Try another part or theme.");
        } else if (matches.size() == 1) {
            state.ask(OpenQuestion.confirmValue(this, "flow_id", matches.get(0)));
        } else {
            state.ask(OpenQuestion.selectValue(this, "flow_id", matches));
        }
    }

    @Override public Agent copy() {
        FlowAgent copy = new FlowAgent(id, catalog, flowId, part, theme);
        copy.status = status;
        return copy;
    }

    public String flowId() { return flowId; }

    private Feature feature(String id) {
        for (Feature flow : catalog) if (flow.id.equals(id)) return flow;
        return null;
    }
}
