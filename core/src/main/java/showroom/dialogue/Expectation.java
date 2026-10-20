package showroom.dialogue;

import java.util.Collections;
import java.util.List;

/** Context established by a question, rather than inferred from a null parameter. */
public final class Expectation {
    public enum Kind { CHOICE, CONFIRMATION, VALUE }

    public final Kind kind;
    public final String parameter;
    public final List<Feature> choices;
    public final String proposedValue;

    private Expectation(Kind kind, String parameter, List<Feature> choices, String proposedValue) {
        this.kind = kind;
        this.parameter = parameter;
        this.choices = choices;
        this.proposedValue = proposedValue;
    }

    public static Expectation choice(List<Feature> choices) {
        return new Expectation(Kind.CHOICE, "feature_id", Feature.immutable(choices), null);
    }

    public static Expectation confirmation(String parameter, String proposedValue) {
        return new Expectation(Kind.CONFIRMATION, parameter, Collections.emptyList(), proposedValue);
    }

    public static Expectation value(String parameter) {
        return new Expectation(Kind.VALUE, parameter, Collections.emptyList(), null);
    }
}
