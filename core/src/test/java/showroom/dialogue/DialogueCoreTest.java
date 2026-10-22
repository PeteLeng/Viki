package showroom.dialogue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class DialogueCoreTest {
    private final Feature rearDisplays = feature("rear_displays", "back-seat", "family");
    private final Feature rearAudio = feature("rear_audio", "back-seat", "family");
    private final Feature front = feature("front_comfort", "front-seat", "comfort");
    private final Feature headlights = feature("headlights", "headlights", "visibility");
    private final List<Feature> catalog = Arrays.asList(rearDisplays, rearAudio, front, headlights);
    private final DialogueCore core = new DialogueCore(catalog);

    @Test public void greetingNameAndAcceptedOfferHandOffToPlayWithoutReplayingYes() {
        DialogueState initial = new DialogueState();
        DialogueResult greeting = turn(initial, DialogueAct.greet());
        assertEquals(Task.Kind.WELCOME, greeting.state.focused().kind());
        assertEquals("name", question(greeting).slots.get("slot"));
        assertEquals(1, greeting.state.tasks().size());
        assertTrue(greeting.commands.isEmpty());
        assertSame(question(greeting), greeting.acts.get(1));
        assertNull(initial.focused());
        assertNull(initial.pendingInteraction());

        DialogueResult named = turn(greeting.state, DialogueAct.inform("name", "Sam"));
        assertEquals("Sam", named.state.userName());
        assertTrue(named.state.focused().parameters().isEmpty());
        assertEquals(Task.Kind.WELCOME, named.state.focused().kind());
        assertEquals(DialogueAct.Type.CONFIRM, question(named).type);
        assertEquals("play", question(named).slots.get("task"));
        assertNull(named.state.find(Task.Kind.PLAY));
        assertNull(greeting.state.userName());

        DialogueResult accepted = turn(named.state, DialogueAct.affirm());
        assertEquals(1, accepted.state.tasks().size());
        assertEquals(Task.Kind.PLAY, accepted.state.focused().kind());
        assertEquals("Sam", accepted.state.userName());
        assertEquals(DialogueAct.Type.SELECT, question(accepted).type);
        assertEquals(accepted.state.focused().id(), accepted.state.pendingInteraction().ownerTaskId);
        assertEquals(1, accepted.acts.size());
        assertTrue(accepted.commands.isEmpty());
        assertNull(accepted.state.focused().parameter("flow_id"));
    }

    @Test public void declineCompletesWelcomeAndKeepsTheSessionName() {
        DialogueResult welcome = turn(new DialogueState(), DialogueAct.greet());
        DialogueResult offer = turn(welcome.state, DialogueAct.inform("name", "Sam"));
        DialogueResult declined = turn(offer.state, DialogueAct.deny());
        assertTrue(declined.state.tasks().isEmpty());
        assertNull(declined.state.pendingInteraction());
        assertEquals("Sam", declined.state.userName());
        assertTrue(declined.commands.isEmpty());
        DialogueResult again = turn(declined.state, DialogueAct.greet());
        assertEquals("play", question(again).slots.get("task"));
    }

    @Test public void directRequestBypassesWelcomeAndUsesSingletonAdmission() {
        DialogueResult welcome = turn(new DialogueState(), DialogueAct.greet());
        DialogueResult direct = turn(welcome.state, DialogueAct.request("play"));
        assertEquals(1, direct.state.tasks().size());
        assertEquals(Task.Kind.PLAY, direct.state.focused().kind());
        assertNull(direct.state.userName());
        String id = direct.state.focused().id();
        DialogueResult named = turn(direct.state, request("flow_id", "headlights"));
        assertEquals(id, named.state.focused().id());
        assertEquals(1, named.state.tasks().size());
        assertNull(named.state.pendingInteraction());
        assertEquals("headlights", named.commands.get(0).flowId);
        assertEquals(Task.Progress.START_REQUESTED, named.state.focused().progress());
        assertTrue(named.acts.isEmpty()); // Issuing a start does not assert successful execution.
    }

    @Test public void themeUpdatesExistingPlayAndInvalidatesDependentChoices() {
        DialogueResult play = turn(new DialogueState(), DialogueAct.request("play"));
        DialogueResult narrowed = turn(play.state, DialogueAct.inform("theme", "family"));
        assertEquals(play.state.focused().id(), narrowed.state.focused().id());
        assertEquals("family", narrowed.state.focused().parameter("theme"));
        assertEquals(Arrays.asList("rear_displays", "rear_audio"), question(narrowed).choices);
        assertEquals(4, question(play).choices.size());
        assertNotSame(play.state.pendingInteraction(), narrowed.state.pendingInteraction());
        assertNull(play.state.focused().parameter("theme"));
        assertTrue(narrowed.commands.isEmpty());

        DialogueResult selected = turn(narrowed.state, DialogueAct.choice(2));
        assertEquals("rear_audio", selected.state.focused().parameter("flow_id"));
        assertNull(selected.state.pendingInteraction());
        assertEquals("rear_audio", selected.commands.get(0).flowId);
    }

    @Test public void aThemeAloneDoesNotEstablishAPlayGoal() {
        DialogueResult result = turn(new DialogueState(), DialogueAct.inform("theme", "family"));
        assertTrue(result.state.tasks().isEmpty());
        assertTrue(result.commands.isEmpty());
        assertEquals("unresolved_answer", result.acts.get(0).slots.get("error"));
    }

    @Test public void unrelatedNameUpdateDoesNotClearTheGoalOffer() {
        DialogueResult greeting = turn(new DialogueState(), DialogueAct.greet());
        DialogueResult offer = turn(greeting.state, DialogueAct.inform("name", "Sam"));
        DialogueResult corrected = turn(offer.state, DialogueAct.inform("name", "Alex"));
        assertEquals("Alex", corrected.state.userName());
        assertSame(offer.state.pendingInteraction(), corrected.state.pendingInteraction());
        assertTrue(corrected.acts.isEmpty());
    }

    @Test public void ordinalUsesTheActualOfferedOrderEvenIfCatalogOrderChanges() {
        DialogueResult offered = turn(new DialogueState(), request("part", "back-seat"));
        DialogueCore reordered = new DialogueCore(Arrays.asList(headlights, rearAudio, rearDisplays, front));
        DialogueResult selected = reordered.handleUserTurn(offered.state, Collections.singletonList(DialogueAct.choice(2)));
        assertEquals("rear_audio", selected.commands.get(0).flowId);
        assertNull(offered.state.focused().parameter("flow_id"));
    }

    @Test public void yesAfterSelectionDoesNotGuessAFlowOrCreateATask() {
        DialogueResult offered = turn(new DialogueState(), DialogueAct.request("play"));
        DialogueResult yes = turn(offered.state, DialogueAct.affirm());
        assertSame(offered.state.pendingInteraction(), yes.state.pendingInteraction());
        assertSame(question(offered), yes.acts.get(0));
        assertNull(yes.state.focused().parameter("flow_id"));
        assertEquals(1, yes.state.tasks().size());
        assertTrue(yes.commands.isEmpty());
    }

    @Test public void confirmationStoresOnlyAProposalUntilAccepted() {
        DialogueResult proposal = turn(new DialogueState(), request("theme", "visibility"));
        assertEquals(DialogueAct.Type.CONFIRM, question(proposal).type);
        assertEquals("headlights", question(proposal).slots.get("flow_id"));
        assertNull(proposal.state.focused().parameter("flow_id"));
        assertTrue(proposal.commands.isEmpty());
        DialogueResult accepted = turn(proposal.state, DialogueAct.affirm());
        assertEquals("headlights", accepted.state.focused().parameter("flow_id"));
        assertEquals("headlights", accepted.commands.get(0).flowId);
        assertNull(accepted.state.pendingInteraction());
    }

    @Test public void rejectionKeepsPlayAndAsksForAReplacementValue() {
        DialogueResult proposal = turn(new DialogueState(), request("theme", "visibility"));
        DialogueResult denied = turn(proposal.state, DialogueAct.deny());
        assertEquals(proposal.state.focused().id(), denied.state.focused().id());
        assertEquals(DialogueAct.Type.REQUEST, question(denied).type);
        assertEquals("flow_id", question(denied).slots.get("slot"));
        assertNull(denied.state.focused().parameter("flow_id"));
        assertTrue(denied.commands.isEmpty());
    }

    @Test public void clearedChoicesCannotResolveALaterOrdinalEvenAfterCompletion() {
        DialogueResult offered = turn(new DialogueState(), DialogueAct.request("play"));
        DialogueResult start = turn(offered.state, DialogueAct.choice(2));
        DialogueResult completed = core.handleFlowEvent(start.state, start.commands.get(0), new Flow.Event("completed", null));
        DialogueResult later = turn(completed.state, DialogueAct.choice(1));
        assertEquals("unresolved_reference", later.acts.get(0).slots.get("error"));
        assertEquals("rear_audio", later.state.focused().parameter("flow_id"));
        assertNull(later.state.pendingInteraction());
        assertTrue(later.commands.isEmpty());
    }

    @Test public void invalidReferencesAndUnknownValuesPreserveTheQuestion() {
        DialogueResult offered = turn(new DialogueState(), DialogueAct.request("play"));
        for (DialogueAct input : Arrays.asList(DialogueAct.choice(0), DialogueAct.choice(99),
                DialogueAct.inform("choice", "second"), DialogueAct.inform("flow_id", "invented"))) {
            DialogueResult result = turn(offered.state, input);
            assertNotNull(result.acts.get(0).slots.get("error"));
            assertSame(offered.state.pendingInteraction(), result.state.pendingInteraction());
            assertTrue(result.state.focused().parameters().isEmpty());
            assertTrue(result.commands.isEmpty());
        }
    }

    @Test public void unsupportedCompositeInputDoesNotPartiallyApplyOrCreateTasks() {
        DialogueResult greeting = turn(new DialogueState(), DialogueAct.greet());
        DialogueResult multiple = turn(greeting.state, DialogueAct.inform("name", "Sam"), DialogueAct.request("play"));
        assertNull(multiple.state.userName());
        assertEquals(Task.Kind.WELCOME, multiple.state.focused().kind());
        assertSame(greeting.state.pendingInteraction(), multiple.state.pendingInteraction());
        DialogueResult multiValue = turn(greeting.state, new DialogueAct(DialogueAct.Type.INFORM,
                slots("name", "Sam", "theme", "family")));
        assertNull(multiValue.state.userName());
        assertSame(greeting.state.pendingInteraction(), multiValue.state.pendingInteraction());
        assertTrue(multiValue.commands.isEmpty());
    }

    @Test public void invalidRequestArgumentsDoNotPartiallyMutateOrHandOffWelcome() {
        DialogueResult greeting = turn(new DialogueState(), DialogueAct.greet());
        DialogueResult invalid = turn(greeting.state, request("theme", "family", "flow_id", "headlights"));
        assertEquals("conflicting_values", invalid.acts.get(0).slots.get("error"));
        assertEquals(Task.Kind.WELCOME, invalid.state.focused().kind());
        assertEquals(1, invalid.state.tasks().size());
        assertSame(greeting.state.pendingInteraction(), invalid.state.pendingInteraction());
        assertTrue(invalid.commands.isEmpty());
    }

    @Test public void noMatchesKeepsBothFiltersAndDoesNotRetainTheOldSelection() {
        DialogueResult play = turn(new DialogueState(), request("part", "back-seat"));
        DialogueResult result = turn(play.state, DialogueAct.inform("theme", "visibility"));
        assertEquals("no_matches", result.acts.get(0).slots.get("error"));
        assertEquals("back-seat", result.state.focused().parameter("part"));
        assertEquals("visibility", result.state.focused().parameter("theme"));
        assertNull(result.state.pendingInteraction());
        assertTrue(result.commands.isEmpty());
        DialogueResult any = turn(result.state, DialogueAct.inform("theme", "any"));
        assertEquals("any", any.state.focused().parameter("theme"));
        assertEquals(Arrays.asList("rear_displays", "rear_audio"), question(any).choices);
    }

    @Test public void outstandingAndCompletedWorkDoNotAutomaticallyRestart() {
        DialogueResult start = turn(new DialogueState(), request("flow_id", "headlights"));
        FlowCommand command = start.commands.get(0);
        DialogueResult duplicate = turn(start.state, DialogueAct.request("play"));
        assertTrue(duplicate.commands.isEmpty());
        assertEquals(start.state.focused().id(), duplicate.state.focused().id());
        DialogueResult running = core.handleFlowEvent(duplicate.state, command, new Flow.Event("running", null));
        assertEquals(Task.Progress.RUNNING, running.state.focused().progress());
        assertEquals("running", running.acts.get(0).slots.get("execution_status"));
        DialogueResult progress = core.handleFlowEvent(running.state, command, new Flow.Event("progress", "50%"));
        assertEquals("50%", progress.acts.get(0).slots.get("description"));
        assertTrue(progress.commands.isEmpty());
        DialogueResult completed = core.handleFlowEvent(progress.state, command, new Flow.Event("completed", null));
        assertEquals(Task.Progress.COMPLETED, completed.state.focused().progress());
        assertTrue(turn(completed.state, DialogueAct.inform("flow_id", "headlights")).commands.isEmpty());
        DialogueResult greeting = turn(completed.state, DialogueAct.greet());
        assertEquals(1, greeting.acts.size());
        assertEquals(DialogueAct.Type.GREET, greeting.acts.get(0).type);
        assertEquals(Task.Kind.PLAY, greeting.state.focused().kind());
    }

    @Test public void changingLivePlayDoesNotChangeSelectionOrStartAnotherExecution() {
        DialogueResult start = turn(new DialogueState(), request("flow_id", "headlights"));
        DialogueResult changed = turn(start.state, request("flow_id", "rear_audio"));
        assertEquals("headlights", changed.state.focused().parameter("flow_id"));
        assertEquals(Task.Progress.START_REQUESTED, changed.state.focused().progress());
        assertTrue(changed.commands.isEmpty());
        assertEquals(1, changed.state.tasks().size());
    }

    @Test public void explicitRetryReusesPlayAndRejectsThePreviousCommandsCallbacks() {
        DialogueResult start = turn(new DialogueState(), request("flow_id", "headlights"));
        FlowCommand oldCommand = start.commands.get(0);
        DialogueResult failed = core.handleFlowEvent(start.state, oldCommand, new Flow.Event("failed", "Unavailable"));
        assertEquals(Task.Progress.FAILED, failed.state.focused().progress());
        assertTrue(failed.commands.isEmpty());
        DialogueResult retry = turn(failed.state, DialogueAct.request("play"));
        assertEquals(start.state.focused().id(), retry.state.focused().id());
        assertEquals(1, retry.commands.size());
        DialogueResult stale = core.handleFlowEvent(retry.state, oldCommand, new Flow.Event("completed", "Old completion"));
        assertEquals(Task.Progress.START_REQUESTED, stale.state.focused().progress());
        assertTrue(stale.acts.isEmpty());
        assertTrue(stale.commands.isEmpty());
        assertTrue(stale.trace.steps.get(0).startsWith("flow.ignore:"));
    }

    @Test public void traceExplainsAdmissionAndDispatchWithoutChangingItsBeforeState() {
        DialogueState before = new DialogueState();
        DialogueResult start = turn(before, request("flow_id", "headlights"));
        assertSame(before, start.trace.before);
        assertSame(start.state, start.trace.after);
        assertNull(before.focused());
        assertEquals(Task.Progress.START_REQUESTED, start.trace.after.focused().progress());
        assertEquals(start.commands, start.trace.commands);
        assertTrue(start.trace.steps.toString().contains("task.admit:"));
        assertTrue(start.trace.steps.toString().contains("play.start:"));
    }

    private DialogueResult turn(DialogueState state, DialogueAct... input) {
        return core.handleUserTurn(state, Arrays.asList(input));
    }

    private static DialogueAct question(DialogueResult result) { return result.state.pendingInteraction().question; }

    private static DialogueAct request(String... pairs) {
        Map<String, String> arguments = slots(pairs);
        arguments.put("task", "play");
        return new DialogueAct(DialogueAct.Type.REQUEST, arguments);
    }

    private static Map<String, String> slots(String... pairs) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) values.put(pairs[i], pairs[i + 1]);
        return values;
    }

    private static Feature feature(String id, String part, String theme) {
        return new Feature(id, id, Collections.emptyList(), Collections.singletonList(part),
                Collections.singletonList(theme), "Description of " + id);
    }
}
