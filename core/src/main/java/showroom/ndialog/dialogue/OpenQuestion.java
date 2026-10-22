package showroom.ndialog.dialogue;

import showroom.ndialog.agent.Agent;

import java.util.List;

/**
 * Bookkeeping for the one open question: its owner and the expected act. Translates
 * an answer into an act the ordinary routing can bind, or null when the incoming
 * act is not this question's answer. A deny translates into a clear; affirming a
 * goal offer translates into a goal request.
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

    /** The translated act, or null when the incoming act is not this question's answer. */
    Act translate(Act incoming) {
        switch (expected.type) {
            case REQUEST: return translateRequest(incoming);
            case SELECT: return translateSelect(incoming);
            case CONFIRM: return translateConfirm(incoming);
            default: return null;
        }
    }

    private Act translateRequest(Act incoming) {
        String asked = expected.soleSlot();
        if (incoming.type == Act.Type.INFORM && incoming.slots.size() == 1 && incoming.has(asked)) {
            return incoming;
        }
        return null;
    }

    private Act translateSelect(Act incoming) {
        String slot = expected.soleSlot();
        List<String> choices = expected.values(slot);
        if (incoming.type == Act.Type.INFORM && incoming.has("choice")) {
            int ordinal;
            try { ordinal = Integer.parseInt(incoming.slot("choice")); }
            catch (NumberFormatException e) { return null; }
            if (ordinal < 1 || ordinal > choices.size()) return null;
            return Act.inform(slot, choices.get(ordinal - 1));
        }
        if (incoming.type == Act.Type.INFORM && incoming.has(slot) && choices.contains(incoming.slot(slot))) {
            return incoming;
        }
        return null;
    }

    private Act translateConfirm(Act incoming) {
        String slot = expected.soleSlot();
        if (incoming.type == Act.Type.AFFIRM) {
            if (slot.equals("task")) return Act.goal(expected.slot(slot));
            return Act.inform(slot, expected.slot(slot));
        }
        if (incoming.type == Act.Type.DENY) {
            if (slot.equals("task")) return Act.clear("task");
            return Act.clear(slot);
        }
        return null;
    }
}
