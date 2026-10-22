package showroom.ndialog.agent;

import showroom.ndialog.dialogue.DialogueAct;
import showroom.ndialog.dialogue.DialogState;
import showroom.ndialog.dialogue.OpenQuestion;
import showroom.ndialog.nlu.Segment;

import java.util.Collections;

/** Greets, collects the session name, offers the flow goal, then stays below flow for name corrections. */
public final class WelcomeAgent extends Agent {
    public WelcomeAgent() { super(Agent.newId()); }

    private WelcomeAgent(String id) { super(id); }

    @Override public Bind bind(Segment segment, DialogState state) {
        String name = null;
        if (segment.key.equals("wakeup") || segment.key.equals("value")) name = segment.slots.get("name");
        if (name == null) return Bind.NOT_MINE;
        state.setName(name);
        return Bind.BOUND;
    }

    @Override public void execute(DialogState state) { }

    @Override public void onFocus(DialogState state) {
        if (state.name() == null) state.ask(OpenQuestion.request(this, "name"));
        else state.ask(OpenQuestion.confirmGoal(this, "flow", Collections.<String, String>emptyMap()));
    }

    @Override public void rejected(DialogState state, OpenQuestion question) {
        if (question.kind != OpenQuestion.Kind.CONFIRM_GOAL) return;
        complete();
        state.say(DialogueAct.inform("description", "Okay. Ask me to play a flow whenever you like."));
    }

    @Override public Agent copy() { return new WelcomeAgent(id); }
}
