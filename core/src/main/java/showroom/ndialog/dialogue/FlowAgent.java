package showroom.ndialog.dialogue;

import showroom.ndialog.bridge.Feature;
import showroom.ndialog.nlu.Segment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The flow goal: binds id/part/theme values, narrows the catalog, asks select/confirm, starts execution. */
final class FlowAgent extends Agent {
    private final List<Feature> catalog;
    private String flowId;
    private String part;
    private String theme;

    FlowAgent(List<Feature> catalog) {
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
    @Override Bind bind(Segment segment, Turn turn) {
        boolean goal = segment.key.equals("flow");
        if (!goal && !segment.key.equals("value")) return Bind.NOT_MINE;
        Map<String, String> supplied = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> slot : segment.slots.entrySet()) {
            if (slot.getKey().equals("id") || slot.getKey().equals("part") || slot.getKey().equals("theme")) {
                supplied.put(slot.getKey(), slot.getValue());
            } else if (goal) {
                turn.error("unsupported_input", "Unsupported flow argument: " + slot.getKey());
                return Bind.REJECTED;
            }
        }
        if (supplied.isEmpty() && !goal) return Bind.NOT_MINE;
        return apply(supplied, turn);
    }

    /** Validate a complete set of arguments before assigning any of them. */
    private Bind apply(Map<String, String> supplied, Turn turn) {
        String nextFlow = supplied.containsKey("id") ? supplied.get("id") : flowId;
        String nextPart = supplied.containsKey("part") ? supplied.get("part") : part;
        String nextTheme = supplied.containsKey("theme") ? supplied.get("theme") : theme;
        if (nextFlow != null) {
            Feature flow = feature(nextFlow);
            if (flow == null) {
                turn.error("unknown_flow", "Unknown flow id: " + nextFlow);
                return Bind.REJECTED;
            }
            if (!flow.matches(nextPart, nextTheme)) {
                turn.error("conflicting_values", "The flow conflicts with the supplied part/theme.");
                return Bind.REJECTED;
            }
        }
        boolean changed = !Objects.equals(nextFlow, flowId)
                || !Objects.equals(nextPart, part) || !Objects.equals(nextTheme, theme);
        if (running() && changed) {
            turn.error("unsupported_input", "Changing issued flow work needs a new request after execution ends.");
            return Bind.REJECTED;
        }
        boolean filtersChanged = !Objects.equals(nextPart, part) || !Objects.equals(nextTheme, theme);
        flowId = nextFlow;
        part = nextPart;
        theme = nextTheme;
        if (supplied.containsKey("id") || filtersChanged) turn.clearQuestion(this);
        return Bind.BOUND;
    }

    @Override void execute(Turn turn) {
        if (flowId == null || progress != Progress.IDLE) return;
        progress = Progress.RUNNING;
        turn.start(this, flowId);
    }

    @Override void onFocus(Turn turn) {
        if (flowId != null || progress != Progress.IDLE) return;
        List<String> matches = new ArrayList<String>();
        for (Feature flow : catalog) {
            if (flow.matches(part, theme)) matches.add(flow.id);
        }
        if (matches.isEmpty()) {
            turn.error("no_matches", "No flows match. Try another part or theme.");
        } else if (matches.size() == 1) {
            turn.ask(OpenQuestion.confirmValue(this, "id", matches.get(0)));
        } else {
            turn.ask(OpenQuestion.select(this, "id", matches));
        }
    }

    @Override void rejected(Turn turn, OpenQuestion question) {
        if (question.kind != OpenQuestion.Kind.CONFIRM_VALUE) return;
        turn.ask(OpenQuestion.request(this, "id"));
    }

    @Override Agent copy() {
        FlowAgent copy = new FlowAgent(id, catalog, flowId, part, theme);
        copy.progress = progress;
        return copy;
    }

    String flowId() { return flowId; }

    private Feature feature(String id) {
        for (Feature flow : catalog) if (flow.id.equals(id)) return flow;
        return null;
    }
}
