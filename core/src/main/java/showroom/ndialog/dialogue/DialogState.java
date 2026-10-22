package showroom.ndialog.dialogue;

import showroom.ndialog.agent.Agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The dialogue state: the agent stack (top is focus), the open question, the
 * session name, and the current turn's output. Acts and start requests are
 * per-turn; a copy starts with fresh output.
 */
public final class DialogState {
    final List<Agent> stack = new ArrayList<Agent>();
    OpenQuestion openQuestion;
    String name;
    final List<Act> acts = new ArrayList<Act>();
    final List<Start> starts = new ArrayList<Start>();

    /** The top of the stack is the focus. */
    public Agent focus() { return stack.isEmpty() ? null : stack.get(stack.size() - 1); }
    public List<Agent> tasks() { return Collections.unmodifiableList(stack); }
    public String name() { return name; }
    public void setName(String name) { this.name = name; }
    public List<Act> acts() { return Collections.unmodifiableList(acts); }
    public List<Start> starts() { return Collections.unmodifiableList(starts); }

    public void say(Act act) { acts.add(act); }

    /** A question is stored and asked in one step; an agent cannot set one silently. */
    public void ask(OpenQuestion question) {
        openQuestion = question;
        say(question.expected);
    }

    public void requestStart(Agent owner, String flowId) { starts.add(new Start(owner.id(), flowId)); }

    public void clearQuestion(Agent owner) {
        if (openQuestion != null && openQuestion.ownedBy(owner)) openQuestion = null;
    }

    /** Unresolved input is an ordinary outcome, not an exception. */
    public void error(String code, String description) {
        Map<String, String> slots = new LinkedHashMap<String, String>();
        slots.put("error", code);
        slots.put("description", description);
        say(Act.inform(slots));
    }

    /** A snapshot without the previous turn's output; agents are deep-copied. */
    DialogState copy() {
        DialogState copy = new DialogState();
        for (Agent agent : stack) copy.stack.add(agent.copy());
        copy.openQuestion = openQuestion;
        copy.name = name;
        return copy;
    }

    Agent find(String id) {
        for (Agent agent : stack) if (agent.id().equals(id)) return agent;
        return null;
    }

    /** A start request captured during execute; the listener keeps the agent association. */
    public static final class Start {
        public final String agentId;
        public final String flowId;

        Start(String agentId, String flowId) {
            this.agentId = agentId;
            this.flowId = flowId;
        }
    }
}
