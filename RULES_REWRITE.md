# Task rules rewrite — proposal v1

**Design checkpoint, not an implemented rewrite.** The Java prototype still uses `Binding.bind`, `StateUpdate.apply`, and `Policy.next`. This proposal replaces that arrangement with task-local input transitions and policy rules. Behavioral requirements are in [MVP_DESIGN.md](MVP_DESIGN.md); examples are in [DIALOGUE_EXAMPLES.md](DIALOGUE_EXAMPLES.md).

## 1. Direction

Use one core step and a small rule module for each goal kind: Play, List, and Help. A module contains two rule tables:

| Rules | Read | Produce |
| --- | --- | --- |
| Input rules | Input and relevant Task/question context | Updates to the working dialogue state, including stack changes |
| Policy rules | Updated state, input, and update outcome | A `Decision`: DialogueActs, FlowCommands, and explicit lifecycle changes |

There need not be a separate, whole-turn, side-effect-free binding phase. Contextual interpretation can happen inside the applicable input transition. Keep small lookups such as `resolveOrdinal(question, n)` read-only; mutate state only through the selected transition.

Task memory remains `id`, `kind`, `parameters`, `progress`, and `expectation`. Play is singleton across the entire stack. Questions belong to Tasks; their choices disappear as reference context when the expectation is cleared. Rule modules hold definitions, not another copy of session state.

## 2. Proposed step

```text
step(before, input):
    working = copy(before)

    report = apply_input_rules(before, working, input)
    decision = choose_next(working, input, report)

    record_issued_outputs(working, decision)
    apply_selected_lifecycle_changes(working, decision)

    return working, decision.outputs, trace
```

`before` supplies the original question/reference context and pre-turn focus. `working` receives changes and supplies current invariant checks. This prevents a new Help request from stealing an earlier answer or being canceled by the same turn's “forget that.”

The report is temporary processing data: rules applied, addressed Tasks, and unresolved inputs. It is not a second Task store or a model-generated state patch. Policy receives the input even when no parameter changed.

The host accepts the returned state before rendering acts or dispatching commands. A user pause request does not set execution to paused; the application result establishes that fact. Flow notifications remain distinct inputs with their originating operation/Task association.

## 3. Java rule tables

“Data-like” means a consistent declaration of the matched input, conditions, and consequence. Start with named Java predicates and operations. YAML could later reference registered operation names; arbitrary Java lambdas are not automatically serializable rule data.

The following are **API sketches**, not classes already present in the prototype:

```java
final class InputRule {
    String id;
    Predicate<InputContext> matches;       // read-only
    Consumer<InputContext> update;         // edits working state
}

final class PolicyRule {
    String id;
    Predicate<PolicyContext> matches;      // read-only
    Function<PolicyContext, Decision> choose;
}
```

Contexts are temporary argument bundles: input/group, relevant Task, catalog, state views, and diagnostics. Input updates get the working Task; policy gets a read-only view. Task ownership comes from core rules, never NLU-generated runtime IDs. The examples below assume the unambiguous T1 context from the walkthroughs; saved-task answer priority remains open.

A small, illustrative part of `PlayRules` could be:

```java
static final List<InputRule> INPUT = Arrays.asList(
    input("play.discovery_information",
          discoveryInformForUnresolvedFlow(),
          PlayRules::applyDiscoveryInformation),

    input("play.accept_confirmation",
          affirmationOfValueConfirmation(),
          PlayRules::acceptConfirmationAndInformation)
);

static final List<PolicyRule> POLICY = Arrays.asList(
    policy("play.pause",
           requestIs(PAUSE),
           PlayRules::decidePause),

    policy("play.need_flow",
           userReplyForThisTask().and(flowIdMissing()),
           PlayRules::chooseFlowQuestion)
);
```

Factories such as `input` and `policy` construct rule objects; they do not execute their operations. The first matcher claims a complete named part/theme-information input while no flow is selected, not arbitrary fields from a larger input. This is a subset of the eventual rule table.

For that discovery-information case, the update is ordinary Java:

```java
private static void applyDiscoveryInformation(InputContext c) {
    Task task = c.workingTask();
    boolean partChanged = assignIfSupplied(task, PART, c.input());
    boolean themeChanged = assignIfSupplied(task, THEME, c.input());

    if ((partChanged || themeChanged)
            && isFlowChoiceQuestion(task.expectation)) {
        task.expectation = null;
    }
}
```

