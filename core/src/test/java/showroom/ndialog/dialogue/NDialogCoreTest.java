package showroom.ndialog.dialogue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import showroom.ndialog.agent.Agent;
import showroom.ndialog.bridge.AppPort;
import showroom.ndialog.bridge.Feature;
import showroom.ndialog.nlu.Segment;

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

    private DialogState turn(Segment segment) {
        state = core.handleUserTurn(state, Collections.singletonList(segment));
        return state;
    }

    private DialogState complete(DialogState.Start start) {
        state = core.onFlowEvent(state, start.agentId, new AppPort.Event("completed", "done"));
        return state;
    }

    private DialogueAct lastInform(DialogState after) {
        List<DialogueAct> acts = after.acts();
        for (int i = acts.size() - 1; i >= 0; i--) {
            if (acts.get(i).type == DialogueAct.Type.INFORM) return acts.get(i);
        }
        return null;
    }

    @Test public void greetingHandoffStartsSecondOfferedFlow() {
        DialogState greeting = turn(Segment.of("wakeup"));
        assertEquals(2, greeting.acts().size());
        assertEquals(DialogueAct.Type.GREET, greeting.acts().get(0).type);
        assertEquals(DialogueAct.Type.REQUEST, greeting.acts().get(1).type);
        assertEquals("name", greeting.acts().get(1).slots.get("slot"));
        assertEquals(OpenQuestion.Kind.REQUEST, state.openQuestion.kind);

        DialogState named = turn(Segment.value("name", "Sam"));
        assertEquals("Sam", state.name());
        assertEquals(1, named.acts().size());
        assertEquals(DialogueAct.Type.CONFIRM, named.acts().get(0).type);
        assertEquals("flow", named.acts().get(0).slots.get("task"));

        turn(Segment.of("yes"));
        assertEquals(2, state.tasks().size());
        assertEquals(OpenQuestion.Kind.SELECT, state.openQuestion.kind);
        assertEquals(4, state.openQuestion.choices.size());

        turn(Segment.value("theme", "family"));
        assertEquals(3, state.openQuestion.choices.size());
        turn(Segment.value("part", "back-seat"));
        assertEquals(Arrays.asList("rear_displays", "rear_audio"), state.openQuestion.choices);

        DialogState chosen = turn(new Segment("choice", Collections.singletonMap("n", "2")));
        assertEquals(1, chosen.starts().size());
        assertEquals("rear_audio", chosen.starts().get(0).flowId);
        assertEquals(Agent.Status.RUNNING, state.focused().status());

        DialogState done = complete(chosen.starts().get(0));
        assertEquals(1, state.tasks().size());
        assertEquals("completed", lastInform(done).slots.get("execution_status"));
        assertEquals("rear_audio", lastInform(done).slots.get("flow_id"));
        assertEquals("Sam", state.name());
        assertNull(state.openQuestion);
    }

    @Test public void directFlowRequestStartsImmediately() {
        DialogState started = turn(new Segment("flow", Collections.singletonMap("id", "headlights")));
        assertEquals(1, started.starts().size());
        assertEquals("headlights", started.starts().get(0).flowId);
        assertTrue(started.acts().isEmpty());
        assertEquals(Agent.Status.RUNNING, state.focused().status());

        complete(started.starts().get(0));
        assertTrue(state.tasks().isEmpty());

        DialogState stale = core.onFlowEvent(state, started.starts().get(0).agentId, new AppPort.Event("completed", null));
        assertTrue(stale.acts().isEmpty());
        assertTrue(state.tasks().isEmpty());
    }

    @Test public void singleMatchIsConfirmedBeforeAcceptance() {
        turn(new Segment("flow", Collections.singletonMap("theme", "visibility")));
        assertEquals(OpenQuestion.Kind.CONFIRM_VALUE, state.openQuestion.kind);
        assertEquals("headlights", state.openQuestion.proposed);

        DialogState accepted = turn(Segment.of("yes"));
        assertEquals(1, accepted.starts().size());
        assertEquals("headlights", accepted.starts().get(0).flowId);
    }

    @Test public void deniedConfirmationAsksForReplacement() {
        turn(new Segment("flow", Collections.singletonMap("theme", "visibility")));
        DialogState denied = turn(Segment.of("no"));
        assertEquals(1, denied.acts().size());
        assertEquals(DialogueAct.Type.REQUEST, denied.acts().get(0).type);
        assertEquals(OpenQuestion.Kind.REQUEST, state.openQuestion.kind);

        DialogState picked = turn(Segment.value("id", "headlights"));
        assertEquals(1, picked.starts().size());
        assertEquals("headlights", picked.starts().get(0).flowId);
    }

    @Test public void nameCorrectionWalksBackStack() {
        turn(Segment.of("wakeup"));
        turn(Segment.value("name", "Sam"));
        turn(Segment.of("yes"));

        DialogState correction = turn(Segment.value("name", "Samuel"));
        assertTrue(correction.acts().isEmpty());
        assertEquals("Samuel", state.name());
        assertEquals(2, state.tasks().size());
        assertEquals(OpenQuestion.Kind.SELECT, state.openQuestion.kind);

        DialogState chosen = turn(new Segment("choice", Collections.singletonMap("n", "1")));
        assertEquals("rear_displays", chosen.starts().get(0).flowId);
    }

    @Test public void bareYesAfterSelectionRepeatsTheOffer() {
        turn(Segment.of("wakeup"));
        turn(Segment.value("name", "Sam"));
        turn(Segment.of("yes"));

        DialogState repeated = turn(Segment.of("yes"));
        assertEquals(1, repeated.acts().size());
        assertEquals(DialogueAct.Type.SELECT, repeated.acts().get(0).type);
        assertEquals(4, repeated.acts().get(0).choices.size());
        assertEquals(OpenQuestion.Kind.SELECT, state.openQuestion.kind);
    }

    @Test public void retryAfterCompletionStartsAgain() {
        DialogState first = turn(new Segment("flow", Collections.singletonMap("id", "headlights")));
        complete(first.starts().get(0));
        assertTrue(state.tasks().isEmpty());

        DialogState second = turn(new Segment("flow", Collections.singletonMap("id", "headlights")));
        assertEquals(1, second.starts().size());
        assertEquals("headlights", second.starts().get(0).flowId);
    }

    @Test public void unsupportedInputIsAnOutcome() {
        assertEquals("unsupported_input", lastInform(turn(Segment.of("stop"))).slots.get("error"));
        assertEquals("unresolved_input", lastInform(turn(Segment.value("theme", "family"))).slots.get("error"));

        java.util.Map<String, String> conflicting = new java.util.LinkedHashMap<String, String>();
        conflicting.put("id", "front_comfort");
        conflicting.put("theme", "visibility");
        DialogState rejected = turn(new Segment("flow", conflicting));
        assertEquals("conflicting_values", lastInform(rejected).slots.get("error"));
        assertTrue(rejected.starts().isEmpty());
        assertTrue(state.tasks().isEmpty());
    }
}
