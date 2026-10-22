package showroom.ndialog.dialogue;

import showroom.ndialog.agent.Agent;

import java.util.List;

/**
 * Bookkeeping for the one open question: its owner and the expected act. It
 * translates answers into acts the ordinary routing can bind. A deny translates
 * into a clear; affirming a goal offer translates into a goal request.
 */
public final class OpenQuestion {
    final String ownerId;
    final Act expected;

    private OpenQuestion(Agent owner, Act expected) {
        this.ownerId = owner.id();
        this.expected = expected;
    }

    public static OpenQuestion requestValue(Agent owner, String slot) {
        return new OpenQuestion(owner, Act.request(slot));
    }

    public static OpenQuestion selectValue(Agent owner, String slot, List<String> choices) {
        return new OpenQuestion(owner, Act.select(slot, choices));
    }

    public static OpenQuestion confirmValue(Agent owner, String slot, String value) {
        return new OpenQuestion(owner, Act.confirm(slot, value));
    }

    public static OpenQuestion confirmGoal(Agent owner, String task) {
        return new OpenQuestion(owner, Act.confirmGoal(task));
    }

    boolean ownedBy(Agent agent) { return agent != null && ownerId.equals(agent.id()); }

    /** Translate an answer act; MISS leaves ordinary routing to handle it. */
    Outcome translate(Act incoming) {
        switch (expected.type) {
            case REQUEST: return translateRequest(incoming);
            case SELECT: return translateSelect(incoming);
            case CONFIRM: return translateConfirm(incoming);
            default: return Outcome.MISS;
        }
    }

    private Outcome translateRequest(Act incoming) {
        String asked = expected.soleSlot();
        if (incoming.type == Act.Type.INFORM && incoming.slots.size() == 1 && incoming.has(asked)) {
            return Outcome.translated(incoming);
        }
        return Outcome.MISS;
    }

    private Outcome translateSelect(Act incoming) {
        String slot = expected.soleSlot();
        List<String> choices = expected.values(slot);
        if (incoming.type == Act.Type.INFORM && incoming.has("choice")) {
            int ordinal;
            try { ordinal = Integer.parseInt(incoming.slot("choice")); }
            catch (NumberFormatException e) {
                return Outcome.error("unresolved_reference", "A choice needs a one-based integer.");
            }
            if (ordinal < 1 || ordinal > choices.size()) {
                return Outcome.error("unresolved_reference", "That position has no value in the offered selection.");
            }
            return Outcome.translated(Act.inform(slot, choices.get(ordinal - 1)));
        }
        if (incoming.type == Act.Type.INFORM && incoming.has(slot) && choices.contains(incoming.slot(slot))) {
            return Outcome.translated(incoming);
        }
        if (incoming.type == Act.Type.AFFIRM) return Outcome.repeated();
        return Outcome.MISS;
    }

    private Outcome translateConfirm(Act incoming) {
        String slot = expected.soleSlot();
        if (incoming.type == Act.Type.AFFIRM) {
            if (slot.equals("task")) return Outcome.translated(Act.goal(expected.slot(slot)));
            return Outcome.translated(Act.inform(slot, expected.slot(slot)));
        }
        if (incoming.type == Act.Type.DENY) return Outcome.translated(Act.clear(slot));
        return Outcome.MISS;
    }

    static final class Outcome {
        enum Kind { TRANSLATED, REPEATED, ERROR, MISS }

        final Kind kind;
        final Act translated;
        final String code;
        final String description;

        private Outcome(Kind kind, Act translated, String code, String description) {
            this.kind = kind;
            this.translated = translated;
            this.code = code;
            this.description = description;
        }

        static final Outcome MISS = new Outcome(Kind.MISS, null, null, null);

        static Outcome translated(Act act) { return new Outcome(Kind.TRANSLATED, act, null, null); }
        static Outcome repeated() { return new Outcome(Kind.REPEATED, null, null, null); }
        static Outcome error(String code, String description) { return new Outcome(Kind.ERROR, null, code, description); }
    }
}
