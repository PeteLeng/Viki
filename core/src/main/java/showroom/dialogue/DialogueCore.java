package showroom.dialogue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** No Android, networking, rendering, or direct flow API calls. */
public final class DialogueCore {
    private final List<Feature> catalog;

    public DialogueCore(List<Feature> catalog) {
        Set<String> ids = new HashSet<>();
        for (Feature feature : catalog) {
            if (!ids.add(feature.id)) throw new IllegalArgumentException("Duplicate feature ID: " + feature.id);
        }
        this.catalog = Feature.immutable(catalog);
    }

    public DialogueResult handleUserTurn(DialogueState state, List<Act> acts) {
        long started = System.nanoTime();
        DialogueState after = state.copy();
        List<String> details = new ArrayList<>();
        Policy.Decision decision;
        try {
            Binding.Result bound = Binding.bind(state, acts, catalog);
            Map<String, Binding.TaskBinding> updates = StateUpdate.apply(after, bound, catalog);
            details.addAll(bound.details);
            Binding.TaskBinding focused = updates.get(after.focused().id);
            decision = focused == null ? Policy.savedTaskUpdated() : Policy.next(after, catalog, focused);
            String rule = decision.rule;
            String reason = decision.reason;
            List<SystemAction> actions = new ArrayList<>(decision.actions);
            while (StateUpdate.record(after, decision.actions)) {
                decision = Policy.resume(after, catalog, updates.get(after.focused().id));
                reason += " Resumed via " + decision.rule + ": " + decision.reason;
                actions.addAll(decision.actions);
            }
            decision = new Policy.Decision(rule, reason, actions);
        } catch (IllegalArgumentException e) {
            after = state.copy();
            decision = Policy.repair(e.getMessage());
        }
        return result(acts, details, state, after, decision, started);
    }

    /** The originating start action is the callback association; no run registry is needed. */
    public DialogueResult handleFlowEvent(DialogueState before, SystemAction request, Flow.Event event) {
        long started = System.nanoTime();
        DialogueState after = before.copy();
        Policy.Decision decision;
        if (!StateUpdate.applyFlow(after, request, event)) {
            decision = new Policy.Decision("flow.ignore", "The callback no longer belongs to an active request in this context.",
                    Collections.emptyList());
        } else {
            decision = Policy.flow(event);
        }
        return result(event, Collections.singletonList("Callback owner: " + (request == null ? "none" : request.taskId)),
                before, after, decision, started);
    }

    private DialogueResult result(Object input, List<String> details, DialogueState before, DialogueState after,
                                  Policy.Decision decision, long started) {
        DialogueResult.Trace trace = new DialogueResult.Trace(input, details, before, after,
                decision.rule, decision.reason, decision.actions, System.nanoTime() - started);
        return new DialogueResult(after, decision.actions, trace);
    }
}
