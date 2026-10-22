package showroom.ndialog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Per-call accumulation: working state, output acts, spawned agents, requested starts. */
final class Turn {
    final DialogState state;
    final List<DialogueAct> acts = new ArrayList<DialogueAct>();
    final List<Agent> spawned = new ArrayList<Agent>();
    final List<Result.Start> starts = new ArrayList<Result.Start>();

    Turn(DialogState state) { this.state = state; }

    void say(DialogueAct act) { acts.add(act); }

    void ask(OpenQuestion question) {
        state.openQuestion = question;
        say(question.act());
    }

    void spawn(Agent agent) { spawned.add(agent); }

    void start(Agent agent, String flowId) { starts.add(new Result.Start(agent.id, flowId)); }

    void clearQuestion(Agent owner) {
        if (state.openQuestion != null && state.openQuestion.ownedBy(owner)) state.openQuestion = null;
    }

    /** Unresolved input is an ordinary outcome, not an exception. */
    void error(String code, String description) {
        Map<String, String> slots = new LinkedHashMap<String, String>();
        slots.put("error", code);
        slots.put("description", description);
        say(new DialogueAct(DialogueAct.Type.INFORM, slots));
    }
}
