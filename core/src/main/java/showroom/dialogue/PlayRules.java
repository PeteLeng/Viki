package showroom.dialogue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Input and advancement rules for the singleton Play goal. */
final class PlayRules {
    static boolean admit(TurnContext c, Task play, Map<String, String> arguments) {
        if (!applyValues(c, play, arguments, true)) return false;
        if (play.progress == Task.Progress.COMPLETED || play.progress == Task.Progress.FAILED) {
            play.progress = Task.Progress.PENDING;
            play.startCommand = null;
            c.note("play.retry", "An explicit request reopens the existing Play task.");
        }
        return true;
    }

    static boolean handleInput(TurnContext c, Task play, DialogueAct input) {
        if (input.type == DialogueAct.Type.INFORM) return applyValues(c, play, input.slots, false);
        PendingInteraction q = c.questionFor(play);
        if (q != null && q.question.type == DialogueAct.Type.CONFIRM
                && q.question.slots.containsKey("flow_id")) {
            if (input.type == DialogueAct.Type.AFFIRM) {
                c.note("play.accept_value", "Accept the proposed flow, not a new goal.");
                return applyValues(c, play, q.question.slots, false);
            }
            if (input.type == DialogueAct.Type.DENY) {
                c.clearQuestion(play);
                c.note("play.reject_value", "Keep the goal and ask for a replacement.");
                c.ask(PendingInteraction.requestValue(play, "flow_id"));
                return true;
            }
        }
        if (q != null && q.question.type == DialogueAct.Type.SELECT && input.type == DialogueAct.Type.AFFIRM) {
            c.note("play.unresolved_affirm", "Yes does not identify a choice; repeat the same offered order.");
            c.ask(q);
            return true;
        }
        return c.unresolved("unresolved_answer", "There is no applicable confirmation for that answer.");
    }

    /** Validate a complete request's arguments before assigning any of them. */
    private static boolean applyValues(TurnContext c, Task play, Map<String, String> supplied, boolean request) {
        Map<String, String> next = new LinkedHashMap<>(play.parameters);
        for (Map.Entry<String, String> entry : supplied.entrySet()) {
            String key = entry.getKey();
            if (!key.equals("flow_id") && !key.equals("part") && !key.equals("theme") && !key.equals("choice")) {
                return c.unresolved("unsupported_input", "Unsupported Play argument: " + key);
            }
            if (!key.equals("choice")) next.put(key, entry.getValue());
        }
        boolean explicitFlow = supplied.containsKey("flow_id") || supplied.containsKey("choice");
        if (supplied.containsKey("choice")) {
            if (supplied.containsKey("flow_id")) return c.unresolved("unsupported_input", "Supply a flow ID or a choice, not both.");
            PendingInteraction q = c.questionFor(play);
            int ordinal;
            try { ordinal = Integer.parseInt(supplied.get("choice")); }
            catch (NumberFormatException e) { return c.unresolved("unresolved_reference", "A choice needs a one-based integer."); }
            if (q == null || q.question.type != DialogueAct.Type.SELECT
                    || ordinal < 1 || ordinal > q.question.choices.size()) {
                return c.unresolved("unresolved_reference", "That position has no value in an outstanding selection.");
            }
            String id = q.question.choices.get(ordinal - 1);
            next.put("flow_id", id);
            c.note("play.resolve_choice", "Ordinal " + ordinal + " -> " + id);
        }
        boolean filtersChanged = !Objects.equals(next.get("part"), play.parameter("part"))
                || !Objects.equals(next.get("theme"), play.parameter("theme"));
        String flowId = next.get("flow_id");
        if (flowId != null) {
            Feature flow = c.flow(flowId);
            if (flow == null) return c.unresolved("unknown_flow", "Unknown flow_id: " + flowId);
            if (!flow.matches(next.get("part"), next.get("theme"))) {
                if (explicitFlow) return c.unresolved("conflicting_values", "The flow conflicts with the supplied part/theme.");
                next.remove("flow_id");
            }
        }
        if (!next.equals(play.parameters) && (play.executionOutstanding()
                || (!request && play.progress != Task.Progress.PENDING))) {
            return c.unresolved("unsupported_input", "Changing issued Play work needs a new request after execution ends.");
        }
        play.parameters.clear();
        play.parameters.putAll(next);
        if (explicitFlow || filtersChanged) c.clearQuestion(play);
        c.note("play.values", play.id + " " + play.parameters);
        return true;
    }

    static void advance(TurnContext c, Task play) {
        if (play.progress != Task.Progress.PENDING) {
            c.note("play.wait", "No new start; progress=" + play.progress);
            return;
        }
        if (play.parameter("flow_id") != null) {
            c.start(play);
            return;
        }
        if (c.questionFor(play) != null) return;
        List<String> matches = new ArrayList<>();
        for (Feature flow : c.catalog) {
            if (flow.matches(play.parameter("part"), play.parameter("theme"))) matches.add(flow.id);
        }
        if (matches.isEmpty()) {
            c.unresolved("no_matches", "No flows match. Try another part or theme.");
        } else if (matches.size() == 1) {
            c.note("play.confirm_flow", "One catalog match; propose it before accepting the value.");
            c.ask(PendingInteraction.confirmValue(play, "flow_id", matches.get(0)));
        } else {
            c.note("play.select_flow", "Offer matching flows in catalog order.");
            c.ask(PendingInteraction.selectValue(play, "flow_id", matches));
        }
    }
}
