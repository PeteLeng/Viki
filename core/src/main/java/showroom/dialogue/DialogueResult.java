package showroom.dialogue;

import java.util.List;

public final class DialogueResult {
    public final DialogueState state;
    public final List<SystemAction> actions;
    public final Trace trace;

    DialogueResult(DialogueState state, List<SystemAction> actions, Trace trace) {
        this.state = state;
        this.actions = Feature.immutable(actions);
        this.trace = trace;
    }

    public static final class Trace {
        public final Object input;
        public final List<String> binding;
        public final DialogueState before;
        public final DialogueState after;
        public final String rule;
        public final String reason;
        public final List<SystemAction> actions;
        public final long elapsedNanos;

        Trace(Object input, List<String> binding, DialogueState before, DialogueState after,
              String rule, String reason, List<SystemAction> actions, long elapsedNanos) {
            this.input = input;
            this.binding = Feature.immutable(binding);
            this.before = before;
            this.after = after;
            this.rule = rule;
            this.reason = reason;
            this.actions = Feature.immutable(actions);
            this.elapsedNanos = elapsedNanos;
        }
    }
}
