package showroom.ndialog.agent;

import showroom.ndialog.dialogue.DialogState;
import showroom.ndialog.dialogue.SysAct;
import showroom.ndialog.dialogue.UserAct;

/** Greets, collects the session name, offers the flow goal, then stays below flow for name corrections. */
public final class WelcomeAgent extends Agent {
    private boolean declined;

    public WelcomeAgent() { super(Agent.newId()); }

    private WelcomeAgent(String id) { super(id); }

    @Override public Bind bind(UserAct act, DialogState state) {
        if (act.type() == UserAct.Type.INFORM && act.has("name") && act.slot("name") != null) {
            state.setName(act.slot("name"));
            return Bind.BOUND;
        }
        if (act.type() == UserAct.Type.INFORM && act.cleared("task")) {
            declined = true;
            return Bind.BOUND;
        }
        return Bind.NOT_MINE;
    }

    @Override public void onBind(UserAct act, DialogState state) {
        if (!declined) return;
        state.utter(SysAct.inform("description", "Okay. Ask me to play a flow whenever you like."));
        complete();
    }

    @Override public void execute(DialogState state) { }

    @Override public void onFocus(DialogState state) {
        if (state.name() == null) state.ask(SysAct.request(this, "name"));
        else state.ask(SysAct.confirm(this, "task", "flow"));
    }

    @Override public Agent copy() {
        WelcomeAgent copy = new WelcomeAgent(id);
        copy.declined = declined;
        return copy;
    }
}
