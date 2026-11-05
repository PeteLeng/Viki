package showroom.cli.json;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import showroom.ndialog.dialogue.UserAct;

/**
 * The typed-act input dialect: {"acts":[{"type":"inform","slots":{"name":"Sam"}}]} → UserActs.
 * Two transports speak it: the CLI's /inject command and the opencode interpreter.
 * Binding is lenient the way hydra's is — unknown fields are ignored, numbers coerce,
 * a null slot value is an unset slot — while arrays as slot values and unknown type
 * names are rejected.
 */
public final class UserActJson {
    private static final Gson GSON = new Gson();

    private static final class ActsJson {
        List<ActJson> acts;
    }

    private static final class ActJson {
        String type;
        Map<String, String> slots;
    }

    private UserActJson() { }

    public static List<UserAct> parse(String json) throws IOException {
        try {
            ActsJson parsed = GSON.fromJson(json, ActsJson.class);
            if (parsed == null || parsed.acts == null || parsed.acts.isEmpty()) {
                throw new IllegalArgumentException("At least one act is required.");
            }
            List<UserAct> acts = new ArrayList<>();
            for (ActJson entry : parsed.acts) {
                acts.add(act(type(entry), slots(entry)));
            }
            return acts;
        } catch (RuntimeException e) {
            throw new IOException("Invalid act JSON: " + e.getMessage(), e);
        }
    }

    /** The input grammar, shape-checked, then built explicitly per type. */
    private static UserAct act(UserAct.Type type, Map<String, String> slots) {
        switch (type) {
            case GREET:
            case AFFIRM:
            case DENY:
                if (!slots.isEmpty()) throw new IllegalArgumentException(type + " takes no slots.");
                return new UserAct(type, slots, null);
            case REQUEST: {
                String task = slots.get("task");
                if (task == null) throw new IllegalArgumentException("A request needs a task.");
                Map<String, String> arguments = new LinkedHashMap<>(slots);
                arguments.remove("task");
                return UserAct.request(task, arguments);
            }
            case INFORM: {
                if (slots.size() != 1) throw new IllegalArgumentException("An inform carries one named value.");
                Map.Entry<String, String> sole = slots.entrySet().iterator().next();
                return UserAct.inform(sole.getKey(), sole.getValue());
            }
            default:
                throw new IllegalArgumentException(type + " is not a user act.");
        }
    }

    private static UserAct.Type type(ActJson entry) {
        if (entry.type == null) throw new IllegalArgumentException("An act needs a type.");
        return UserAct.Type.valueOf(entry.type.toUpperCase(java.util.Locale.ROOT));
    }

    private static Map<String, String> slots(ActJson entry) {
        return entry.slots == null ? Collections.emptyMap() : entry.slots;
    }
}
