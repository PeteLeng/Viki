package showroom.ndialog.dialogue;

import showroom.ndialog.agent.Agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An act from the system side: what the engine speaks. The question family
 * carries exactly one slot whose value list is the cardinality — empty asks,
 * a singleton proposes, a list offers — and it is born owned by the agent that
 * asks; translate() turns an answer into the bindable form, or null when the
 * answer does not resolve this question. Informs state descriptions, errors,
 * and flow events — any number of single values, no owner.
 */
public final class SysAct {
    public enum Type { GREET, REQUEST, SELECT, CONFIRM, INFORM }

    private final Type type;
    private final Map<String, List<String>> slots;
    private final String ownerId;

    SysAct(Type type, Map<String, List<String>> slots, String ownerId) {
        if (type == null) throw new IllegalArgumentException("An act needs a type.");
        Map<String, List<String>> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> slot : slots.entrySet()) {
            if (slot.getKey() == null || slot.getKey().trim().isEmpty()) {
                throw new IllegalArgumentException("Slots need a name.");
            }
            List<String> values = new ArrayList<>();
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
        this.ownerId = ownerId;
    }

    public Type type() { return type; }
    public Map<String, List<String>> slots() { return slots; }

    /** True when the agent passed is the one that asked this question; informs have no owner. */
    public boolean ownedBy(Agent agent) { return agent != null && ownerId != null && agent.id().equals(ownerId); }

    /** The single value of a slot; null when the slot is absent, unset, or offered. */
    public String slot(String name) {
        List<String> values = slots.get(name);
        if (values == null || values.isEmpty()) return null;
        if (values.size() > 1) throw new IllegalStateException("Slot " + name + " carries multiple values.");
        return values.get(0);
    }

    /** The offered values of a slot; empty when absent or unset. */
    public List<String> values(String name) {
        List<String> values = slots.get(name);
        return values == null ? Collections.<String>emptyList() : values;
    }

    public boolean has(String name) { return slots.containsKey(name); }

    /** The one slot this question is about; null unless there is exactly one. */
    private String askedSlot() {
        if (slots.size() != 1) return null;
        return slots.keySet().iterator().next();
    }

    public static SysAct greet() {
        return new SysAct(Type.GREET, Collections.emptyMap(), null);
    }

    /** A question asking for one slot's value, open-ended. */
    public static SysAct request(Agent owner, String slot) {
        return new SysAct(Type.REQUEST, Collections.singletonMap(slot, Collections.emptyList()), owner.id());
    }

    /** A question offering a list to choose from. */
    public static SysAct select(Agent owner, String slot, List<String> offered) {
        if (offered.isEmpty()) throw new IllegalArgumentException("A selection needs choices.");
        return new SysAct(Type.SELECT, Collections.singletonMap(slot, offered), owner.id());
    }

    /** A question proposing a value; confirm("task", t) proposes the flow goal. */
    public static SysAct confirm(Agent owner, String slot, String value) {
        return new SysAct(Type.CONFIRM, Collections.singletonMap(slot, Collections.singletonList(value)), owner.id());
    }

    /** States one named value. */
    public static SysAct inform(String slot, String value) {
        return new SysAct(Type.INFORM, Collections.singletonMap(slot, Collections.singletonList(value)), null);
    }

    /** States several values: errors and flow events. */
    public static SysAct inform(Map<String, String> slots) {
        Map<String, List<String>> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> slot : slots.entrySet()) {
            normalized.put(slot.getKey(), Collections.singletonList(slot.getValue()));
        }
        return new SysAct(Type.INFORM, normalized, null);
    }

    /**
     * The question family's answer semantics: an open question turns an answer
     * act into the bindable form, or null when the act is not this question's
     * answer. A deny translates into a clear; affirming a goal offer translates
     * into a task request.
     */
    public UserAct translate(UserAct incoming) {
        switch (type) {
            case REQUEST: return translateRequest(incoming);
            case SELECT: return translateSelect(incoming);
            case CONFIRM: return translateConfirm(incoming);
            default: return null;
        }
    }

    private UserAct translateRequest(UserAct incoming) {
        String asked = askedSlot();
        if (asked == null) return null;
        if (incoming.type() == UserAct.Type.INFORM && incoming.has(asked)) return incoming;
        return null;
    }

    private UserAct translateSelect(UserAct incoming) {
        String slot = askedSlot();
        if (slot == null) return null;
        List<String> offered = slots.get(slot);
        if (incoming.type() == UserAct.Type.INFORM && incoming.has("choice")) {
            int ordinal;
            try { ordinal = Integer.parseInt(incoming.slot("choice")); }
            catch (NumberFormatException e) { return null; }
            if (ordinal < 1 || ordinal > offered.size()) return null;
            return UserAct.inform(slot, offered.get(ordinal - 1));
        }
        if (incoming.type() == UserAct.Type.INFORM && incoming.has(slot)
                && offered.contains(incoming.slot(slot))) {
            return incoming;
        }
        return null;
    }

    private UserAct translateConfirm(UserAct incoming) {
        String slot = askedSlot();
        if (slot == null) return null;
        if (incoming.type() == UserAct.Type.AFFIRM) {
            if (slot.equals("task")) return UserAct.request(slot(slot), Collections.<String, String>emptyMap());
            return UserAct.inform(slot, slot(slot));
        }
        if (incoming.type() == UserAct.Type.DENY) {
            return UserAct.clear(slot);
        }
        return null;
    }

    @Override public String toString() { return type + slots.toString(); }
}
