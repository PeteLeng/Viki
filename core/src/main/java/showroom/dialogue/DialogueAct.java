package showroom.dialogue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Communicated meaning, shared by user input and system output. No runtime Task IDs. */
public final class DialogueAct {
    public enum Type { GREET, REQUEST, INFORM, CONFIRM, AFFIRM, DENY, SELECT }

    public final Type type;
    public final Map<String, String> slots;
    public final List<String> choices;

    public DialogueAct(Type type, Map<String, String> slots) {
        this(type, slots, Collections.emptyList());
    }

    private DialogueAct(Type type, Map<String, String> slots, List<String> choices) {
        this.type = Objects.requireNonNull(type);
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> slot : slots.entrySet()) {
            if (slot.getKey() == null || slot.getValue() == null || slot.getValue().trim().isEmpty()) {
                throw new IllegalArgumentException("Slots need a name and a nonempty string value.");
            }
            normalized.put(slot.getKey(), slot.getValue().trim());
        }
        this.slots = Collections.unmodifiableMap(normalized);
        this.choices = Feature.immutable(choices);
    }

    public static DialogueAct of(String type, Map<String, String> slots) {
        return new DialogueAct(Type.valueOf(type.toUpperCase(Locale.ROOT)), slots);
    }

    public static DialogueAct greet() { return empty(Type.GREET); }
    public static DialogueAct affirm() { return empty(Type.AFFIRM); }
    public static DialogueAct deny() { return empty(Type.DENY); }
    public static DialogueAct request(String task) { return single(Type.REQUEST, "task", task); }
    public static DialogueAct inform(String slot, String value) { return single(Type.INFORM, slot, value); }

    /** Local wire encoding of inform(choice=ordinal(n)); the core resolves the reference. */
    public static DialogueAct choice(int ordinal) { return inform("choice", Integer.toString(ordinal)); }

    public static DialogueAct select(String slot, List<String> choices) {
        if (choices.isEmpty()) throw new IllegalArgumentException("A selection needs choices.");
        return new DialogueAct(Type.SELECT, Collections.singletonMap("slot", slot), choices);
    }

    static DialogueAct single(Type type, String slot, String value) {
        return new DialogueAct(type, Collections.singletonMap(slot, value));
    }

    private static DialogueAct empty(Type type) { return new DialogueAct(type, Collections.emptyMap()); }
}
