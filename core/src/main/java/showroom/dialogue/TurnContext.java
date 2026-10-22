package showroom.dialogue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Temporary arguments and bookkeeping helpers for one call. No I/O or persistent event queue. */
final class TurnContext {
    final DialogueState state;
    final List<Feature> catalog;
    final List<DialogueAct> acts = new ArrayList<>();
    final List<FlowCommand> commands = new ArrayList<>();
    final List<String> steps = new ArrayList<>();

    TurnContext(DialogueState state, List<Feature> catalog) {
        this.state = state;
        this.catalog = catalog;
    }

    /** Both a direct request and an accepted offer enter here. Admission precedes handoff. */
    boolean activateTask(Task.Kind kind, Map<String, String> arguments) {
        Task task = state.find(kind);
        boolean fresh = task == null;
        if (fresh) task = new Task(kind);
        if (kind == Task.Kind.PLAY) {
            if (!PlayRules.admit(this, task, arguments)) return false;
            Task welcome = state.find(Task.Kind.WELCOME);
            if (welcome != null) completeTask(welcome);
        }
        if (fresh) state.tasks.add(task);
        else {
            state.tasks.remove(task);
            state.tasks.add(task);
        }
        note("task.admit", (fresh ? "Created " : "Reused ") + kind + " " + task.id);
        return true;
    }

    void completeTask(Task task) {
        clearQuestion(task);
        state.tasks.remove(task);
        note("task.complete", task.kind + " " + task.id);
    }

    PendingInteraction questionFor(Task task) {
        PendingInteraction q = state.pendingInteraction;
        return q != null && q.ownerTaskId.equals(task.id) ? q : null;
    }

    void clearQuestion(Task task) {
        if (questionFor(task) != null) {
            state.pendingInteraction = null;
            note("interaction.clear", task.id);
        }
    }

    void ask(PendingInteraction question) {
        if (state.find(question.ownerTaskId) == null) throw new IllegalStateException("Question owner is missing.");
        state.pendingInteraction = question;
        acts.add(question.question);
        note("interaction.ask", question.ownerTaskId + " " + question.question.type);
    }

    void start(Task play) {
        FlowCommand command = FlowCommand.start(play);
        play.startCommand = command;
        play.progress = Task.Progress.START_REQUESTED;
        commands.add(command);
        note("play.start", command.flowId + " owned by " + play.id);
    }

    Feature flow(String id) {
        for (Feature feature : catalog) if (feature.id.equals(id)) return feature;
        return null;
    }

    /** Ordinary unresolved/unsupported input is an outcome, not an exception. */
    boolean unresolved(String code, String description) {
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("error", code);
        slots.put("description", description);
        acts.add(new DialogueAct(DialogueAct.Type.INFORM, slots));
        note("input." + code, description);
        return false;
    }

    void note(String rule, String detail) { steps.add(rule + ": " + detail); }

    DialogueResult result(DialogueState before, Object input, long started) {
        DialogueResult.Trace trace = new DialogueResult.Trace(input, steps, before, state, acts, commands,
                System.nanoTime() - started);
        return new DialogueResult(state, acts, commands, trace);
    }
}
