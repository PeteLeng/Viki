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
    private final Feature front = feature("front_comfort", "front-seat", "family");
    private final Feature headlights = feature("headlights", "headlights", "visibility");
    private final List<Feature> catalog = Arrays.asList(rearDisplays, rearAudio, front, headlights);
    private final DialogueCore core = new DialogueCore(catalog);

    @Test public void requestCreatesTaskAndQuestionOnThatTask() {
        DialogueState initial = new DialogueState();
        DialogueResult result = turn(initial, Act.request("demonstrate"));
        assertNull(initial.focused());
        assertEquals(1, result.state.tasks().size());
        assertEquals(Task.Kind.DEMONSTRATE, result.state.focused().kind());
        assertNull(result.state.focused().featureId());
        assertEquals(Expectation.Kind.CHOICE, result.state.focused().expectation().kind);
        assertSame(result.state.focused().choices(), result.state.focused().expectation().choices);
        assertEquals("demonstrate.ask_feature", result.trace.rule);
    }

    @Test public void ordinalUsesOfferedSubsetAndOrderNotLatestCatalog() {
        DialogueResult offered = turn(new DialogueState(), Act.request("demonstrate"), Act.inform("part", "back-seat"));
        DialogueCore reordered = new DialogueCore(Arrays.asList(headlights, rearAudio, rearDisplays, front));
        DialogueResult result = reordered.handleUserTurn(offered.state, Collections.singletonList(Act.select(2)));
        assertEquals("rear_audio", result.state.focused().featureId());
        assertEquals("rear_audio", result.actions.get(0).featureId);
        assertEquals(SystemAction.Kind.START_DEMO, result.actions.get(0).kind);
        assertNull(result.state.focused().expectation());
        assertEquals(Arrays.asList(rearDisplays, rearAudio), result.state.focused().choices());
        assertNull(offered.state.focused().featureId());
    }

    @Test public void answeredChoicesRemainUsableAfterCompletion() {
        DialogueResult offered = turn(new DialogueState(), Act.request("demonstrate"), Act.inform("part", "back-seat"));
        DialogueResult started = turn(offered.state, Act.select(2));
        DialogueResult completed = core.handleFlowEvent(started.state, started.actions.get(0), new Flow.Event("completed", null));
        DialogueResult changed = turn(completed.state, Act.select(1));
        assertEquals("rear_displays", changed.state.focused().featureId());
        assertEquals(SystemAction.Kind.START_DEMO, changed.actions.get(0).kind);
        assertEquals(2, changed.state.focused().choices().size());
    }

    @Test public void affirmDoesNotSelectFromChoiceQuestion() {
        DialogueResult offered = turn(new DialogueState(), Act.request("demonstrate"));
        DialogueResult result = turn(offered.state, Act.affirm());
        assertEquals("repair.unresolved_input", result.trace.rule);
        assertNull(result.state.focused().featureId());
        assertEquals(Expectation.Kind.CHOICE, result.state.focused().expectation().kind);
    }

    @Test public void affirmResolvesProposedValueRatherThanMissingParameterAlone() {
        DialogueResult offered = turn(new DialogueState(), Act.request("demonstrate"), Act.inform("part", "front-seat"));
        assertEquals(Expectation.Kind.CONFIRMATION, offered.state.focused().expectation().kind);
        assertEquals("front_comfort", offered.state.focused().expectation().proposedValue);
        DialogueResult result = turn(offered.state, Act.affirm());
        assertEquals("front_comfort", result.state.focused().featureId());
        assertEquals(SystemAction.Kind.START_DEMO, result.actions.get(0).kind);
    }

    @Test public void rejectedConfirmationDoesNotImmediatelyRepeatTheSameProposal() {
        DialogueResult offered = turn(new DialogueState(), Act.request("explain"), Act.inform("part", "front-seat"));
        DialogueResult result = turn(offered.state, Act.deny());
        assertEquals("task.rejected_confirmation", result.trace.rule);
        assertEquals(Expectation.Kind.VALUE, result.state.focused().expectation().kind);
        assertNull(result.state.focused().featureId());
    }

    @Test public void wholeTurnCorrectionReplacesPartAndInvalidatesDependentChoice() {
        DialogueResult offered = turn(new DialogueState(), Act.request("browse"), Act.inform("part", "back-seat"));
        DialogueResult selected = turn(offered.state, Act.select(2));
        DialogueResult result = turn(selected.state, Act.deny(), Act.inform("part", "front-seat"));
        assertEquals("front-seat", result.state.focused().part());
        assertNull(result.state.focused().featureId());
        assertEquals(Collections.singletonList(front), result.state.focused().choices());
        assertEquals("browse.present_matches", result.trace.rule);
        DialogueResult reverse = turn(selected.state, Act.inform("part", "front-seat"), Act.deny());
        assertEquals(result.state.focused().part(), reverse.state.focused().part());
        assertEquals(result.state.focused().choices(), reverse.state.focused().choices());
    }

    @Test public void overanswerAppliesPartAndThemeBeforePolicy() {
        DialogueResult browse = turn(new DialogueState(), Act.request("browse"));
        DialogueResult result = turn(browse.state, Act.inform("part", "back-seat"), Act.inform("theme", "family"));
        assertEquals("back-seat", result.state.focused().part());
        assertEquals("family", result.state.focused().theme());
        assertEquals(Arrays.asList(rearDisplays, rearAudio), result.state.focused().choices());
        assertNull(result.state.focused().expectation());
        assertEquals(1, result.actions.size());
    }

    @Test public void browsingDoesNotDemandFiltersOrStartDemoOnSelection() {
        DialogueResult browse = turn(new DialogueState(), Act.request("browse"));
        assertEquals(SystemAction.Kind.PRESENT_FEATURES, browse.actions.get(0).kind);
        DialogueResult selected = turn(browse.state, Act.select(1));
        assertEquals("browse.selected", selected.trace.rule);
        assertEquals(SystemAction.Kind.REPLY, selected.actions.get(0).kind);
    }

    @Test public void asideKeepsParentsQuestionAndRestoresOfferedOrder() {
        DialogueResult parent = turn(new DialogueState(), Act.request("demonstrate"), Act.inform("part", "back-seat"));
        DialogueResult aside = turn(parent.state, related(requestFeature("explain", "headlights"), "aside"));
        assertEquals(1, aside.state.tasks().size());
        assertEquals(parent.state.focused().id(), aside.state.focused().id());
        assertEquals(2, aside.actions.size());
        assertEquals(Expectation.Kind.CHOICE, aside.state.focused().expectation().kind);
        assertEquals(Arrays.asList(rearDisplays, rearAudio), aside.actions.get(1).choices);
        assertEquals("rear_audio", turn(aside.state, Act.select(2)).state.focused().featureId());
    }

    @Test public void suspendedFlowCompletionDoesNotAnswerOrRepeatAsideQuestion() {
        DialogueResult demo = turn(new DialogueState(), requestFeature("demonstrate", "rear_audio"));
        DialogueResult aside = turn(demo.state, related(Act.request("explain"), "aside"));
        assertEquals(2, aside.state.tasks().size());
        DialogueResult completed = core.handleFlowEvent(aside.state, demo.actions.get(0), new Flow.Event("completed", null));
        assertEquals(2, completed.state.tasks().size());
        assertFalse(completed.state.tasks().get(0).demoOutstanding());
        assertEquals(Expectation.Kind.CHOICE, completed.state.focused().expectation().kind);
        assertTrue(completed.actions.isEmpty());
    }

    @Test public void progressAndNarrationNeverRerunStartPolicy() {
        DialogueResult demo = turn(new DialogueState(), requestFeature("demonstrate", "rear_audio"));
        DialogueResult progress = core.handleFlowEvent(demo.state, demo.actions.get(0), new Flow.Event("progress", "50%"));
        assertEquals(1, progress.actions.size());
        assertEquals(SystemAction.Kind.REPLY, progress.actions.get(0).kind);
        assertEquals("50%", progress.actions.get(0).text);
        assertTrue(progress.state.focused().demoOutstanding());
    }

    @Test public void unmarkedDifferentGoalDoesNotSilentlyChoosePushOrReplace() {
        DialogueResult browse = turn(new DialogueState(), Act.request("browse"));
        DialogueResult result = turn(browse.state, requestFeature("explain", "headlights"));
        assertEquals("repair.unresolved_input", result.trace.rule);
        assertEquals(browse.state.focused().id(), result.state.focused().id());
        assertEquals(1, result.state.tasks().size());
    }

    @Test public void parentConfirmationAndNewAsideBindToDifferentGoals() {
        DialogueResult confirmation = turn(new DialogueState(), Act.request("demonstrate"), Act.inform("part", "front-seat"));
        DialogueResult result = turn(confirmation.state, related(Act.request("explain"), "aside"), Act.affirm());
        assertEquals(2, result.state.tasks().size());
        assertEquals("front_comfort", result.state.tasks().get(0).featureId());
        assertNull(result.state.tasks().get(0).expectation());
        assertEquals(Task.Kind.EXPLAIN, result.state.focused().kind());
        assertEquals(Expectation.Kind.CHOICE, result.state.focused().expectation().kind);
        assertNull(result.state.focused().featureId());
        assertEquals(SystemAction.Kind.ASK, result.actions.get(0).kind);
    }

    @Test public void asideRestoresAConfirmationWithItsActualProposedValue() {
        DialogueResult confirmation = turn(new DialogueState(), Act.request("demonstrate"), Act.inform("part", "front-seat"));
        DialogueResult result = turn(confirmation.state, related(requestFeature("explain", "headlights"), "aside"));
        assertEquals(2, result.actions.size());
        assertTrue(result.actions.get(1).text.contains("front_comfort"));
        assertEquals("front_comfort", turn(result.state, Act.affirm()).state.focused().featureId());
    }

    @Test public void explicitReplacementAbandonsOldChoiceContext() {
        DialogueResult browse = turn(new DialogueState(), Act.request("browse"));
        DialogueResult replaced = turn(browse.state, related(requestFeature("explain", "headlights"), "replace"));
        assertEquals(Task.Kind.EXPLAIN, replaced.state.focused().kind());
        assertTrue(replaced.state.focused().choices().isEmpty());
        DialogueResult ordinal = turn(replaced.state, Act.select(2));
        assertEquals("repair.unresolved_input", ordinal.trace.rule);
    }

    @Test public void changingSelectionDuringOutstandingDemoDoesNotReplaceExecution() {
        DialogueResult offered = turn(new DialogueState(), Act.request("demonstrate"), Act.inform("part", "back-seat"));
        DialogueResult demo = turn(offered.state, Act.select(2));
        DialogueResult changed = turn(demo.state, Act.select(1));
        assertEquals("rear_displays", changed.state.focused().featureId());
        assertEquals("demonstrate.active", changed.trace.rule);
        assertEquals(SystemAction.Kind.REPLY, changed.actions.get(0).kind);
        DialogueResult completed = core.handleFlowEvent(changed.state, demo.actions.get(0), new Flow.Event("completed", null));
        assertTrue(completed.actions.isEmpty());
    }

    @Test public void failedDemoDoesNotAutomaticallyRestartButExplicitRequestCanRetry() {
        DialogueResult demo = turn(new DialogueState(), requestFeature("demonstrate", "rear_audio"));
        DialogueResult failed = core.handleFlowEvent(demo.state, demo.actions.get(0), new Flow.Event("failed", "Unavailable"));
        assertFalse(failed.state.focused().demoOutstanding());
        assertEquals(SystemAction.Kind.REPLY, failed.actions.get(0).kind);
        DialogueResult repeated = turn(failed.state, Act.request("demonstrate"));
        assertEquals(SystemAction.Kind.START_DEMO, repeated.actions.get(0).kind);
    }

    @Test public void staleCallbackDoesNotEndRepeatedStartOfSameFeature() {
        DialogueResult first = turn(new DialogueState(), requestFeature("demonstrate", "rear_audio"));
        DialogueResult completed = core.handleFlowEvent(first.state, first.actions.get(0), new Flow.Event("completed", null));
        DialogueResult second = turn(completed.state, Act.request("demonstrate"));
        DialogueResult stale = core.handleFlowEvent(second.state, first.actions.get(0), new Flow.Event("failed", "Old failure"));
        assertEquals("flow.ignore", stale.trace.rule);
        assertTrue(stale.state.focused().demoOutstanding());
        assertTrue(stale.actions.isEmpty());
    }

    @Test public void unsupportedMultipleFilterValuesDoNotPartiallyMutateState() {
        DialogueResult browse = turn(new DialogueState(), Act.request("browse"));
        DialogueResult result = turn(browse.state, Act.inform("part", "back-seat"), Act.inform("theme", "family"), Act.inform("theme", "visibility"));
        assertEquals("repair.unresolved_input", result.trace.rule);
        assertNull(result.state.focused().part());
        assertNull(result.state.focused().theme());
        assertEquals(catalog, result.state.focused().choices());
    }

    @Test public void noMatchesDoesNotRelaxFiltersOrKeepInvalidChoices() {
        DialogueResult browse = turn(new DialogueState(), Act.request("browse"));
        DialogueResult result = turn(browse.state, Act.inform("part", "headlights"), Act.inform("theme", "family"));
        assertEquals("task.no_matches", result.trace.rule);
        assertTrue(result.state.focused().choices().isEmpty());
        assertEquals("family", result.state.focused().theme());
    }

    @Test public void traceCapturesInterpretationDecisionActionsAndUnchangedBeforeState() {
        DialogueState before = new DialogueState();
        DialogueResult result = turn(before, requestFeature("demonstrate", "headlights"));
        assertEquals("demonstrate.start", result.trace.rule);
        assertFalse(result.trace.binding.isEmpty());
        assertSame(before, result.trace.before);
        assertSame(result.state, result.trace.after);
        assertEquals(result.actions, result.trace.actions);
        assertNull(result.trace.before.focused());
        assertTrue(result.trace.after.focused().demoOutstanding());
    }

    @Test public void ordinalAndUnknownFeatureErrorsKeepThePendingQuestion() {
        DialogueResult offered = turn(new DialogueState(), Act.request("demonstrate"));
        for (Act act : Arrays.asList(Act.select(0), Act.select(99), Act.inform("feature", "invented"))) {
            DialogueResult result = turn(offered.state, act);
            assertEquals("repair.unresolved_input", result.trace.rule);
            assertEquals(Expectation.Kind.CHOICE, result.state.focused().expectation().kind);
            assertNull(result.state.focused().featureId());
        }
    }

    @Test public void answerAndAsideArgumentsKeepIndependentDestinationsRegardlessOfActOrder() {
        DialogueResult parent = turn(new DialogueState(), Act.request("demonstrate"));
        Act aside = related(requestFeature("explain", "headlights"), "aside");
        for (List<Act> acts : Arrays.asList(Arrays.asList(Act.inform("part", "back-seat"), aside),
                Arrays.asList(aside, Act.inform("part", "back-seat")))) {
            DialogueResult result = core.handleUserTurn(parent.state, acts);
            assertEquals(1, result.state.tasks().size());
            assertEquals(parent.state.focused().id(), result.state.focused().id());
            assertEquals("back-seat", result.state.focused().part());
            assertNull(result.state.focused().featureId());
            assertEquals(Arrays.asList(rearDisplays, rearAudio), result.state.focused().choices());
            assertTrue(result.actions.get(0).text.contains("Description of headlights"));
            assertEquals(SystemAction.Kind.ASK, result.actions.get(1).kind);
            assertNull(parent.state.focused().part());
        }
    }

    @Test public void oneTurnCanResolveDifferentFeatureValuesForParentAndRequestedGoal() {
        DialogueResult parent = turn(new DialogueState(), Act.request("demonstrate"), Act.inform("part", "back-seat"));
        DialogueResult result = turn(parent.state, Act.select(2), related(requestFeature("explain", "headlights"), "aside"));
        assertEquals("rear_audio", result.state.focused().featureId());
        assertTrue(result.actions.get(0).text.contains("headlights"));
        assertEquals(SystemAction.Kind.START_DEMO, result.actions.get(1).kind);
        assertEquals("rear_audio", result.actions.get(1).featureId);
        assertFalse(result.trace.binding.isEmpty());
    }

    @Test public void oneTurnCanBindAnswersToTwoExistingTasksBeforePolicyRuns() {
        DialogueResult parent = turn(new DialogueState(), Act.request("demonstrate"), Act.inform("part", "front-seat"));
        DialogueResult aside = turn(parent.state, related(Act.request("explain"), "aside"));
        DialogueResult result = turn(aside.state, Act.affirm(), Act.select(4));
        assertEquals(1, result.state.tasks().size());
        assertEquals("front_comfort", result.state.focused().featureId());
        assertTrue(result.actions.get(0).text.contains("Description of headlights"));
        assertEquals("front_comfort", result.actions.get(1).featureId);
        assertEquals(SystemAction.Kind.START_DEMO, result.actions.get(1).kind);
        assertNull(aside.state.tasks().get(0).featureId());
        assertNull(aside.state.tasks().get(1).featureId());
    }

    @Test public void updatingSavedTaskDoesNotRepeatFocusedQuestionAndIsUsedOnLaterResume() {
        DialogueResult parent = turn(new DialogueState(), Act.request("demonstrate"), Act.inform("part", "front-seat"));
        DialogueResult aside = turn(parent.state, related(Act.request("explain"), "aside"));
        DialogueResult answered = turn(aside.state, Act.affirm());
        assertEquals("task.updated_saved", answered.trace.rule);
        assertTrue(answered.actions.isEmpty());
        assertEquals("front_comfort", answered.state.tasks().get(0).featureId());
        assertEquals(Expectation.Kind.CHOICE, answered.state.focused().expectation().kind);
        DialogueResult resumed = turn(answered.state, Act.select(4));
        assertEquals("front_comfort", resumed.actions.get(1).featureId);
    }

    @Test public void affirmationAndCorrectionCanBelongToDifferentTasks() {
        DialogueResult parent = turn(new DialogueState(), Act.request("demonstrate"), Act.inform("part", "front-seat"));
        DialogueResult aside = turn(parent.state, related(Act.request("explain"), "aside"));
        DialogueResult result = turn(aside.state, Act.affirm(), Act.deny(), Act.inform("feature_id", "headlights"));
        assertEquals("front_comfort", result.state.focused().featureId());
        assertTrue(result.actions.get(0).text.contains("Description of headlights"));
        assertEquals("front_comfort", result.actions.get(1).featureId);
    }

    @Test public void compatibleExpectationsRouteParameterValuesToDifferentExistingTasks() {
        DialogueState state = new DialogueState();
        Task parent = new Task(Task.Kind.BROWSE);
        parent.expectation = Expectation.value("part");
        Task focused = new Task(Task.Kind.BROWSE);
        focused.expectation = Expectation.value("theme");
        state.tasks.add(parent);
        state.tasks.add(focused);

        DialogueResult result = turn(state, Act.inform("part", "back-seat"), Act.inform("theme", "family"));
        assertEquals("back-seat", result.state.tasks().get(0).part());
        assertNull(result.state.tasks().get(0).theme());
        assertEquals("family", result.state.focused().theme());
        assertNull(result.state.focused().part());
        assertNull(result.state.tasks().get(0).expectation());
        assertNull(result.state.focused().expectation());
        assertEquals(SystemAction.Kind.PRESENT_FEATURES, result.actions.get(0).kind);
        assertEquals(1, result.actions.size());
    }

    @Test public void ambiguousDenialDoesNotBroadcastOrPartiallyApplyMultiTaskValues() {
        DialogueState state = new DialogueState();
        Task parent = new Task(Task.Kind.BROWSE);
        parent.expectation = Expectation.value("part");
        Task focused = new Task(Task.Kind.BROWSE);
        focused.expectation = Expectation.value("theme");
        state.tasks.add(parent);
        state.tasks.add(focused);

        DialogueResult result = turn(state, Act.deny(), Act.inform("part", "back-seat"), Act.inform("theme", "family"));
        assertEquals("repair.unresolved_input", result.trace.rule);
        assertNull(result.state.tasks().get(0).part());
        assertNull(result.state.focused().theme());
        assertEquals(Expectation.Kind.VALUE, result.state.tasks().get(0).expectation().kind);
        assertEquals(Expectation.Kind.VALUE, result.state.focused().expectation().kind);
    }

    @Test public void denialDoesNotGuessBetweenAnotherTasksConfirmationAndAnInformationUpdate() {
        DialogueState state = new DialogueState();
        Task parent = new Task(Task.Kind.BROWSE);
        parent.expectation = Expectation.value("part");
        Task focused = new Task(Task.Kind.EXPLAIN);
        focused.expectation = Expectation.confirmation("feature_id", "headlights");
        state.tasks.add(parent);
        state.tasks.add(focused);

        DialogueResult result = turn(state, Act.deny(), Act.inform("part", "back-seat"));
        assertEquals("repair.unresolved_input", result.trace.rule);
        assertNull(result.state.tasks().get(0).part());
        assertEquals(Expectation.Kind.CONFIRMATION, result.state.focused().expectation().kind);
    }

    @Test public void conflictInOneDestinationRollsBackOtherDestinationsAndTaskCreation() {
        DialogueResult parent = turn(new DialogueState(), Act.request("demonstrate"), Act.inform("part", "back-seat"));
        DialogueResult result = turn(parent.state, related(requestFeature("explain", "headlights"), "aside"),
                Act.select(1), Act.inform("feature_id", "rear_audio"));
        assertEquals("repair.unresolved_input", result.trace.rule);
        assertEquals(1, result.state.tasks().size());
        assertNull(result.state.focused().featureId());
        assertEquals(Expectation.Kind.CHOICE, result.state.focused().expectation().kind);
        assertEquals(Arrays.asList(rearDisplays, rearAudio), result.state.focused().choices());
    }

    @Test public void requestArgumentsDoNotLeakIntoParentFilters() {
        DialogueResult parent = turn(new DialogueState(), Act.request("browse"), Act.inform("part", "back-seat"));
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("operation", "explain");
        slots.put("relation", "aside");
        slots.put("part", "headlights");
        DialogueResult aside = turn(parent.state, new Act(Act.Type.REQUEST, slots));
        assertEquals("back-seat", aside.state.tasks().get(0).part());
        assertEquals("headlights", aside.state.focused().part());
        assertEquals(Expectation.Kind.CONFIRMATION, aside.state.focused().expectation().kind);
    }

    @Test public void sameKindRequestsMergeBindingsOnlyWhenTheyResolveToSameTask() {
        DialogueResult parent = turn(new DialogueState(), Act.request("demonstrate"));
        DialogueResult conflict = turn(parent.state, requestFeature("demonstrate", "headlights"), Act.select(1));
        assertEquals("repair.unresolved_input", conflict.trace.rule);
        assertNull(conflict.state.focused().featureId());
        DialogueResult aside = turn(parent.state, related(requestFeature("demonstrate", "headlights"), "aside"), Act.select(1));
        assertEquals(2, aside.state.tasks().size());
        assertEquals("rear_displays", aside.state.tasks().get(0).featureId());
        assertEquals("headlights", aside.state.focused().featureId());
        assertEquals("headlights", aside.actions.get(0).featureId);
    }

    @Test public void explicitRequestCanResolveItsOrdinalFromExistingChoicesWithoutUpdatingTheirOwner() {
        DialogueResult browse = turn(new DialogueState(), Act.request("browse"), Act.inform("part", "back-seat"));
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("operation", "explain");
        slots.put("relation", "aside");
        slots.put("ordinal", "2");
        DialogueResult result = turn(browse.state, new Act(Act.Type.REQUEST, slots));
        assertTrue(result.actions.get(0).text.contains("Description of rear_audio"));
        assertNull(result.state.focused().featureId());
        assertEquals(browse.state.focused().id(), result.state.focused().id());
    }

    @Test public void multipleNewRequestsAndUnknownRelationsRemainExplicitlyUnsupported() {
        DialogueResult parent = turn(new DialogueState(), Act.request("browse"));
        DialogueResult multiple = turn(parent.state, related(Act.request("explain"), "aside"), Act.request("demonstrate"));
        DialogueResult unknown = turn(parent.state, related(Act.request("explain"), "invented"));
        assertEquals("repair.unresolved_input", multiple.trace.rule);
        assertEquals("repair.unresolved_input", unknown.trace.rule);
        assertEquals(1, multiple.state.tasks().size());
        assertEquals(1, unknown.state.tasks().size());
    }

    private static Act related(Act request, String relation) {
        Map<String, String> slots = new LinkedHashMap<>(request.slots);
        slots.put("relation", relation);
        return new Act(request.type, slots);
    }

    private DialogueResult turn(DialogueState state, Act... acts) {
        return core.handleUserTurn(state, Arrays.asList(acts));
    }

    private static Act requestFeature(String operation, String id) {
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("operation", operation);
        slots.put("feature_id", id);
        return new Act(Act.Type.REQUEST, slots);
    }

    private static Feature feature(String id, String part, String theme) {
        return new Feature(id, id, Collections.emptyList(), Collections.singletonList(part),
                Collections.singletonList(theme), "Description of " + id);
    }
}
