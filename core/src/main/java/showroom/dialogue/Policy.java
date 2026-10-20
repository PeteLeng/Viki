package showroom.dialogue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Handwritten next-action selection, organized by Task kind. No mutations or I/O. */
final class Policy {
    static final class Decision {
        final String rule;
        final String reason;
        final List<SystemAction> actions;

        Decision(String rule, String reason, SystemAction action) {
            this(rule, reason, Collections.singletonList(action));
        }

        Decision(String rule, String reason, List<SystemAction> actions) {
            this.rule = rule;
            this.reason = reason;
            this.actions = actions;
        }
    }

    static Decision next(DialogueState state, List<Feature> catalog, Binding.TaskBinding turn) {
        Task task = state.focused();
        if (turn != null && turn.denied && turn.values.isEmpty()) {
            return new Decision("task.rejected_confirmation", "The user rejected the proposed value.",
                    SystemAction.ask(task, "Which feature did you mean instead?", Expectation.value("feature_id")));
        }
        Feature selected = task.featureId == null ? null : StateUpdate.find(catalog, task.featureId);
        if (task.featureId != null && (selected == null || !selected.matches(task.part, task.theme))) {
            return repair("The selected feature is unavailable or conflicts with the current filters.");
        }
        switch (task.kind) {
            case BROWSE:
                if (selected != null) return new Decision("browse.selected", "A selection does not request a demo.",
                        SystemAction.reply("Selected " + selected.name + ". Request explain or demonstrate to continue."));
                List<Feature> matches = matches(catalog, task);
                if (matches.isEmpty()) return noMatches();
                return new Decision("browse.present_matches", "Browsing filters are optional.", SystemAction.present(task, matches));
            case EXPLAIN:
                if (selected == null) return askFeature(task, catalog);
                return new Decision("explain.respond", "The feature is resolved; use catalog content.",
                        SystemAction.reply(task, selected.name + ": " + selected.description));
            case DEMONSTRATE:
                if (selected == null) return askFeature(task, catalog);
                if (task.demoOutstanding) {
                    boolean same = task.demoRequest.featureId.equals(task.featureId);
                    return new Decision("demonstrate.active", "No automatic interruption/replacement rule has been chosen.",
                            SystemAction.reply(same ? "That demonstration has already been requested."
                                    : "Selection updated. Replacing a running demonstration is not implemented."));
                }
                if (task.demoRequest != null && task.demoRequest.featureId.equals(task.featureId)) {
                    return new Decision("demonstrate.already_requested", "Do not restart completed work on a follow-up.",
                            SystemAction.reply("That demo was already requested. Make an explicit demonstration request to repeat it."));
                }
                return new Decision("demonstrate.start", "A resolved demonstration request is ready.", SystemAction.start(task));
            default:
                throw new IllegalStateException("Unknown Task kind.");
        }
    }

    private static Decision askFeature(Task task, List<Feature> catalog) {
        List<Feature> choices = matches(catalog, task);
        if (choices.isEmpty()) return noMatches();
        if (choices.size() == 1) {
            Feature only = choices.get(0);
            return new Decision("task.confirm_feature", "One candidate remains; confirm the proposed value.",
                    SystemAction.ask(task, "Did you mean " + only.name + "?",
                            Expectation.confirmation("feature_id", only.id)));
        }
        return new Decision(task.kind.name().toLowerCase(java.util.Locale.ROOT) + ".ask_feature",
                "The requested feature is unresolved.",
                SystemAction.ask(task, "Which feature?", Expectation.choice(choices)));
    }

    private static List<Feature> matches(List<Feature> catalog, Task task) {
        List<Feature> matches = new ArrayList<>();
        for (Feature feature : catalog) if (feature.matches(task.part, task.theme)) matches.add(feature);
        return matches;
    }

    private static Decision noMatches() {
        return new Decision("task.no_matches", "Do not silently relax filters.",
                SystemAction.reply("No features match those filters. Change the part or theme."));
    }

    static Decision repair(String reason) {
        return new Decision("repair.unresolved_input", reason, SystemAction.reply(reason));
    }

    static Decision resume(DialogueState state, List<Feature> catalog, Binding.TaskBinding turn) {
        Task task = state.focused();
        if (turn != null) return next(state, catalog, turn);
        if (task.expectation != null) {
            String question = "Back to your previous question: which feature?";
            if (task.expectation.kind == Expectation.Kind.CONFIRMATION) {
                question = "Back to your previous question: did you mean " + task.expectation.proposedValue + "?";
            } else if (task.expectation.kind == Expectation.Kind.VALUE) {
                question = "Back to your previous question: what " + task.expectation.parameter + "?";
            }
            return new Decision("task.resume_question", "Restore the saved expectation and offered order.",
                    SystemAction.ask(task, question, task.expectation));
        }
        if (task.kind == Task.Kind.BROWSE && task.featureId == null && !task.choices.isEmpty()) {
            return new Decision("task.resume_choices", "Restore the retained offered order.", SystemAction.present(task, task.choices));
        }
        return next(state, catalog, null);
    }

    static Decision savedTaskUpdated() {
        return new Decision("task.updated_saved", "Updated saved Task values; the focused interaction is unchanged.", Collections.emptyList());
    }

    static Decision flow(Flow.Event event) {
        if (event.text == null || event.text.isEmpty()) {
            return new Decision("flow.observe", "Observed " + event.kind + "; no reply needed.", Collections.emptyList());
        }
        return new Decision("flow.output", "Present " + event.kind + " without rerunning the user-turn policy.",
                SystemAction.reply(event.text));
    }
}
