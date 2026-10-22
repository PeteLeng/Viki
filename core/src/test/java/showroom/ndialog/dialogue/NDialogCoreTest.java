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
        List<Feature> catalog = new ArrayList<Feature>();
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
    private static Act flow(String... pairs) {
        Map<String, List<String>> slots = new LinkedHashMap<String, List<String>>();
        slots.put("task", Collections.singletonList("flow"));
        for (int i = 0; i < pairs.length; i += 2) {
            slots.put(pairs[i], Collections.singletonList(pairs[i + 1]));
        }
        return new Act(Act.Type.REQUEST, slots);
    }

    private DialogState turn(Act act) {
        state = core.handleInput(state, Collections.singletonList(act));
        return state;
    }

    private DialogState complete(DialogState.Start start) {
        state = core.onFlowEvent(state, start.agentId, new AppPort.Event("completed", "done"));
        return state;
    }

    private Act lastInform(DialogState after) {
        List<Act> acts = after.acts();
        for (int i = acts.size() - 1; i >= 0; i--) {
            if (acts.get(i).type == Act.Type.INFORM) return acts.get(i);
        }
        return null;
    }

    @Test public void greetingHandoffStartsSecondOfferedFlow() {
        DialogState greeting = turn(Act.greet());
        assertEquals(2, greeting.acts().size());
        assertEquals(Act.Type.GREET, greeting.acts().get(0).type);
        assertEquals(Act.Type.REQUEST, greeting.acts().get(1).type);
        assertEquals("name", greeting.acts().get(1).soleSlot());
        assertEquals(Act.Type.REQUEST, state.openQuestion.expected.type);

        DialogState named = turn(Act.inform("name", "Sam"));
        assertEquals("Sam", state.name());
        assertEquals(1, named.acts().size());
        assertEquals(Act.Type.CONFIRM, named.acts().get(0).type);
        assertEquals("flow", named.acts().get(0).slot("task"));

        turn(Act.affirm());
        assertEquals(2, state.tasks().size());
        assertEquals(Act.Type.SELECT, state.openQuestion.expected.type);
        assertEquals(4, state.openQuestion.expected.values("flow_id").size());

        turn(Act.inform("theme", "family"));
        assertEquals(3, state.openQuestion.expected.values("flow_id").size());
        turn(Act.inform("part", "back-seat"));
        assertEquals(Arrays.asList("rear_displays", "rear_audio"),
                state.openQuestion.expected.values("flow_id"));

        DialogState chosen = turn(Act.inform("choice", "2"));
        assertEquals(1, chosen.starts().size());
        assertEquals("rear_audio", chosen.starts().get(0).flowId);
        assertEquals(Agent.Status.RUNNING, state.focus().status());

        DialogState done = complete(chosen.starts().get(0));
        assertEquals(1, state.tasks().size());
        assertEquals("completed", lastInform(done).slot("execution_status"));
        assertEquals("rear_audio", lastInform(done).slot("flow_id"));
        assertEquals("Sam", state.name());
        assertNull(state.openQuestion);
    }

    @Test public void directFlowRequestStartsImmediately() {
        DialogState started = turn(flow("flow_id", "headlights"));
        assertEquals(1, started.starts().size());
        assertEquals("headlights", started.starts().get(0).flowId);
        assertTrue(started.acts().isEmpty());
        assertEquals(Agent.Status.RUNNING, state.focus().status());

        complete(started.starts().get(0));
        assertTrue(state.tasks().isEmpty());

        DialogState stale = core.onFlowEvent(state, started.starts().get(0).agentId, new AppPort.Event("completed", null));
        assertTrue(stale.acts().isEmpty());
        assertTrue(state.tasks().isEmpty());
    }

    @Test public void singleMatchIsConfirmedBeforeAcceptance() {
        turn(flow("theme", "visibility"));
        assertEquals(Act.Type.CONFIRM, state.openQuestion.expected.type);
        assertEquals("headlights", state.openQuestion.expected.slot("flow_id"));

        DialogState accepted = turn(Act.affirm());
        assertEquals(1, accepted.starts().size());
        assertEquals("headlights", accepted.starts().get(0).flowId);
    }

    @Test public void deniedProposalClearsTheValueAndReasks() {
        turn(flow("theme", "visibility"));
        DialogState denied = turn(Act.deny());
        assertTrue(denied.starts().isEmpty());
        assertEquals(1, denied.acts().size());
        assertEquals(Act.Type.CONFIRM, denied.acts().get(0).type);
        assertEquals("headlights", denied.acts().get(0).slot("flow_id"));
        assertNull(((FlowAgent) state.focus()).flowId());
        assertEquals(Act.Type.CONFIRM, state.openQuestion.expected.type);

        DialogState picked = turn(Act.inform("flow_id", "headlights"));
        assertEquals(1, picked.starts().size());
        assertEquals("headlights", picked.starts().get(0).flowId);
    }

    @Test public void deniedGoalOfferCompletesWelcome() {
        turn(Act.greet());
        turn(Act.inform("name", "Sam"));
        DialogState denied = turn(Act.deny());
        assertEquals(1, denied.acts().size());
        assertEquals("Okay. Ask me to play a flow whenever you like.", denied.acts().get(0).slot("description"));
        assertTrue(state.tasks().isEmpty());
        assertNull(state.openQuestion);
    }

    @Test public void nameCorrectionWalksBackStack() {
        turn(Act.greet());
        turn(Act.inform("name", "Sam"));
        turn(Act.affirm());

        DialogState correction = turn(Act.inform("name", "Samuel"));
        assertTrue(correction.acts().isEmpty());
        assertEquals("Samuel", state.name());
        assertEquals(2, state.tasks().size());
        assertEquals(Act.Type.SELECT, state.openQuestion.expected.type);

        DialogState chosen = turn(Act.inform("choice", "1"));
        assertEquals("rear_displays", chosen.starts().get(0).flowId);
    }

    @Test public void bareAffirmUnderSelectionIsUnresolved() {
        turn(Act.greet());
        turn(Act.inform("name", "Sam"));
        turn(Act.affirm());

        DialogState answered = turn(Act.affirm());
        assertEquals(1, answered.acts().size());
        assertEquals("unresolved_input", lastInform(answered).slot("error"));
        assertEquals(Act.Type.SELECT, state.openQuestion.expected.type);
    }

    @Test public void retryAfterCompletionStartsAgain() {
        DialogState first = turn(flow("flow_id", "headlights"));
        complete(first.starts().get(0));
        assertTrue(state.tasks().isEmpty());

        DialogState second = turn(flow("flow_id", "headlights"));
        assertEquals(1, second.starts().size());
        assertEquals("headlights", second.starts().get(0).flowId);
    }

    @Test public void unsupportedInputIsAnOutcome() {
        assertEquals("unsupported_input", lastInform(turn(new Act(Act.Type.REQUEST,
                Collections.singletonMap("task", Collections.singletonList("stop"))))).slot("error"));
        assertEquals("unresolved_input", lastInform(turn(Act.inform("theme", "family"))).slot("error"));

        DialogState rejected = turn(flow("flow_id", "front_comfort", "theme", "visibility"));
        assertEquals("conflicting_values", lastInform(rejected).slot("error"));
        assertTrue(rejected.starts().isEmpty());
        assertTrue(state.tasks().isEmpty());
    }
}
