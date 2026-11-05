package showroom.ndialog.dialogue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import showroom.ndialog.agent.Agent;
import showroom.ndialog.agent.FlowAgent;
import showroom.ndialog.bridge.AppPort;
import showroom.ndialog.bridge.Feature;

public class NDialogCoreTest {
    private final NDialogCore core = new NDialogCore(catalog());
    private DialogState state = new DialogState();

    private static List<Feature> catalog() {
        List<Feature> catalog = new ArrayList<>();
        catalog.add(new Feature("rear_displays", "Rear displays",
                Arrays.asList("back-seat screens", "rear-seat entertainment"),
                Arrays.asList("back-seat"), Arrays.asList("entertainment", "family"),
                "Rear passengers can enjoy separate entertainment on the rear displays."));
        catalog.add(new Feature("rear_audio", "Rear audio",
                Arrays.asList("back-seat audio"),
                Arrays.asList("back-seat"), Arrays.asList("entertainment", "family"),
                "Rear passengers can listen through the rear-seat audio system."));
        catalog.add(new Feature("front_comfort", "Front-seat comfort",
                Arrays.asList("front seats"),
                Arrays.asList("front-seat"), Arrays.asList("comfort", "family"),
                "The front seats offer adjustable comfort settings."));
        catalog.add(new Feature("headlights", "Headlights",
                Arrays.asList("front lights"),
                Arrays.asList("headlights"), Arrays.asList("visibility"),
                "The headlight demonstration showcases the available lighting patterns."));
        return catalog;
    }

