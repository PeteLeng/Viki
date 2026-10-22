package showroom.dialogue;

/** Application request, distinct from speech. The listener captures this exact command. */
public final class FlowCommand {
    public enum Kind { START }

    public final Kind kind;
    public final String taskId;
    public final String flowId;

    private FlowCommand(Task owner) {
        kind = Kind.START;
        taskId = owner.id;
        flowId = owner.parameter("flow_id");
    }

    static FlowCommand start(Task owner) { return new FlowCommand(owner); }
}
