package showroom.ndialog.dialogue;

import showroom.ndialog.agent.Agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The dialogue state: the agent stack (top is focus), the open question, the
 * session name, and the current turn's output. The working copy is the turn's
 * scratchpad; the engine drains it into a Turn at the boundary, and a copy
 * starts with fresh output.
 */
public final class DialogState {
    final List<Agent> stack = new ArrayList<Agent>();
    Question openQuestion;
    String name;
    final List<Turn.Output> outputs = new ArrayList<Turn.Output>();

    /** The top of the stack is the focus. */
    public Agent focus() { return stack.isEmpty() ? null : stack.get(stack.size() - 1); }
    public List<Agent> tasks() { return Collections.unmodifiableList(stack); }

    Agent find(String id) {
        for (Agent agent : stack) if (agent.id().equals(id)) return agent;
        return null;
    }

    public String name() { return name; }
    public void setName(String name) { this.name = name; }

    /** A question is stored and asked in one step; an agent cannot set one silently. */
    public void ask(Question question) {
        openQuestion = question;
        utter(question.expected);
    }

    public void dropQuestion(Agent owner) {
        if (openQuestion != null && openQuestion.ownedBy(owner)) openQuestion = null;
    }

    public void utter(Act act) { outputs.add(Turn.Output.utter(act)); }

    public void flow(Agent owner, String flowId) {
        outputs.add(Turn.Output.flow(owner.id(), flowId));
    }

    /** Unresolved input is an ordinary outcome, not an exception. */
    public void error(String code, String description) {
        Map<String, String> slots = new LinkedHashMap<String, String>();
        slots.put("error", code);
        slots.put("description", description);
        utter(Act.inform(slots));
    }

    /** A snapshot without the previous turn's output; agents are deep-copied. */
    DialogState copy() {
        DialogState copy = new DialogState();
        for (Agent agent : stack) copy.stack.add(agent.copy());
        copy.openQuestion = openQuestion;
        copy.name = name;
        return copy;
    }

    /** The turn's output; the buffer is empty afterwards. */
    List<Turn.Output> drain() {
        List<Turn.Output> drained = new ArrayList<Turn.Output>(outputs);
        outputs.clear();
        return drained;
    }
}
