package showroom.ndialog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The engine's one open question, owned by the focused agent. It adapts answer
 * segments into value segments the ordinary routing can bind.
 */
final class OpenQuestion {
    enum Kind { REQUEST, SELECT, CONFIRM_VALUE, CONFIRM_GOAL }

    final Kind kind;
    final String ownerId;
    final String slot;
    final String proposed;
    final List<String> choices;
    final Map<String, String> goalArguments;

    private OpenQuestion(Kind kind, String ownerId, String slot, String proposed,
                         List<String> choices, Map<String, String> goalArguments) {
        this.kind = kind;
        this.ownerId = ownerId;
        this.slot = slot;
        this.proposed = proposed;
        this.choices = choices;
        this.goalArguments = goalArguments;
    }

    static OpenQuestion request(Agent owner, String slot) {
        return new OpenQuestion(Kind.REQUEST, owner.id, slot, null,
                Collections.<String>emptyList(), Collections.<String, String>emptyMap());
    }

    static OpenQuestion select(Agent owner, String slot, List<String> choices) {
        if (choices.isEmpty()) throw new IllegalArgumentException("A selection needs choices.");
        return new OpenQuestion(Kind.SELECT, owner.id, slot, null,
                Feature.immutable(choices), Collections.<String, String>emptyMap());
    }

    static OpenQuestion confirmValue(Agent owner, String slot, String value) {
        return new OpenQuestion(Kind.CONFIRM_VALUE, owner.id, slot, value,
                Collections.<String>emptyList(), Collections.<String, String>emptyMap());
    }

    static OpenQuestion confirmGoal(Agent owner, String goal, Map<String, String> arguments) {
        return new OpenQuestion(Kind.CONFIRM_GOAL, owner.id, goal, null,
                Collections.<String>emptyList(), new LinkedHashMap<String, String>(arguments));
    }

    boolean ownedBy(Agent agent) { return agent != null && ownerId.equals(agent.id); }

    DialogueAct act() {
        switch (kind) {
            case REQUEST: return DialogueAct.request(slot);
            case SELECT: return DialogueAct.select(slot, choices);
            case CONFIRM_VALUE: return DialogueAct.confirm(slot, proposed);
            default: return DialogueAct.confirmGoal(slot);
        }
    }

    /** Translate an answer segment; MISS leaves ordinary routing to handle it. */
    Outcome adapt(Segment input) {
        switch (kind) {
            case REQUEST:
                if (input.key.equals("value") && input.slots.size() == 1 && input.slots.containsKey(slot)) {
                    return Outcome.translated(input);
                }
                return Outcome.MISS;
            case SELECT:
                return adaptSelect(input);
            case CONFIRM_VALUE:
                if (input.key.equals("yes")) return Outcome.translated(Segment.value(slot, proposed));
                if (input.key.equals("no")) return Outcome.rejected();
                return Outcome.MISS;
            default:
                if (input.key.equals("yes")) return Outcome.translated(new Segment(slot, goalArguments));
                if (input.key.equals("no")) return Outcome.rejected();
                return Outcome.MISS;
        }
    }

    private Outcome adaptSelect(Segment input) {
        if (input.key.equals("choice")) {
            String raw = input.slots.get("n");
            int ordinal;
            try { ordinal = Integer.parseInt(raw); }
            catch (NumberFormatException e) {
                return Outcome.error("unresolved_reference", "A choice needs a one-based integer.");
            }
            if (ordinal < 1 || ordinal > choices.size()) {
                return Outcome.error("unresolved_reference", "That position has no value in the offered selection.");
            }
            return Outcome.translated(Segment.value(slot, choices.get(ordinal - 1)));
        }
        if (input.key.equals("value") && input.slots.containsKey(slot)
                && choices.contains(input.slots.get(slot))) {
            return Outcome.translated(input);
        }
        if (input.key.equals("yes")) return Outcome.repeated();
        return Outcome.MISS;
    }

    static final class Outcome {
        enum Kind { TRANSLATED, REJECTED, REPEATED, ERROR, MISS }

        final Kind kind;
        final Segment translated;
        final String code;
        final String description;

        private Outcome(Kind kind, Segment translated, String code, String description) {
            this.kind = kind;
            this.translated = translated;
            this.code = code;
            this.description = description;
        }

        static final Outcome MISS = new Outcome(Kind.MISS, null, null, null);

        static Outcome translated(Segment segment) { return new Outcome(Kind.TRANSLATED, segment, null, null); }
        static Outcome rejected() { return new Outcome(Kind.REJECTED, null, null, null); }
        static Outcome repeated() { return new Outcome(Kind.REPEATED, null, null, null); }
        static Outcome error(String code, String description) { return new Outcome(Kind.ERROR, null, code, description); }
    }
}
