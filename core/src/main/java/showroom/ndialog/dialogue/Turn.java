package showroom.ndialog.dialogue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One engine turn: the next state and the ordered output it produced. Output
 * events are either utterances or flow start requests, in emission order.
 */
public final class Turn {
    private final DialogState state;
    private final List<Output> outputs;

    Turn(DialogState state, List<Output> outputs) {
        this.state = state;
        this.outputs = Collections.unmodifiableList(new ArrayList<>(outputs));
    }

    public DialogState state() { return state; }
    public List<Output> outputs() { return outputs; }

    /** The uttered system acts, in order. */
    public List<SysAct> utters() {
        List<SysAct> acts = new ArrayList<>();
        for (Output output : outputs) if (output.isUtter()) acts.add(output.act);
        return acts;
    }

    /** The flow start requests, in order. */
    public List<Output> flows() {
        List<Output> flows = new ArrayList<>();
        for (Output output : outputs) if (!output.isUtter()) flows.add(output);
        return flows;
    }

    /** One output event: something uttered, or a flow asked to start. */
    public static final class Output {
        public enum Kind { UTTER, FLOW }

        public final Kind kind;
        public final SysAct act;
        public final String agentId;
        public final String flowId;

        private Output(Kind kind, SysAct act, String agentId, String flowId) {
            this.kind = kind;
            this.act = act;
            this.agentId = agentId;
            this.flowId = flowId;
        }

        static Output utter(SysAct act) { return new Output(Kind.UTTER, act, null, null); }
        static Output flow(String agentId, String flowId) { return new Output(Kind.FLOW, null, agentId, flowId); }

        public boolean isUtter() { return kind == Kind.UTTER; }
    }
}
