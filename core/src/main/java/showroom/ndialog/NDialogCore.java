package showroom.ndialog;

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

    public Result handleUserTurn(DialogState before, List<Segment> segments) {
        DialogState working = before.copy();
        Turn turn = new Turn(working);
        if (segments == null || segments.isEmpty()) {
            turn.error("unsupported_input", "Supply at least one segment.");
            return result(turn);
        }
        for (Segment segment : segments) {
            if (segment == null) {
                turn.error("unsupported_input", "Segments cannot be null.");
                continue;
            }
            if (GLOBAL_KEYS.contains(segment.key)) {
                turn.error("unsupported_input", "The '" + segment.key + "' control is not implemented in this slice.");
                continue;
            }
            process(turn, segment);
        }
        return result(turn);
    }

    private void process(Turn turn, Segment segment) {
        OpenQuestion question = turn.state.openQuestion;
        if (question != null) {
            OpenQuestion.Outcome outcome = question.adapt(segment);
            if (outcome.kind != OpenQuestion.Outcome.Kind.MISS) {
                apply(turn, question, outcome);
                advance(turn);
                return;
            }
        }
        route(turn, segment);
        advance(turn);
    }

    private void apply(Turn turn, OpenQuestion question, OpenQuestion.Outcome outcome) {
        switch (outcome.kind) {
            case TRANSLATED:
                turn.state.openQuestion = null;
                Agent owner = turn.state.find(question.ownerId);
                if (owner != null) owner.resolved(turn, question);
                route(turn, outcome.translated);
                break;
            case REJECTED:
                turn.state.openQuestion = null;
                Agent rejectOwner = turn.state.find(question.ownerId);
                if (rejectOwner != null) rejectOwner.rejected(turn, question);
                break;
            case REPEATED:
                turn.say(question.act());
                break;
            case ERROR:
                turn.error(outcome.code, outcome.description);
                break;
            default:
                break;
        }
    }

    /** Nearest agent first; spawning is the fallback when nobody binds the segment. */
    private void route(Turn turn, Segment segment) {
        for (int i = turn.state.stack.size() - 1; i >= 0; i--) {
            if (turn.state.stack.get(i).bind(segment, turn) != Agent.Bind.NOT_MINE) return;
        }
        spawn(turn, segment);
    }

    private void spawn(Turn turn, Segment segment) {
        if (segment.key.equals("wakeup")) {
            turn.say(DialogueAct.greet());
            if (!turn.state.stack.isEmpty()) return;
            WelcomeAgent candidate = new WelcomeAgent();
            candidate.bind(segment, turn);
            turn.spawn(candidate);
            return;
        }
        if (segment.key.equals("flow")) {
            FlowAgent candidate = new FlowAgent(catalog);
            if (candidate.bind(segment, turn) == Agent.Bind.BOUND) turn.spawn(candidate);
            return;
        }
        turn.error("unresolved_input", "No agent wants '" + segment.key + "'. Greet or request a flow first.");
    }

    private void advance(Turn turn) {
        refocus(turn);
        executeFocus(turn);
        cleanup(turn);
        onFocus(turn);
    }

    /** Spawned agents enter now, so execution and initiative already see the new focus. */
    private void refocus(Turn turn) {
        for (Agent agent : turn.spawned) turn.state.stack.add(agent);
        turn.spawned.clear();
        OpenQuestion question = turn.state.openQuestion;
        if (question != null) {
            Agent focus = turn.state.focused();
            if (focus == null || !question.ownedBy(focus)) turn.state.openQuestion = null;
        }
    }

    private void executeFocus(Turn turn) {
        Agent focus = turn.state.focused();
        if (focus != null && !focus.finished()) focus.execute(turn);
    }

    private void cleanup(Turn turn) {
        for (int i = turn.state.stack.size() - 1; i >= 0; i--) {
            Agent agent = turn.state.stack.get(i);
            if (agent.finished()) {
                turn.clearQuestion(agent);
                turn.state.stack.remove(i);
            }
        }
    }

    private void onFocus(Turn turn) {
        Agent focus = turn.state.focused();
        if (focus == null || focus.finished() || turn.state.openQuestion != null) return;
        focus.onFocus(turn);
    }

    /** Application notifications update a running agent's progress; they never rerun input policy. */
    public Result onFlowEvent(DialogState before, String agentId, AppPort.Event event) {
        DialogState working = before.copy();
        Turn turn = new Turn(working);
        Agent owner = working.find(agentId);
        if (owner != null && owner.running()) {
            if (event.kind.equals("completed")) owner.progress = Agent.Progress.DONE;
            else if (event.kind.equals("failed")) owner.progress = Agent.Progress.FAILED;
            Map<String, String> slots = new LinkedHashMap<String, String>();
            if (event.kind.equals("running") || event.kind.equals("completed") || event.kind.equals("failed")) {
                slots.put("flow_id", flowIdOf(owner));
                slots.put("execution_status", event.kind);
            }
            if (event.text != null && !event.text.trim().isEmpty()) slots.put("description", event.text);
            if (!slots.isEmpty()) turn.say(new DialogueAct(DialogueAct.Type.INFORM, slots));
        }
        cleanup(turn);
        return result(turn);
    }

    private String flowIdOf(Agent owner) {
        return owner instanceof FlowAgent ? ((FlowAgent) owner).flowId() : null;
    }

    private Result result(Turn turn) {
        return new Result(turn.state, turn.acts, turn.starts);
    }
}
