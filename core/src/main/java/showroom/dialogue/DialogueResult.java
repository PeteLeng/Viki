package showroom.dialogue;

import java.util.List;

public final class DialogueResult {
    public final DialogueState state;
    public final List<DialogueAct> acts;
    public final List<FlowCommand> commands;
    public final Trace trace;

    DialogueResult(DialogueState state, List<DialogueAct> acts, List<FlowCommand> commands, Trace trace) {
        this.state = state;
        this.acts = Feature.immutable(acts);
        this.commands = Feature.immutable(commands);
        this.trace = trace;
    }

    public static final class Trace {
        public final Object input;
        public final List<String> steps;
        public final DialogueState before;
        public final DialogueState after;
        public final List<DialogueAct> acts;
        public final List<FlowCommand> commands;
        public final long elapsedNanos;

        Trace(Object input, List<String> steps, DialogueState before, DialogueState after,
              List<DialogueAct> acts, List<FlowCommand> commands, long elapsedNanos) {
            this.input = input;
            this.steps = Feature.immutable(steps);
            this.before = before;
            this.after = after;
            this.acts = Feature.immutable(acts);
            this.commands = Feature.immutable(commands);
            this.elapsedNanos = elapsedNanos;
        }
    }
}
