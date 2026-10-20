package showroom.dialogue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Resolves the whole turn without changing Tasks or calling the app. */
final class Binding {
    /** Values retain their destination; null taskId is the requested goal before state update resolves it. */
    static final class TaskBinding {
        final String taskId;
        final Map<String, String> values = new LinkedHashMap<>();
        boolean affirmed;
        boolean denied;

        TaskBinding(String taskId) { this.taskId = taskId; }
    }

    static final class Result {
        Task.Kind request;
        String relation;
        TaskBinding requested;
        final Map<String, TaskBinding> tasks = new LinkedHashMap<>();
        final List<String> details = new ArrayList<>();

        TaskBinding forTask(Task task) {
            return tasks.computeIfAbsent(task.id, TaskBinding::new);
        }
    }

    static Result bind(DialogueState state, List<Act> acts, List<Feature> catalog) {
        if (acts == null || acts.isEmpty()) throw new IllegalArgumentException("No user acts were supplied.");
        Result result = new Result();
        // A request's own slots belong to its requested goal, not to an existing question.
        for (Act act : acts) {
            if (act.type == Act.Type.REQUEST) {
                if (result.request != null) throw new IllegalArgumentException("Use one requested goal per turn in this slice.");
                String operation = act.slots.get("operation");
                if (operation == null) throw new IllegalArgumentException("A request needs operation.");
                try {
                    result.request = Task.Kind.valueOf(operation.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Supported operations: browse, explain, demonstrate.");
                }
                result.relation = act.slots.get("relation");
                if (result.relation != null && !result.relation.equals("aside") && !result.relation.equals("replace")) {
                    throw new IllegalArgumentException("Request relation must be aside or replace.");
                }
                result.requested = new TaskBinding(null);
                result.details.add("Requested " + result.request + "; relation=" + result.relation);
                for (Map.Entry<String, String> slot : act.slots.entrySet()) {
                    if (slot.getKey().equals("operation") || slot.getKey().equals("relation")) continue;
                    bindSlot(result, result.requested, act.type, slot, state, catalog);
                }
            }
        }

        Set<String> correctionTargets = new LinkedHashSet<>();
        for (Act act : acts) {
            if (act.type == Act.Type.INFORM || act.type == Act.Type.SELECT) {
                if (act.slots.isEmpty()) throw new IllegalArgumentException(act.type + " needs a slot value.");
                for (Map.Entry<String, String> slot : act.slots.entrySet()) {
                    Task owner = context(state, parameter(slot.getKey()), slot.getKey().equals("ordinal"));
                    TaskBinding target = owner == null ? result.requested : result.forTask(owner);
                    if (target == null) throw new IllegalArgumentException("First request browse, explain, or demonstrate.");
                    bindSlot(result, target, act.type, slot, state, catalog);
                    if (owner != null) correctionTargets.add(owner.id);
                }
            }
        }

        for (Act act : acts) {
            if (act.type != Act.Type.AFFIRM) continue;
            if (!act.slots.isEmpty()) throw new IllegalArgumentException("Affirm does not take slots.");
            Task owner = confirmation(state);
            if (owner == null) throw new IllegalArgumentException("There is no confirmation for 'yes' to answer.");
            TaskBinding target = result.forTask(owner);
            target.affirmed = true;
            put(target, owner.expectation.parameter, owner.expectation.proposedValue);
            result.details.add("Affirm -> Task " + owner.id + "." + owner.expectation.parameter
                    + "=" + owner.expectation.proposedValue);
        }

        for (Act act : acts) {
            if (act.type != Act.Type.DENY) continue;
            if (!act.slots.isEmpty()) throw new IllegalArgumentException("Deny does not take slots.");
            if (correctionTargets.size() > 1) {
                throw new IllegalArgumentException("The denial could refer to several Tasks; clarify which answer should change.");
            }
            Task proposed = confirmation(state);
            Task owner = correctionTargets.isEmpty() ? proposed
                    : state.find(correctionTargets.iterator().next());
            if (owner != null && proposed != null && owner != proposed) {
                TaskBinding confirmationAnswer = result.tasks.get(proposed.id);
                if (confirmationAnswer == null || !confirmationAnswer.affirmed) {
                    throw new IllegalArgumentException("The denial could answer another Task's confirmation; clarify which answer should change.");
                }
            }
            if (owner == null) owner = state.focused();
            if (owner == null) throw new IllegalArgumentException("There is no current Task to correct.");
            TaskBinding target = result.forTask(owner);
            if (target.affirmed) throw new IllegalArgumentException("Both affirm and deny for the same Task need clarification.");
            target.denied = true;
            if (target.values.isEmpty()
                    && (owner.expectation == null || owner.expectation.kind != Expectation.Kind.CONFIRMATION)) {
                throw new IllegalArgumentException("What should I change? Supply the corrected value.");
            }
            result.details.add("Deny -> Task " + owner.id + "; interpreted with that Task's supplied values.");
        }
        return result;
    }

    private static void bindSlot(Result result, TaskBinding target, Act.Type type,
                                 Map.Entry<String, String> slot, DialogueState state, List<Feature> catalog) {
        String name = slot.getKey();
        String value = slot.getValue().trim();
        String parameter = parameter(name);
        if (name.equals("ordinal") && (type == Act.Type.SELECT || type == Act.Type.REQUEST)) {
            Task owner = context(state, "feature_id", true);
            int ordinal;
            try { ordinal = Integer.parseInt(value); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("Ordinal must be an integer."); }
            if (owner == null || ordinal < 1 || ordinal > owner.choices.size()) {
                throw new IllegalArgumentException("That ordinal is outside the offered choices.");
            }
            value = owner.choices.get(ordinal - 1).id;
            result.details.add("Ordinal " + ordinal + " -> " + value + " in Task " + owner.id + "'s retained offered order.");
        } else if (name.equals("feature") || name.equals("feature_id")) {
            List<Feature> named = new ArrayList<>();
            for (Feature feature : catalog) if (feature.named(value)) named.add(feature);
            if (named.size() != 1) {
                throw new IllegalArgumentException(named.isEmpty()
                        ? "Unknown feature: " + value : "That feature name is ambiguous; use an offered ID.");
            }
            value = named.get(0).id;
        } else if (!(name.equals("part") || name.equals("theme")) || type == Act.Type.SELECT) {
            throw new IllegalArgumentException("Unsupported slot for " + type + ": " + name);
        }
        put(target, parameter, value);
        String destination = target.taskId == null ? "requested " + result.request : "Task " + target.taskId;
        result.details.add(destination + "." + parameter + "=" + value);
    }

    private static String parameter(String slot) {
        return slot.equals("feature") || slot.equals("ordinal") ? "feature_id" : slot;
    }

    /** Nearest compatible expectation first; an ordinal can also use retained choices. */
    private static Task context(DialogueState state, String parameter, boolean ordinal) {
        for (int i = state.tasks.size() - 1; i >= 0; i--) {
            Task task = state.tasks.get(i);
            if (task.expectation != null && task.expectation.parameter.equals(parameter)
                    && (!ordinal || task.expectation.kind == Expectation.Kind.CHOICE)) return task;
        }
        if (ordinal) {
            for (int i = state.tasks.size() - 1; i >= 0; i--) {
                Task task = state.tasks.get(i);
                if (!task.choices.isEmpty()) return task;
            }
            return null;
        }
        return state.focused();
    }

    private static Task confirmation(DialogueState state) {
        for (int i = state.tasks.size() - 1; i >= 0; i--) {
            Task task = state.tasks.get(i);
            if (task.expectation != null && task.expectation.kind == Expectation.Kind.CONFIRMATION) return task;
        }
        return null;
    }

    static void put(TaskBinding target, String parameter, String value) {
        String prior = target.values.get(parameter);
        if (prior != null && !prior.equals(value)) {
            throw new IllegalArgumentException("Multiple values for " + parameter + " are not supported in this slice.");
        }
        target.values.put(parameter, value);
    }
}
