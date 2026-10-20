package showroom.dialogue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Applies interpreted turns and records issued actions; does not select actions. */
final class StateUpdate {
    static Map<String, Binding.TaskBinding> apply(DialogueState state, Binding.Result bound, List<Feature> catalog) {
        Task focused = state.focused();
        Task requested = null;
        if (bound.request != null) {
            if (focused != null && bound.relation == null && focused.kind != bound.request) {
                throw new IllegalArgumentException("Should the new request be an aside or replace the current goal?");
            }
            if ("replace".equals(bound.relation) && focused != null && focused.demoOutstanding) {
                throw new IllegalArgumentException("Replacing an outstanding demo is not implemented.");
            }
            requested = focused == null || bound.relation != null ? new Task(bound.request) : focused;
        }

        Map<String, Binding.TaskBinding> updates = new LinkedHashMap<>();
        for (Binding.TaskBinding binding : bound.tasks.values()) merge(updates, binding.taskId, binding);
        if (requested != null) {
            merge(updates, requested.id, bound.requested);
            bound.details.add("Requested " + bound.request + " -> Task " + requested.id);
        }
        for (Binding.TaskBinding binding : updates.values()) {
            Task task = requested != null && requested.id.equals(binding.taskId) ? requested : state.find(binding.taskId);
            if (task == null) throw new IllegalArgumentException("The bound Task is no longer available.");
            applyValues(task, binding, catalog);
        }

        if (requested != null) {
            if (requested == focused) {
                // An explicit request can repeat a previously completed demonstration.
                if (!requested.demoOutstanding) requested.demoRequest = null;
            } else {
                if ("replace".equals(bound.relation) && focused != null) state.tasks.remove(state.tasks.size() - 1);
                state.tasks.add(requested);
            }
        }
        return updates;
    }

    private static void merge(Map<String, Binding.TaskBinding> updates, String taskId, Binding.TaskBinding source) {
        Binding.TaskBinding target = updates.computeIfAbsent(taskId, Binding.TaskBinding::new);
        for (Map.Entry<String, String> value : source.values.entrySet()) Binding.put(target, value.getKey(), value.getValue());
        target.affirmed |= source.affirmed;
        target.denied |= source.denied;
        if (target.affirmed && target.denied) {
            throw new IllegalArgumentException("Both affirm and deny for the same Task need clarification.");
        }
    }

    private static void applyValues(Task task, Binding.TaskBinding bound, List<Feature> catalog) {
        boolean filtersChanged = false;
        for (Map.Entry<String, String> value : bound.values.entrySet()) {
            if (value.getKey().equals("part") && !value.getValue().equals(task.part)) {
                task.part = value.getValue();
                filtersChanged = true;
            }
            if (value.getKey().equals("theme") && !value.getValue().equals(task.theme)) {
                task.theme = value.getValue();
                filtersChanged = true;
            }
        }
        if (filtersChanged) {
            task.expectation = null;
            task.choices = Collections.emptyList();
            if (task.featureId != null) {
                Feature selected = find(catalog, task.featureId);
                if (selected == null || !selected.matches(task.part, task.theme)) task.featureId = null;
            }
        }
        if (bound.denied && task.expectation != null
                && task.expectation.kind == Expectation.Kind.CONFIRMATION) task.expectation = null;
        if (bound.values.containsKey("feature_id")) task.featureId = bound.values.get("feature_id");
        if (task.expectation != null && bound.values.containsKey(task.expectation.parameter)) {
            task.expectation = null;
        }
    }

    /** Returns whether completing an explanatory reply restored a saved Task. */
    static boolean record(DialogueState state, List<SystemAction> actions) {
        boolean resumed = false;
        for (SystemAction action : actions) {
            Task task = action.taskId == null ? null : state.find(action.taskId);
            if (task == null) continue;
            if (action.kind == SystemAction.Kind.ASK) {
                task.expectation = action.expectation;
                if (action.expectation.kind == Expectation.Kind.CHOICE) task.choices = action.choices;
            } else if (action.kind == SystemAction.Kind.PRESENT_FEATURES) {
                task.choices = action.choices;
            } else if (action.kind == SystemAction.Kind.START_DEMO) {
                task.demoRequest = action;
                task.demoOutstanding = true;
            } else if (action.kind == SystemAction.Kind.REPLY && task.kind == Task.Kind.EXPLAIN
                    && task.featureId != null && task == state.focused() && state.tasks.size() > 1) {
                // A Task-addressed explanation reply fulfills the aside; log identifiers play no role.
                state.tasks.remove(state.tasks.size() - 1);
                resumed = true;
            }
        }
        return resumed;
    }

    static boolean applyFlow(DialogueState state, SystemAction request, Flow.Event event) {
        Task owner = request == null ? null : state.find(request.taskId);
        if (owner == null || owner.demoRequest != request || !owner.demoOutstanding) return false;
        if (event.kind.equals("completed") || event.kind.equals("failed")) owner.demoOutstanding = false;
        return true;
    }

    static Feature find(List<Feature> catalog, String id) {
        for (Feature feature : catalog) if (feature.id.equals(id)) return feature;
        return null;
    }
}
