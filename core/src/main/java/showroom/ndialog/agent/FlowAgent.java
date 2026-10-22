package showroom.ndialog.agent;

import showroom.ndialog.bridge.Feature;
import showroom.ndialog.dialogue.DialogueAct;
import showroom.ndialog.dialogue.DialogState;
import showroom.ndialog.dialogue.OpenQuestion;
import showroom.ndialog.nlu.Segment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The flow goal: binds id/part/theme values, narrows the catalog, asks select/confirm, starts execution. */
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
     * A goal segment is always this agent's; a value segment is this agent's when it
     * carries id/part/theme. Value segments carry one named value each, so unknown
     * slots belong to other agents.
     */
    @Override public Bind bind(Segment segment, DialogState state) {
        boolean goal = segment.key.equals("flow");
        if (!goal && !segment.key.equals("value")) return Bind.NOT_MINE;
        Map<String, String> supplied = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> slot : segment.slots.entrySet()) {
            if (slot.getKey().equals("id") || slot.getKey().equals("part") || slot.getKey().equals("theme")) {
                supplied.put(slot.getKey(), slot.getValue());
            } else if (goal) {
                state.error("unsupported_input", "Unsupported flow argument: " + slot.getKey());
                return Bind.REJECTED;
            }
        }
        if (supplied.isEmpty() && !goal) return Bind.NOT_MINE;
        return apply(supplied, state);
    }

    /** Validate a complete set of arguments before assigning any of them. */
    private Bind apply(Map<String, String> supplied, DialogState state) {
        String nextFlow = supplied.containsKey("id") ? supplied.get("id") : flowId;
        String nextPart = supplied.containsKey("part") ? supplied.get("part") : part;
        String nextTheme = supplied.containsKey("theme") ? supplied.get("theme") : theme;
        if (nextFlow != null) {
            Feature flow = feature(nextFlow);
            if (flow == null) {
                state.error("unknown_flow", "Unknown flow id: " + nextFlow);
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
        if (supplied.containsKey("id") || filtersChanged) state.clearQuestion(this);
        return Bind.BOUND;
    }

    @Override public void execute(DialogState state) {
        if (flowId == null || status != Status.IDLE) return;
        status = Status.RUNNING;
        state.requestStart(this, flowId);
    }

    @Override public void onFocus(DialogState state) {
        if (flowId != null || status != Status.IDLE) return;
        List<String> matches = new ArrayList<String>();
        for (Feature flow : catalog) {
            if (flow.matches(part, theme)) matches.add(flow.id);
        }
        if (matches.isEmpty()) {
            state.error("no_matches", "No flows match. Try another part or theme.");
        } else if (matches.size() == 1) {
            state.ask(OpenQuestion.confirmValue(this, "id", matches.get(0)));
        } else {
            state.ask(OpenQuestion.select(this, "id", matches));
        }
    }

    @Override public void rejected(DialogState state, OpenQuestion question) {
        if (question.kind != OpenQuestion.Kind.CONFIRM_VALUE) return;
        state.ask(OpenQuestion.request(this, "id"));
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
