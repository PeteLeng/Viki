package showroom.dialogue;

import java.util.Collections;
import java.util.List;

/** Policy output. Adapters execute these after their state consequences are recorded. */
public final class SystemAction {
    public enum Kind { REPLY, ASK, PRESENT_FEATURES, START_DEMO }

    public final Kind kind;
    public final String text;
    public final String taskId;
    public final String featureId;
    public final Expectation expectation;
    public final List<Feature> choices;

    private SystemAction(Kind kind, String text, String taskId, String featureId,
                         Expectation expectation, List<Feature> choices) {
        this.kind = kind;
        this.text = text;
        this.taskId = taskId;
        this.featureId = featureId;
        this.expectation = expectation;
        this.choices = choices;
    }

    public static SystemAction reply(String text) {
        return new SystemAction(Kind.REPLY, text, null, null, null, Collections.emptyList());
    }

    public static SystemAction reply(Task task, String text) {
        return new SystemAction(Kind.REPLY, text, task.id, null, null, Collections.emptyList());
    }

    public static SystemAction ask(Task task, String text, Expectation expectation) {
        return new SystemAction(Kind.ASK, text, task.id, null, expectation, expectation.choices);
    }

    public static SystemAction present(Task task, List<Feature> choices) {
        return new SystemAction(Kind.PRESENT_FEATURES, "Available features:", task.id,
                null, null, Feature.immutable(choices));
    }

    public static SystemAction start(Task task) {
        return new SystemAction(Kind.START_DEMO, null, task.id, task.featureId,
                null, Collections.emptyList());
    }
}
