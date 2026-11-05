package showroom.cli;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import showroom.ndialog.bridge.Feature;

/**
 * Slot-value normalization for hydra predictions: known aliases canonicalize,
 * unrecognized text passes through verbatim, and the engine's validation
 * produces the user feedback.
 */
final class HydraValues {
    private static final Map<String, String> PARTS = new LinkedHashMap<String, String>();
    private static final Map<String, String> THEMES = new LinkedHashMap<String, String>();
    private static final String[] ORDINALS =
            {"first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth"};
    private static final String[] NUMBERS = {"two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"};
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    static {
        PARTS.put("inside", "inside");
        PARTS.put("interior", "inside");
        PARTS.put("cabin", "inside");
        PARTS.put("车内", "inside");
        PARTS.put("内饰", "inside");
        PARTS.put("座舱里", "inside");
        PARTS.put("outside", "outside");
        PARTS.put("exterior", "outside");
        PARTS.put("车外", "outside");
        PARTS.put("外饰", "outside");

        THEMES.put("family", "family");
        THEMES.put("family-friendly", "family");
        THEMES.put("families", "family");
        THEMES.put("家庭", "family");
        THEMES.put("全家", "family");
        THEMES.put("家用", "family");
        THEMES.put("entertainment", "entertainment");
        THEMES.put("娱乐", "entertainment");
        THEMES.put("privacy", "privacy");
        THEMES.put("隐私", "privacy");
        THEMES.put("私密", "privacy");
    }

    private HydraValues() { }

    /** The flow id for an extracted feature reference, or the text itself when no catalog entry matches. */
    static String flow(String text, List<Feature> catalog) {
        String trimmed = text.trim();
        String lowered = trimmed.toLowerCase(Locale.ROOT);
        for (Feature feature : catalog) {
            if (matches(lowered, feature.id) || matches(lowered, feature.name)) return feature.id;
            for (String alias : feature.aliases) {
                if (matches(lowered, alias)) return feature.id;
            }
        }
        return trimmed;
    }

    static String part(String text) { return canonical(PARTS, text); }

    static String theme(String text) { return canonical(THEMES, text); }

    /** The one-based ordinal for an offered-list selection, or the text itself when unparseable. */
    static String choice(String text) {
        String trimmed = text.trim();
        String lowered = trimmed.toLowerCase(Locale.ROOT);
        if (lowered.equals("one")) return "1";
        Matcher digits = DIGITS.matcher(lowered);
        if (digits.find()) return digits.group();
        for (int i = 0; i < ORDINALS.length; i++) {
            if (containsWord(lowered, ORDINALS[i])) return Integer.toString(i + 1);
        }
        for (int i = 0; i < NUMBERS.length; i++) {
            if (containsWord(lowered, NUMBERS[i])) return Integer.toString(i + 2);
        }
        return trimmed;
    }

    private static String canonical(Map<String, String> table, String text) {
        String trimmed = text.trim();
        String mapped = table.get(trimmed.toLowerCase(Locale.ROOT));
        return mapped != null ? mapped : trimmed;
    }

    private static boolean matches(String lowered, String value) {
        return lowered.equals(value.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean containsWord(String lowered, String word) {
        return (" " + lowered + " ").contains(" " + word + " ");
    }
}
