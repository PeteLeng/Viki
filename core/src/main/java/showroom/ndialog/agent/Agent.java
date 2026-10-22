package showroom.ndialog.agent;

import showroom.ndialog.dialogue.DialogState;
import showroom.ndialog.dialogue.OpenQuestion;
import showroom.ndialog.nlu.Segment;

import java.util.UUID;

/** One conversational goal on the agent stack. Subclasses own their fields, binding, and questions. */
public abstract class Agent {
    public enum Status { IDLE, RUNNING, DONE, FAILED }

    /** Outcome of offering a segment to an agent. */
    public enum Bind { NOT_MINE, BOUND, REJECTED }

    final String id;
    Status status = Status.IDLE;

    protected Agent(String id) { this.id = id; }

    /**
     * Absorb a segment's values. NOT_MINE leaves the walk untouched; BOUND applies
     * values (an error act is emitted for REJECTED, which stops the walk without values).
     */
    public abstract Bind bind(Segment segment, DialogState state);

    /** Perform the application side effect when ready; request starts through the state. */
    public abstract void execute(DialogState state);

    /** Take dialogue initiative when focused and nothing is open. */
    public abstract void onFocus(DialogState state);

    /** The user rejected this agent's pending proposal. */
    public abstract void rejected(DialogState state, OpenQuestion question);

    public abstract Agent copy();

    public final String id() { return id; }
    public final Status status() { return status; }
    public String kind() { return getClass().getSimpleName(); }

    public final boolean finished() { return status == Status.DONE || status == Status.FAILED; }
    public final boolean running() { return status == Status.RUNNING; }

    public final void complete() { status = Status.DONE; }
    public final void fail() { status = Status.FAILED; }

    static String newId() { return UUID.randomUUID().toString(); }
}
