package showroom.ndialog.dialogue;

import showroom.ndialog.agent.Agent;
import showroom.ndialog.agent.FlowAgent;
import showroom.ndialog.agent.WelcomeAgent;
import showroom.ndialog.bridge.AppPort;
import showroom.ndialog.bridge.Feature;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The dialogue engine. Per act: the open question translates an answer into a
 * directly bindable act, the stack binds nearest-first, else a goal agent spawns;
 * then the focus executes, finished agents leave the stack, and the focus takes
 * initiative. Flow notifications bypass this pipeline.
 */
public final class NDialogCore {
    private static final Set<String> GLOBAL_TASKS = new HashSet<String>(
            Arrays.asList("stop", "resume", "quit", "help", "list"));

    private final List<Feature> catalog;

    public NDialogCore(List<Feature> catalog) {
        Set<String> ids = new HashSet<String>();
        for (Feature feature : catalog) {
            if (!ids.add(feature.id)) throw new IllegalArgumentException("Duplicate flow ID: " + feature.id);
        }
        this.catalog = Feature.immutable(catalog);
    }

    /** Acts are nonempty and validated upstream. Returns the next state; its acts and starts are this turn's output. */
    public DialogState handleUserTurn(DialogState before, List<Act> acts) {
        DialogState state = before.copy();
        for (Act act : acts) {
            if (act.type == Act.Type.REQUEST && GLOBAL_TASKS.contains(act.slot("task"))) {
                state.error("unsupported_input", "The '" + act.slot("task") + "' control is not implemented in this slice.");
                continue;
            }

            // The open question is bookkeeping: translate an answer into a bindable act.
            Act bindable = act;
            OpenQuestion question = state.openQuestion;
            if (question != null) {
                OpenQuestion.Outcome outcome = question.translate(act);
                if (outcome.kind != OpenQuestion.Outcome.Kind.MISS) {
                    if (outcome.kind == OpenQuestion.Outcome.Kind.REPEATED) {
                        state.say(question.expected);
                        continue;
                    }
                    if (outcome.kind == OpenQuestion.Outcome.Kind.ERROR) {
                        state.error(outcome.code, outcome.description);
                        continue;
                    }
                    state.openQuestion = null;
                    bindable = outcome.translated;
                }
            }

            // Bind nearest agent first; spawning a goal agent is the fallback.
            if (bindable != null) {
                boolean bound = false;
                for (int i = state.stack.size() - 1; i >= 0; i--) {
                    if (state.stack.get(i).bind(bindable, state) != Agent.Bind.NOT_MINE) {
                        bound = true;
                        break;
                    }
                }
                if (!bound) {
                    if (bindable.type == Act.Type.GREET) {
                        state.say(Act.greet());
                        if (state.stack.isEmpty()) state.stack.add(new WelcomeAgent());
                    } else if (bindable.type == Act.Type.REQUEST && "flow".equals(bindable.slot("task"))) {
                        FlowAgent candidate = new FlowAgent(catalog);
                        if (candidate.bind(bindable, state) == Agent.Bind.BOUND) state.stack.add(candidate);
                    } else {
                        state.error("unresolved_input", "No agent wants that. Greet or request a flow first.");
                    }
                }
            }

            // The question belongs to the focus; then execute, take initiative, clean up.
            question = state.openQuestion;
            Agent focus = state.focused();
            if (question != null && (focus == null || !question.ownedBy(focus))) state.openQuestion = null;
            if (focus != null && !focus.finished()) focus.execute(state);
            focus = state.focused();
            if (focus != null && !focus.finished() && state.openQuestion == null) focus.onFocus(state);
            cleanup(state);
        }
        return state;
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
            if (!slots.isEmpty()) working.say(Act.inform(slots));
        }
        cleanup(working);
        return working;
    }

    private String flowIdOf(Agent owner) {
        return owner instanceof FlowAgent ? ((FlowAgent) owner).flowId() : null;
    }
}
