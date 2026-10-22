package showroom.dialogue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One outstanding question and its owner. Its issued act is the sole proposal/choice payload. */
public final class PendingInteraction {
    public final String ownerTaskId;
    public final DialogueAct question;

    private PendingInteraction(Task owner, DialogueAct question) {
        this.ownerTaskId = owner.id;
        this.question = question;
    }

    public static PendingInteraction requestValue(Task owner, String slot) {
        return new PendingInteraction(owner, DialogueAct.single(DialogueAct.Type.REQUEST, "slot", slot));
    }

    public static PendingInteraction selectValue(Task owner, String slot, List<String> choices) {
        return new PendingInteraction(owner, DialogueAct.select(slot, choices));
    }

    public static PendingInteraction confirmValue(Task owner, String slot, String value) {
        return new PendingInteraction(owner, DialogueAct.single(DialogueAct.Type.CONFIRM, slot, value));
    }

    public static PendingInteraction confirmGoal(Task owner, Task.Kind goal, Map<String, String> arguments) {
        Map<String, String> slots = new LinkedHashMap<>(arguments);
        slots.put("task", goal.name().toLowerCase(Locale.ROOT));
        return new PendingInteraction(owner, new DialogueAct(DialogueAct.Type.CONFIRM, slots));
    }
}