    /** request(task=flow, key=value...) */
    private static UserAct flow(String... pairs) {
        Map<String, String> arguments = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            arguments.put(pairs[i], pairs[i + 1]);
        }
        return UserAct.request("flow", arguments);
    }

    private static UserAct greet() { return new UserAct(UserAct.Type.GREET, Collections.emptyMap(), null); }
    private static UserAct affirm() { return new UserAct(UserAct.Type.AFFIRM, Collections.emptyMap(), null); }
    private static UserAct deny() { return new UserAct(UserAct.Type.DENY, Collections.emptyMap(), null); }

    private Turn turn(UserAct act) {
        Turn result = core.handleInput(state, Collections.singletonList(act));
        state = result.state();
        return result;
    }

    private Turn complete(Turn.Output flow) {
        Turn result = core.onFlowEvent(state, flow.agentId, new AppPort.Event("completed", "done"));
        state = result.state();
        return result;
    }

    private SysAct lastInform(Turn after) {
        List<SysAct> acts = after.utters();
        for (int i = acts.size() - 1; i >= 0; i--) {
            if (acts.get(i).type() == SysAct.Type.INFORM) return acts.get(i);
        }
        return null;
    }

    @Test public void greetingHandoffStartsSecondOfferedFlow() {
        Turn greeting = turn(greet());
        assertEquals(2, greeting.utters().size());
        assertEquals(SysAct.Type.GREET, greeting.utters().get(0).type());
        assertEquals(SysAct.Type.REQUEST, greeting.utters().get(1).type());
        assertTrue(greeting.utters().get(1).slots().size() == 1 && greeting.utters().get(1).has("name"));
        assertEquals(SysAct.Type.REQUEST, state.openQuestion.type());

        Turn named = turn(UserAct.inform("name", "Sam"));
        assertEquals("Sam", state.name());
        assertEquals(1, named.utters().size());
        assertEquals(SysAct.Type.CONFIRM, named.utters().get(0).type());
        assertEquals("flow", named.utters().get(0).slot("task"));

        turn(affirm());
        assertEquals(2, state.tasks().size());
        assertEquals(SysAct.Type.SELECT, state.openQuestion.type());
        assertEquals(4, state.openQuestion.values("flow_id").size());

        turn(UserAct.inform("theme", "family"));
        assertEquals(3, state.openQuestion.values("flow_id").size());
        turn(UserAct.inform("part", "back-seat"));
        assertEquals(Arrays.asList("rear_displays", "rear_audio"),
                state.openQuestion.values("flow_id"));

        Turn chosen = turn(UserAct.inform("choice", "2"));
        assertEquals(1, chosen.flows().size());
        assertEquals("rear_audio", chosen.flows().get(0).flowId);
        assertEquals(Agent.Status.RUNNING, state.focus().status());

        Turn done = complete(chosen.flows().get(0));
        assertEquals(1, state.tasks().size());
        assertEquals("completed", lastInform(done).slot("execution_status"));
        assertEquals("rear_audio", lastInform(done).slot("flow_id"));
        assertEquals("Sam", state.name());
        assertNull(state.openQuestion);
    }

    @Test public void directFlowRequestStartsImmediately() {
        Turn started = turn(flow("flow_id", "headlights"));
        assertEquals(1, started.flows().size());
        assertEquals("headlights", started.flows().get(0).flowId);
        assertTrue(started.utters().isEmpty());
        assertEquals(Agent.Status.RUNNING, state.focus().status());

        complete(started.flows().get(0));
        assertTrue(state.tasks().isEmpty());

        Turn stale = core.onFlowEvent(state, started.flows().get(0).agentId, new AppPort.Event("completed", null));
        assertTrue(stale.utters().isEmpty());
        assertTrue(state.tasks().isEmpty());
    }

    @Test public void singleMatchIsConfirmedBeforeAcceptance() {
        turn(flow("theme", "visibility"));
        assertEquals(SysAct.Type.CONFIRM, state.openQuestion.type());
        assertEquals("headlights", state.openQuestion.slot("flow_id"));

        Turn accepted = turn(affirm());
        assertEquals(1, accepted.flows().size());
        assertEquals("headlights", accepted.flows().get(0).flowId);
    }

    @Test public void deniedProposalClearsTheValueAndReasks() {
        turn(flow("theme", "visibility"));
        Turn denied = turn(deny());
        assertTrue(denied.flows().isEmpty());
        assertEquals(1, denied.utters().size());
        assertEquals(SysAct.Type.CONFIRM, denied.utters().get(0).type());
        assertEquals("headlights", denied.utters().get(0).slot("flow_id"));
        assertNull(((FlowAgent) state.focus()).flowId());
        assertEquals(SysAct.Type.CONFIRM, state.openQuestion.type());

        Turn picked = turn(UserAct.inform("flow_id", "headlights"));
        assertEquals(1, picked.flows().size());
        assertEquals("headlights", picked.flows().get(0).flowId);
    }

    @Test public void deniedGoalOfferCompletesWelcome() {
        turn(greet());
        turn(UserAct.inform("name", "Sam"));
        Turn denied = turn(deny());
        assertEquals(1, denied.utters().size());
        assertEquals("Okay. Ask me to play a flow whenever you like.", denied.utters().get(0).slot("description"));
        assertTrue(state.tasks().isEmpty());
        assertNull(state.openQuestion);
    }

    @Test public void nameCorrectionWalksBackStack() {
        turn(greet());
        turn(UserAct.inform("name", "Sam"));
        turn(affirm());

        Turn correction = turn(UserAct.inform("name", "Samuel"));
        assertTrue(correction.utters().isEmpty());
        assertEquals("Samuel", state.name());
        assertEquals(2, state.tasks().size());
        assertEquals(SysAct.Type.SELECT, state.openQuestion.type());

        Turn chosen = turn(UserAct.inform("choice", "1"));
        assertEquals("rear_displays", chosen.flows().get(0).flowId);
    }

    @Test public void bareAffirmUnderSelectionIsUnresolved() {
        turn(greet());
        turn(UserAct.inform("name", "Sam"));
        turn(affirm());

        Turn answered = turn(affirm());
        assertEquals(1, answered.utters().size());
        assertEquals("unresolved_input", lastInform(answered).slot("error"));
        assertEquals(SysAct.Type.SELECT, state.openQuestion.type());
    }

    @Test public void retryAfterCompletionStartsAgain() {
        Turn first = turn(flow("flow_id", "headlights"));
        complete(first.flows().get(0));
        assertTrue(state.tasks().isEmpty());

        Turn second = turn(flow("flow_id", "headlights"));
        assertEquals(1, second.flows().size());
        assertEquals("headlights", second.flows().get(0).flowId);
    }

    @Test public void unsupportedInputIsAnOutcome() {
        assertEquals("unsupported_input", lastInform(turn(UserAct.request("stop",
                Collections.emptyMap()))).slot("error"));
        assertEquals("unresolved_input", lastInform(turn(UserAct.inform("theme", "family"))).slot("error"));

        Turn rejected = turn(flow("flow_id", "front_comfort", "theme", "visibility"));
        assertEquals("conflicting_values", lastInform(rejected).slot("error"));
        assertTrue(rejected.flows().isEmpty());
        assertTrue(state.tasks().isEmpty());
    }
}
