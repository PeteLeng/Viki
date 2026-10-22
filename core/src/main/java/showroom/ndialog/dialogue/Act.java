package showroom.ndialog.dialogue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One unit of language meaning, shared by input and output. Slot values are lists:
 * a singleton is a value, an empty list is unset, and multiple values are offered
 * choices (system questions only; input carries singletons).
 */
public final class Act {
    public enum Type { GREET, REQUEST, INFORM, CONFIRM, AFFIRM, DENY, SELECT }

    public final Type type;
    public final Map<String, List<String>> slots;

    public Act(Type type, Map<String, List<String>> slots) {
        if (type == null) throw new IllegalArgumentException("An act needs a type.");
        Map<String, List<String>> normalized = new LinkedHashMap<String, List<String>>();
        for (Map.Entry<String, List<String>> slot : slots.entrySet()) {
            if (slot.getKey() == null || slot.getKey().trim().isEmpty()) {
                throw new IllegalArgumentException("Slots need a name.");
            }
            List<String> values = new ArrayList<String>();
            if (slot.getValue() != null) {
                for (String value : slot.getValue()) {
                    if (value == null || value.trim().isEmpty()) {
                        throw new IllegalArgumentException("Slot values need to be nonempty strings.");
                    }
                    values.add(value.trim());
                }
            }
            normalized.put(slot.getKey().trim(), Collections.unmodifiableList(values));
        }
        this.type = type;
        this.slots = Collections.unmodifiableMap(normalized);
    }

    /** The single value of a slot; null when the slot is absent or unset. */
    public String slot(String name) {
        List<String> values = slots.get(name);
        if (values == null || values.isEmpty()) return null;
        if (values.size() > 1) throw new IllegalStateException("Slot " + name + " carries multiple values.");
        return values.get(0);
    }

    /** The values of a slot; empty when absent or unset. */
    public List<String> values(String name) {
        List<String> values = slots.get(name);
        return values == null ? Collections.<String>emptyList() : values;
    }

    public boolean has(String name) { return slots.containsKey(name); }

    /** The one slot name of a question or selection act. */
    public String soleSlot() {
        return slots.size() == 1 ? slots.keySet().iterator().next() : null;
    }

    public static Act greet() { return empty(Type.GREET); }
    public static Act affirm() { return empty(Type.AFFIRM); }
    public static Act deny() { return empty(Type.DENY); }

    /** A goal request, as spoken by the user or translated from an accepted goal offer. */
    public static Act goal(String task) { return single(Type.REQUEST, "task", task); }

    /** A system question asking for one slot's value. */
    public static Act request(String slot) {
        return new Act(Type.REQUEST, Collections.<String, List<String>>singletonMap(slot, emptyValues()));
    }

    public static Act inform(String slot, String value) { return single(Type.INFORM, slot, value); }

    public static Act inform(Map<String, String> slots) {
        Map<String, List<String>> normalized = new LinkedHashMap<String, List<String>>();
        for (Map.Entry<String, String> slot : slots.entrySet()) {
            normalized.put(slot.getKey(), singleton(slot.getValue()));
        }
        return new Act(Type.INFORM, normalized);
    }

    /** The deny translation: set the slot back to unset. */
    public static Act clear(String slot) {
        return new Act(Type.INFORM, Collections.<String, List<String>>singletonMap(slot, emptyValues()));
    }

    public static Act confirm(String slot, String value) { return single(Type.CONFIRM, slot, value); }

    public static Act confirmGoal(String task) { return single(Type.CONFIRM, "task", task); }

    public static Act select(String slot, List<String> choices) {
        if (choices.isEmpty()) throw new IllegalArgumentException("A selection needs choices.");
        return new Act(Type.SELECT, Collections.<String, List<String>>singletonMap(slot, immutable(choices)));
    }

    private static Act single(Type type, String slot, String value) {
        return new Act(type, Collections.<String, List<String>>singletonMap(slot, singleton(value)));
    }

    private static Act empty(Type type) {
        return new Act(type, Collections.<String, List<String>>emptyMap());
    }

    private static List<String> singleton(String value) {
        List<String> values = new ArrayList<String>(1);
        values.add(value);
        return values;
    }

    private static List<String> emptyValues() {
        return Collections.emptyList();
    }

    static List<String> immutable(List<String> values) {
        return Collections.unmodifiableList(new ArrayList<String>(values));
    }

    @Override public String toString() { return type + slots.toString(); }
}
