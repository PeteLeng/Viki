package showroom.ndialog.agent;

import showroom.ndialog.dialogue.Act;
import showroom.ndialog.dialogue.DialogState;
import showroom.ndialog.dialogue.Question;

/** Greets, collects the session name, offers the flow goal, then stays below flow for name corrections. */
public final class WelcomeAgent extends Agent {
    private boolean declined;

    public WelcomeAgent() { super(Agent.newId()); }

    private WelcomeAgent(String id) { super(id); }

    @Override public Bind bind(Act act, DialogState state) {
        if (act.type == Act.Type.INFORM && act.has("name") && act.slot("name") != null) {
            state.setName(act.slot("name"));
            return Bind.BOUND;
        }
        if (act.type == Act.Type.INFORM && act.has("task") && act.slot("task") == null) {
            declined = true;
            return Bind.BOUND;
        }
        return Bind.NOT_MINE;
    }

    @Override public void onBind(Act act, DialogState state) {
        if (!declined) return;
        state.utter(Act.inform("description", "Okay. Ask me to play a flow whenever you like."));
        complete();
    }

    @Override public void execute(DialogState state) { }

    @Override public void onFocus(DialogState state) {
        if (state.name() == null) state.ask(Question.requestValue(this, "name"));
        else state.ask(Question.confirmGoal(this, "flow"));
    }

    @Override public Agent copy() {
        WelcomeAgent copy = new WelcomeAgent(id);
        copy.declined = declined;
        return copy;
    }
}
