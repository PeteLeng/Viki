package showroom.ndialog.dialogue;

import showroom.ndialog.bridge.Feature;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** System output vocabulary. User input arrives as segments, never as acts. */
public final class DialogueAct {
    public enum Type { GREET, REQUEST, SELECT, CONFIRM, INFORM }

    public final Type type;
    public final Map<String, String> slots;
    public final List<String> choices;

    public DialogueAct(Type type, Map<String, String> slots) {
        this(type, slots, Collections.<String>emptyList());
    }

    private DialogueAct(Type type, Map<String, String> slots, List<String> choices) {
        this.type = type;
        Map<String, String> normalized = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> slot : slots.entrySet()) {
            if (slot.getKey() == null || slot.getValue() == null || slot.getValue().trim().isEmpty()) {
                throw new IllegalArgumentException("Slots need a name and a nonempty string value.");
            }
            normalized.put(slot.getKey(), slot.getValue().trim());
        }
        this.slots = Collections.unmodifiableMap(normalized);
        this.choices = Feature.immutable(choices);
    }

    public static DialogueAct greet() { return empty(Type.GREET); }

    public static DialogueAct request(String slot) { return single(Type.REQUEST, "slot", slot); }

    public static DialogueAct select(String slot, List<String> choices) {
        if (choices.isEmpty()) throw new IllegalArgumentException("A selection needs choices.");
        return new DialogueAct(Type.SELECT, Collections.singletonMap("slot", slot), choices);
    }

    public static DialogueAct confirm(String slot, String value) { return single(Type.CONFIRM, slot, value); }

    public static DialogueAct confirmGoal(String task) { return single(Type.CONFIRM, "task", task); }

    public static DialogueAct inform(String slot, String value) { return single(Type.INFORM, slot, value); }

    public static DialogueAct inform(Map<String, String> slots) { return new DialogueAct(Type.INFORM, slots); }

    private static DialogueAct single(Type type, String slot, String value) {
        return new DialogueAct(type, Collections.singletonMap(slot, value));
    }

    private static DialogueAct empty(Type type) {
        return new DialogueAct(type, Collections.<String, String>emptyMap());
    }
}
