package showroom.dialogue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Catalog data supplied by flow.list(); no per-feature dialogue policy. */
public final class Feature {
    public final String id;
    public final String name;
    public final List<String> aliases;
    public final List<String> parts;
    public final List<String> themes;
    public final String description;

    public Feature(String id, String name, List<String> aliases, List<String> parts,
                   List<String> themes, String description) {
        if (id == null || id.trim().isEmpty() || name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("A feature needs an ID and name.");
        }
        this.id = id;
        this.name = name;
        this.aliases = immutable(aliases);
        this.parts = immutable(parts);
        this.themes = immutable(themes);
        this.description = description;
    }

    public boolean matches(String part, String theme) {
        return (part == null || parts.contains(part)) && (theme == null || themes.contains(theme));
    }

    public boolean named(String reference) {
        String normalized = reference.toLowerCase(Locale.ROOT).trim();
        if (id.equalsIgnoreCase(normalized) || name.equalsIgnoreCase(normalized)) return true;
        for (String alias : aliases) if (alias.equalsIgnoreCase(normalized)) return true;
        return false;
    }

    static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
