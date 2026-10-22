package showroom.dialogue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Welcome owns the dialogue; the visitor's name belongs to the session. */
final class WelcomeRules {
    static boolean greet(TurnContext c) {
        c.acts.add(DialogueAct.greet());
        c.note("welcome.greet", "Greeting received.");
        // A greeting alone does not interrupt existing Play or repeat its question.
        if (c.state.find(Task.Kind.PLAY) != null) return false;
        return c.activateTask(Task.Kind.WELCOME, Collections.emptyMap());
    }

    static boolean handleInput(TurnContext c, Task welcome, DialogueAct input) {
        if (input.type == DialogueAct.Type.INFORM && input.slots.containsKey("name")) {
            c.state.userName = input.slots.get("name");
            PendingInteraction q = c.questionFor(welcome);
            if (q != null && q.question.type == DialogueAct.Type.REQUEST) c.clearQuestion(welcome);
            c.note("welcome.name", "session.userName=" + c.state.userName);
            return true;
        }
        PendingInteraction q = c.questionFor(welcome);
        if (q != null && q.question.type == DialogueAct.Type.CONFIRM
                && "play".equals(q.question.slots.get("task"))) {
            if (input.type == DialogueAct.Type.AFFIRM) {
                Map<String, String> arguments = new LinkedHashMap<>(q.question.slots);
                arguments.remove("task");
                c.note("welcome.accept_offer", "Activate the proposed Play goal.");
                return c.activateTask(Task.Kind.PLAY, arguments);
            }
            if (input.type == DialogueAct.Type.DENY) {
                c.completeTask(welcome);
                c.acts.add(DialogueAct.inform("description", "Okay. Ask me to play a flow whenever you like."));
                return true;
            }
        }
        return c.unresolved("unresolved_answer", "Answer the name question or the Play offer, or request Play directly.");
    }

    static void advance(TurnContext c, Task welcome) {
        if (c.questionFor(welcome) != null) return;
        if (c.state.userName == null) {
            c.note("welcome.ask_name", "Collect the session name first.");
            c.ask(PendingInteraction.requestValue(welcome, "name"));
        } else {
            c.note("welcome.offer_play", "A proposal does not create a Play task.");
            c.ask(PendingInteraction.confirmGoal(welcome, Task.Kind.PLAY, Collections.emptyMap()));
        }
    }
}
