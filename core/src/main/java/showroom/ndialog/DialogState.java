package showroom.ndialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Working state: the agent stack (top is focus), the open question, and the session name. */
public final class DialogState {
    final List<Agent> stack = new ArrayList<Agent>();
    OpenQuestion openQuestion;
    String name;

    public Agent focused() { return stack.isEmpty() ? null : stack.get(stack.size() - 1); }
    public List<Agent> tasks() { return Collections.unmodifiableList(stack); }
    public String name() { return name; }

    DialogState copy() {
        DialogState copy = new DialogState();
        for (Agent agent : stack) copy.stack.add(agent.copy());
        copy.openQuestion = openQuestion;
        copy.name = name;
        return copy;
    }

    Agent find(String id) {
        for (Agent agent : stack) if (agent.id.equals(id)) return agent;
        return null;
    }
}
