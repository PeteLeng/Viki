package showroom.ndialog.bridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Catalog data supplied by the application; no dialogue policy lives here. */
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
        return (part == null || part.equals("any") || parts.contains(part))
                && (theme == null || theme.equals("any") || themes.contains(theme));
    }

    public static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }
}
