package showroom.dialogue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** The meaning of one expression in the current turn, not a state update. */
public final class Act {
    public enum Type { REQUEST, INFORM, SELECT, AFFIRM, DENY }

    public final Type type;
    public final Map<String, String> slots;

    public Act(Type type, Map<String, String> slots) {
        this.type = Objects.requireNonNull(type);
        this.slots = Collections.unmodifiableMap(new LinkedHashMap<>(slots));
        for (Map.Entry<String, String> slot : slots.entrySet()) {
            if (slot.getKey() == null || slot.getValue() == null || slot.getValue().trim().isEmpty()) {
                throw new IllegalArgumentException("Slots need a name and a nonempty string value.");
            }
        }
    }

    public static Act of(String type, Map<String, String> slots) {
        return new Act(Type.valueOf(type.toUpperCase(Locale.ROOT)), slots);
    }

    public static Act request(String operation) { return single(Type.REQUEST, "operation", operation); }
    public static Act inform(String parameter, String value) { return single(Type.INFORM, parameter, value); }
    public static Act select(int ordinal) { return single(Type.SELECT, "ordinal", Integer.toString(ordinal)); }
    public static Act affirm() { return new Act(Type.AFFIRM, Collections.emptyMap()); }
    public static Act deny() { return new Act(Type.DENY, Collections.emptyMap()); }

    private static Act single(Type type, String name, String value) {
        return new Act(type, Collections.singletonMap(name, value));
    }
}
