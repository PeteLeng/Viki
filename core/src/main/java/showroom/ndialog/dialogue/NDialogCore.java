package showroom.ndialog.dialogue;

import showroom.ndialog.agent.Agent;
import showroom.ndialog.agent.FlowAgent;
import showroom.ndialog.agent.WelcomeAgent;
import showroom.ndialog.bridge.AppPort;
import showroom.ndialog.bridge.Feature;
import showroom.ndialog.nlu.Segment;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The dialogue engine. Per segment: absorb through the open question, else bind by
 * stack walk, else spawn; then refocus, execute, clean up, and let the focus take
 * initiative. Flow notifications bypass this pipeline.
 */
public final class NDialogCore {
    private static final Set<String> GLOBAL_KEYS = new HashSet<String>(
            Arrays.asList("stop", "resume", "quit", "help", "list"));

    private final List<Feature> catalog;

    public NDialogCore(List<Feature> catalog) {
        Set<String> ids = new HashSet<String>();
        for (Feature feature : catalog) {
            if (!ids.add(feature.id)) throw new IllegalArgumentException("Duplicate flow ID: " + feature.id);
        }
        this.catalog = Feature.immutable(catalog);
    }

    /** Returns the next state; its acts and starts are this turn's output. */
    public DialogState handleUserTurn(DialogState before, List<Segment> segments) {
        DialogState working = before.copy();
        if (segments == null || segments.isEmpty()) {
            working.error("unsupported_input", "Supply at least one segment.");
            return working;
        }
        for (Segment segment : segments) {
            if (segment == null) {
                working.error("unsupported_input", "Segments cannot be null.");
                continue;
            }
            if (GLOBAL_KEYS.contains(segment.key)) {
                working.error("unsupported_input", "The '" + segment.key + "' control is not implemented in this slice.");
                continue;
            }
            process(working, segment);
        }
        return working;
    }

    private void process(DialogState state, Segment segment) {
        OpenQuestion question = state.openQuestion;
        if (question != null) {
            OpenQuestion.Outcome outcome = question.adapt(segment);
            if (outcome.kind != OpenQuestion.Outcome.Kind.MISS) {
                apply(state, question, outcome);
                advance(state);
                return;
            }
        }
        route(state, segment);
        advance(state);
    }

    private void apply(DialogState state, OpenQuestion question, OpenQuestion.Outcome outcome) {
        switch (outcome.kind) {
            case TRANSLATED:
                state.openQuestion = null;
                Agent owner = state.find(question.ownerId);
                if (owner != null) owner.resolved(state, question);
                route(state, outcome.translated);
                break;
            case REJECTED:
                state.openQuestion = null;
                Agent rejectOwner = state.find(question.ownerId);
                if (rejectOwner != null) rejectOwner.rejected(state, question);
                break;
            case REPEATED:
                state.say(question.act());
                break;
            case ERROR:
                state.error(outcome.code, outcome.description);
                break;
            default:
                break;
        }
    }

    /** Nearest agent first; spawning is the fallback when nobody binds the segment. */
    private void route(DialogState state, Segment segment) {
        for (int i = state.stack.size() - 1; i >= 0; i--) {
            if (state.stack.get(i).bind(segment, state) != Agent.Bind.NOT_MINE) return;
        }
        spawn(state, segment);
    }

    private void spawn(DialogState state, Segment segment) {
        if (segment.key.equals("wakeup")) {
            state.say(DialogueAct.greet());
            if (!state.stack.isEmpty()) return;
            WelcomeAgent candidate = new WelcomeAgent();
            candidate.bind(segment, state);
            state.stack.add(candidate);
            return;
        }
        if (segment.key.equals("flow")) {
            FlowAgent candidate = new FlowAgent(catalog);
            if (candidate.bind(segment, state) == Agent.Bind.BOUND) state.stack.add(candidate);
            return;
        }
        state.error("unresolved_input", "No agent wants '" + segment.key + "'. Greet or request a flow first.");
    }

    private void advance(DialogState state) {
        refocus(state);
        executeFocus(state);
        cleanup(state);
        onFocus(state);
    }

    /** The open question always belongs to the focus; a focus shift clears it. */
    private void refocus(DialogState state) {
        OpenQuestion question = state.openQuestion;
        if (question != null) {
            Agent focus = state.focused();
            if (focus == null || !question.ownedBy(focus)) state.openQuestion = null;
        }
    }

    private void executeFocus(DialogState state) {
        Agent focus = state.focused();
        if (focus != null && !focus.finished()) focus.execute(state);
    }

    private void cleanup(DialogState state) {
        for (int i = state.stack.size() - 1; i >= 0; i--) {
            Agent agent = state.stack.get(i);
            if (agent.finished()) {
                state.clearQuestion(agent);
                state.stack.remove(i);
            }
        }
    }

    private void onFocus(DialogState state) {
        Agent focus = state.focused();
        if (focus == null || focus.finished() || state.openQuestion != null) return;
        focus.onFocus(state);
    }

    /** Application notifications update a running agent's status; they never rerun input policy. */
    public DialogState onFlowEvent(DialogState before, String agentId, AppPort.Event event) {
        DialogState working = before.copy();
        Agent owner = working.find(agentId);
        if (owner != null && owner.running()) {
            if (event.kind.equals("completed")) owner.complete();
            else if (event.kind.equals("failed")) owner.fail();
            Map<String, String> slots = new LinkedHashMap<String, String>();
            if (event.kind.equals("running") || event.kind.equals("completed") || event.kind.equals("failed")) {
                slots.put("flow_id", flowIdOf(owner));
                slots.put("execution_status", event.kind);
            }
            if (event.text != null && !event.text.trim().isEmpty()) slots.put("description", event.text);
            if (!slots.isEmpty()) working.say(new DialogueAct(DialogueAct.Type.INFORM, slots));
        }
        cleanup(working);
        return working;
    }

    private String flowIdOf(Agent owner) {
        return owner instanceof FlowAgent ? ((FlowAgent) owner).flowId() : null;
    }
}
