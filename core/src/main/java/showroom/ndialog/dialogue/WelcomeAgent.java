package showroom.ndialog.dialogue;

import showroom.ndialog.nlu.Segment;

/** Greets, collects the session name, offers the flow goal, then stays below flow for name corrections. */
final class WelcomeAgent extends Agent {
    WelcomeAgent() { super(Agent.newId()); }

    private WelcomeAgent(String id) { super(id); }

    @Override Bind bind(Segment segment, Turn turn) {
        String name = null;
        if (segment.key.equals("wakeup") || segment.key.equals("value")) name = segment.slots.get("name");
        if (name == null) return Bind.NOT_MINE;
        turn.state.name = name;
        return Bind.BOUND;
    }

    @Override void execute(Turn turn) { }

    @Override void onFocus(Turn turn) {
        if (turn.state.name == null) turn.ask(OpenQuestion.request(this, "name"));
        else turn.ask(OpenQuestion.confirmGoal(this, "flow", java.util.Collections.<String, String>emptyMap()));
    }

    @Override void rejected(Turn turn, OpenQuestion question) {
        if (question.kind != OpenQuestion.Kind.CONFIRM_GOAL) return;
        progress = Progress.DONE;
        turn.say(DialogueAct.inform("description", "Okay. Ask me to play a flow whenever you like."));
    }

    @Override Agent copy() { return new WelcomeAgent(id); }
}
