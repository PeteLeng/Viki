package showroom.ndialog.dialogue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An act from the user side: what an NLU adapter or typed input hands the engine.
 * Slots are flat key-value pairs — input carries single values, and a null value
 * means the slot is being unset (the deny translation's target). An NLU adapter
 * constructs its acts with the prediction's confidence; the engine does not use it.
 */
public final class UserAct {
    public enum Type { GREET, REQUEST, INFORM, AFFIRM, DENY }

    private final Type type;
    private final Map<String, String> slots;
    private final Double confidence;

    public UserAct(Type type, Map<String, String> slots, Double confidence) {
        if (type == null) throw new IllegalArgumentException("An act needs a type.");
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> slot : slots.entrySet()) {
            if (slot.getKey() == null || slot.getKey().trim().isEmpty()) {
                throw new IllegalArgumentException("Slots need a name.");
            }
            if (slot.getValue() != null && slot.getValue().trim().isEmpty()) {
                throw new IllegalArgumentException("Slot values need to be nonempty strings.");
            }
            normalized.put(slot.getKey().trim(), slot.getValue() == null ? null : slot.getValue().trim());
        }
        this.type = type;
        this.slots = Collections.unmodifiableMap(normalized);
        this.confidence = confidence;
    }

    public Type type() { return type; }
    public Map<String, String> slots() { return slots; }
    public Double confidence() { return confidence; }

    /** The value of a slot; null when the slot is absent or unset. */
    public String slot(String name) { return slots.get(name); }

    public boolean has(String name) { return slots.containsKey(name); }

    /** True when the slot is present but unset — the state a deny translation leaves behind. */
    public boolean cleared(String name) { return slots.containsKey(name) && slots.get(name) == null; }

    public static UserAct inform(String slot, String value) {
        return new UserAct(Type.INFORM, Collections.singletonMap(slot, value), null);
    }

    /** A task request with its arguments: request(task=flow, flow_id=..., part=..., theme=...). */
    public static UserAct request(String task, Map<String, String> arguments) {
        if (arguments.containsKey("task")) {
            throw new IllegalArgumentException("The task argument carries its own slot.");
        }
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("task", task);
        slots.putAll(arguments);
        return new UserAct(Type.REQUEST, slots, null);
    }

    /** The deny translation: set the slot back to unset. */
    static UserAct clear(String slot) {
        return new UserAct(Type.INFORM, Collections.singletonMap(slot, null), null);
    }

    @Override public String toString() { return type + slots.toString(); }
}
