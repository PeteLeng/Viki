package showroom.ndialog;

import java.util.UUID;

/** One conversational goal on the agent stack. Subclasses own their fields, binding, and questions. */
public abstract class Agent {
    public enum Progress { IDLE, RUNNING, DONE, FAILED }

    /** Outcome of offering a segment to an agent. */
    enum Bind { NOT_MINE, BOUND, REJECTED }

    final String id;
    Progress progress = Progress.IDLE;

    protected Agent(String id) { this.id = id; }

    /**
     * Absorb a segment's values. NOT_MINE leaves the walk untouched; BOUND applies
     * values (an error act is emitted for REJECTED, which stops the walk without values).
     */
    abstract Bind bind(Segment segment, Turn turn);

    /** Perform the application side effect when ready; request starts through the turn. */
    abstract void execute(Turn turn);

    /** Take dialogue initiative when focused and nothing is open. */
    abstract void onFocus(Turn turn);

    /** The user rejected this agent's pending proposal. */
    abstract void rejected(Turn turn, OpenQuestion question);

    /** The open question was resolved by acceptance or by a translated answer. */
    void resolved(Turn turn, OpenQuestion question) { }

    abstract Agent copy();

    public final String id() { return id; }
    public final Progress progress() { return progress; }
    public String kind() { return getClass().getSimpleName(); }

    final boolean finished() { return progress == Progress.DONE || progress == Progress.FAILED; }
    final boolean running() { return progress == Progress.RUNNING; }

    static String newId() { return UUID.randomUUID().toString(); }
}