The helper assigns accepted supplied values only. Handling references, existing selections, or coupled corrections needs the corresponding complete rule; do not use this narrow example as a universal `inform` handler.

`chooseFlowQuestion` reuses a still-valid flow-selection question or computes matches from current parameters and chooses an appropriate question/result. It returns data; the common recorder installs any issued question on its owner.

`decidePause` can select a pause command for running play work, or choose information/no output otherwise. Its request matcher works even with no play Task or with Help focused. **A matched `Decision.noOutput()` is handled, not permission to fall through to an unrelated question rule.**

## 4. Execution discipline

These are proposed runner rules to make precise before implementation:

1. **Match without mutation; update only `working`.** A failed condition must not leave edits behind. Pure reference helpers may be called within an input transition.
2. **Consume complete acts/groups.** Keep request arguments together and handle `deny() + inform(...)` as a correction unit. A matcher must account for what it claims; do not silently discard fields or apply conflicting assignments by last-write-wins.
3. **Finish the user turn before policy.** Input rules can create/update/cancel Tasks, but must not issue speech or call the Flow API between acts.
4. **Make priority explicit.** The proposed default is specific input patterns before generic ones and first-match selection per policy decision point. Exact grouping, overlap, and partial-turn handling still need agreement.
5. **Run per trigger, not until a fixed point.** Include the input/event in applicability. A still-missing parameter must not cause repeated output in a loop, or a question on every progress notification.
6. **Centralize output bookkeeping.** Record questions and command ownership before dispatch. Complete/refocus Tasks through explicit lifecycle changes, not because any `inform` happened or a diagnostic rule ID matched a string.

Immediate Help completion can require a further policy decision for restored focus. That scheduling must not replay the original input or introduce a `continue` Task. Unresolved conversational meaning is recorded for policy rather than converted automatically into exceptions or mandatory error replies.

## 5. Two rule traces

Both start with a play Task awaiting a flow. See [C10](DIALOGUE_EXAMPLES.md#c10) and [C11](DIALOGUE_EXAMPLES.md#c11) for the conversations.

| Input | Input-rule result | Policy result |
| --- | --- | --- |
| `inform(theme=family, part=back-seat)` | Assign both values; invalidate the old dependent flow-choice question | `play.need_flow` offers rear displays/rear audio; recorder stores the `select` expectation |
| `affirm()` after that `select` | No applicable confirmation acceptance; no chosen flow or parameter change | The same need-flow policy can repeat the valid selection; no special exception/repair Task |

For `inform(choice=ordinal(2))`, an input rule can read the actual pre-turn selection, resolve its value, write the corresponding parameter, and clear the answered expectation. No separate global `Binding.Result` is required, and no answered choice list survives in state.

## 6. Migration and next implementation slice

| Current code | Intended destination |
| --- | --- |
| `Binding.bind` | Task-local input interpretation/transitions plus small shared lookup helpers |
| Domain branches in `StateUpdate.apply` / `applyValues` | The relevant task's input rules, including singleton admission and dependency checks |
| `Policy.next` / `askFeature` | Task-local policy tables and named decision functions |
| `StateUpdate.record` | Common issued-output bookkeeping; explicit task-specific lifecycle decisions |
| Legacy `Act`, `SystemAction`, Task fields, NLU/CLI schema | The agreed DialogueAct / FlowCommand / minimal Task contracts |

First implementation sequence:

1. Define the minimal rule/context/decision records and validation boundary; keep the existing `core`/`cli` module split.
2. Implement one Play slice: named values, outstanding selections, confirmations, and the C10/C11 traces. Verify original-state preservation and expectation clearing.
3. Add singleton admission, global controls, Help/cancellation, and multi-act ownership. Test C18–C29, especially cleared-list failures and controls while Help has focus.
4. Migrate adapters, injection syntax, and tests together. Check callbacks cannot repeat questions/restart flows; evaluate NLU separately.

Before generalizing the runner, settle saved-context priority, input-group consumption/conflicts, new-play switching, and cancellation of live play work. Keep detailed or exceptional operations as small Java methods; promote repeated operations into reusable declarations only when it improves readability.
