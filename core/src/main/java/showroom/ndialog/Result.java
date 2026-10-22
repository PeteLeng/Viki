package showroom.ndialog;

import java.util.List;

/** One engine result: the next state, output acts, and starts the host dispatches after accepting it. */
public final class Result {
    public final DialogState state;
    public final List<DialogueAct> acts;
    public final List<Start> starts;

    Result(DialogState state, List<DialogueAct> acts, List<Start> starts) {
        this.state = state;
        this.acts = Feature.immutable(acts);
        this.starts = Feature.immutable(starts);
    }

    /** A start request captured during execute; the listener keeps the agent association. */
    public static final class Start {
        public final String agentId;
        public final String flowId;

        Start(String agentId, String flowId) {
            this.agentId = agentId;
            this.flowId = flowId;
        }
    }
}
