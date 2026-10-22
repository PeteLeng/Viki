package showroom.dialogue;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One private working state, direct input handlers, then one advancement. No adapter calls. */
public final class DialogueCore {
    private final List<Feature> catalog;

    public DialogueCore(List<Feature> catalog) {
        Set<String> ids = new HashSet<>();
        for (Feature feature : catalog) {
            if (!ids.add(feature.id)) throw new IllegalArgumentException("Duplicate flow ID: " + feature.id);
        }
        this.catalog = Feature.immutable(catalog);
    }

    public DialogueResult handleUserTurn(DialogueState before, List<DialogueAct> input) {
        long started = System.nanoTime();
        TurnContext c = new TurnContext(before.copy(), catalog);
        if (input == null || input.size() != 1 || input.get(0) == null) {
            c.unresolved("unsupported_input", "This slice accepts exactly one user act per turn.");
        } else if (handleInput(c, input.get(0))) {
            advanceFocusedTask(c);
        }
        return c.result(before, input, started);
    }

    private boolean handleInput(TurnContext c, DialogueAct input) {
        switch (input.type) {
            case GREET:
                if (!input.slots.isEmpty()) return c.unresolved("unsupported_input", "A greeting has no arguments.");
                return WelcomeRules.greet(c);
            case REQUEST:
                if (!"play".equals(input.slots.get("task"))) {
                    return c.unresolved("unsupported_input", "This slice implements request(task=play).");
                }
                Map<String, String> arguments = new LinkedHashMap<>(input.slots);
                arguments.remove("task");
                return c.activateTask(Task.Kind.PLAY, arguments);
            case INFORM:
                if (input.slots.size() != 1) return c.unresolved("unsupported_input", "Supply one named value or choice per inform in this slice.");
                break;
            case AFFIRM:
            case DENY:
                if (!input.slots.isEmpty()) return c.unresolved("unsupported_input", "Use an unqualified yes/no in this slice.");
                break;
            default:
                return c.unresolved("unsupported_input", "That user act is outside this slice.");
        }
        Task task = c.state.focused();
        if (task == null) return c.unresolved("unresolved_answer", "Greet or request Play first.");
        return task.kind == Task.Kind.WELCOME
                ? WelcomeRules.handleInput(c, task, input) : PlayRules.handleInput(c, task, input);
    }

    private void advanceFocusedTask(TurnContext c) {
        Task task = c.state.focused();
        if (task == null) return;
        if (task.kind == Task.Kind.WELCOME) WelcomeRules.advance(c, task);
        else PlayRules.advance(c, task);
    }

    /** Notifications use their captured command, never the focused question or the user-input path. */
    public DialogueResult handleFlowEvent(DialogueState before, FlowCommand command, Flow.Event event) {
        long started = System.nanoTime();
        TurnContext c = new TurnContext(before.copy(), catalog);
        Task owner = command == null ? null : c.state.find(command.taskId);
        if (owner == null || owner.startCommand != command || !owner.executionOutstanding()) {
            c.note("flow.ignore", "No outstanding request owns this callback.");
        } else {
            switch (event.kind) {
                case "running": owner.progress = Task.Progress.RUNNING; break;
                case "completed": owner.progress = Task.Progress.COMPLETED; break;
                case "failed": owner.progress = Task.Progress.FAILED; break;
                default: break;
            }
            c.note("flow.observe", owner.id + " " + command.flowId + " " + event.kind);
            Map<String, String> slots = new LinkedHashMap<>();
            if (event.kind.equals("running") || event.kind.equals("completed") || event.kind.equals("failed")) {
                slots.put("flow_id", command.flowId);
                slots.put("execution_status", event.kind);
            }
            if (event.text != null && !event.text.trim().isEmpty()) slots.put("description", event.text);
            if (!slots.isEmpty()) c.acts.add(new DialogueAct(DialogueAct.Type.INFORM, slots));
        }
        return c.result(before, event, started);
    }
}
