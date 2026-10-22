package showroom.ndialog.nlu;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** One semantically coherent unit of user input, produced by the interpreter. */
public final class Segment {
    public final String key;
    public final Map<String, String> slots;

    public Segment(String key, Map<String, String> slots) {
        if (key == null || key.trim().isEmpty()) throw new IllegalArgumentException("A segment needs a key.");
        this.key = key.trim();
        Map<String, String> normalized = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> slot : slots.entrySet()) {
            if (slot.getKey() == null || slot.getValue() == null || slot.getValue().trim().isEmpty()) {
                throw new IllegalArgumentException("Slots need a name and a nonempty string value.");
            }
            normalized.put(slot.getKey(), slot.getValue().trim());
        }
        this.slots = Collections.unmodifiableMap(normalized);
    }

    public static Segment of(String key) {
        return new Segment(key, Collections.<String, String>emptyMap());
    }

    public static Segment value(String slot, String value) {
        return new Segment("value", Collections.singletonMap(slot, value));
    }

    @Override public String toString() { return key + slots; }
}
