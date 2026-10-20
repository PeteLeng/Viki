# Showroom dialogue MVP: implementation design

## 1. Purpose and agreed constraints

Build a small, inspectable dialogue manager for a showroom application. Users can discover features, ask for explanations, and start or stop demonstrations. Features may have several independent categories, including parts/locations and themes.

The first interactive interface is a CLI. The dialogue policy is handwritten. The production NLU is supplied by another team; an LLM temporarily implements the same NLU contract for development and testing.

**Keep the MVP small and the terminology stable.** The goal is a maintainable core that can be experimented with, rather than a general dialogue framework. Data shapes in examples illustrate the discussed behavior; they do not settle every storage or API detail. Unsettled choices are listed in section 16.

Agreed constraints:

- **NLU has text-only runtime input:** `interpret(text) -> acts + slots`.
- The development LLM receives the same runtime input as production NLU. Conversation context belongs to the dialogue core.
- **Use Task consistently** for a goal and its remembered progress.
- **Binding, state update, and policy have distinct responsibilities.** In this document, policy specifically means next-action selection.
- The existing flow interface is conceptually `flow.list()`, `flow.start(name_or_id, listener)`, and `flow.stop()`.
- `flow.list()` returns pure data. It does not display or manage a presented list.
- Flow listeners provide lifecycle notifications, progress, and narration. They do not ask the user questions or wait for user answers.
- Logging and observability are first-class parts of the MVP.
- The CLI chat interface should be available from the earliest working version.
- A presented choice list remains available for subsequent references until that presentation is superseded or its conversational context is abandoned. Answering its question does not, by itself, discard the list.

The examples below are language-neutral. Exact SDK signatures and the external NLU payload format still need to be aligned with the actual integrations.

## 2. Stable vocabulary

### 2.1 Core concepts

| Term | Definition |
| --- | --- |
| **User act** | What the user communicated in this turn, with associated slot values or references. A turn may contain several acts. |
| **Task** | A user goal together with remembered values and progress. A Task may span several turns. |
| **Task kind** | The reusable type of goal: initially `browse`, `explain`, or `demonstrate`. |
| **Dialogue state** | The Tasks and other session information the dialogue manager must remember. |
| **System action** | An operation selected by policy: ask, reply, present information, or request an app operation. |
| **Catalog** | Feature/flow definitions used for lookup, filtering, explanations, and execution binding. This is domain data. |

An act and a Task are different:

```text
User: "Start a demo."

NLU:
  request(operation=demonstrate)

Dialogue state:
  Task(kind=demonstrate, feature_id=unknown)
```

The following turn updates the same Task:

```text
User: "The second one."

NLU:
  select(ordinal=2)

Dialogue state:
  The same Task, now with a selected feature.
```

The shorthand `feature_id=unknown` above corresponds to an unset value in the Task's `parameters` record.

### 2.2 Processing responsibilities

| Step | Responsibility |
| --- | --- |
| **Binding** | Determine what an act refers to: which Task, parameter, feature, or question. |
| **State update** | Apply the interpreted turn: update, create, replace, suspend, or complete Tasks according to rules. |
| **Policy** | Choose the next system action from the resulting situation. |

**Binding identifies the target and meaning. State-update rules determine the Task changes. Policy chooses what the system does next.**

These are processing steps inside one dialogue core, not three required services or frameworks. The policy can inspect the just-processed turn or notification as well as the updated state.

State bookkeeping also records the consequences of issued system actions. Asking a question records its expectation; requesting a demo start records that execution has been requested. Policy selects these actions; it does not directly mutate state or call the flow API.

### 2.3 Expectations and retained choices

| Term | Definition |
| --- | --- |
| **Expectation** | The current outstanding interaction used to interpret a response, such as choosing a value or confirming a proposed value. |
| **Retained choices** | The alternatives actually offered to the user, including their order and conversational context. They can remain useful after their question is answered. |

These describe information the dialogue manager remembers. Their different lifetimes are agreed: an expectation can be resolved while the choices remain available for references and corrections. Their final field layout is not yet settled; a separate Presentation entity or ID scheme is not required by this agreement.

### 2.4 Naming rules

- A Task instance is the runtime record previously described informally as a frame. Use **Task**, not a separate Frame type.
- Active and suspended describe a Task's position in the conversational focus stack. Do not maintain separate `active_frame` and `suspended_frame` copies.
- Task `parameters` are its remembered slot values. NLU slot values describe the current turn; binding and state-update rules determine which become remembered values.
- Use **system action**, rather than switching between action, effect, and backend call.
- If implementation code uses an `Input` wrapper, it contains either a parsed user turn or a flow notification. It is not another semantic interpretation layer.
- Trace details are observational data, not an additional dialogue abstraction.

## 3. Ownership and integration boundaries

| Information or operation | Owner |
| --- | --- |
| Available flows, stable IDs, and flow metadata | Flow module, exposed through `flow.list()` |
| Actual execution of a flow | Flow module |
| Lifecycle, progress, and narration production | Flow module/listener |
| Filtering and deciding which alternatives to offer | Dialogue policy using catalog data |
| Snapshot of what was offered and its order | Dialogue state |
| Meaning of an answer for a Task | Binding and state-update rules |
| Choosing the next response or operation | Dialogue policy |
| Printing, displaying, or speaking selected output | Output adapter |
| Calling `flow.start()` and `flow.stop()` | Flow adapter |
| Recording the interpretation, state changes, decisions, and app results | Logging around dialogue processing |

### 3.1 Catalog as data

Use `flow.list()` as the catalog source where possible. Supply additional metadata only where its return data lacks the descriptions, aliases, or categories needed by the dialogue manager.

Illustrative feature metadata, rather than a prescribed `flow.list()` schema:

```yaml
id: rear_displays
name: Rear displays
aliases:
  - back-seat screens
  - rear-seat entertainment
parts: [back-seat]
themes: [entertainment, family]
description: "..."
```

Parts and themes are independent facets. A feature can match several categories. The relationship between feature IDs and flow IDs should follow the existing API; no additional ID mapping is assumed here.

Adding a feature that uses existing behavior should normally add catalog data and an execution binding. A genuinely new interaction can require a new Task handler. Avoid creating a new intent or Task kind for every feature.

This reduces dialogue-code changes. The amount of NLU training required for new feature names depends on the external team's entity strategy, such as refreshable aliases, generic feature mentions, or fixed labels.

### 3.2 Available flows are not presented choices

```text
flow.list() returns:       [A, B, C, D]

After filtering, we say:  "You can try B or D."

User:                    "The second one."
```

The answer refers to **D**. Binding must use the offered `[B, D]` snapshot, not a fresh `flow.list()` result.

The stored snapshot is conversational memory, not a second authoritative catalog. Availability may change after presentation; execution can reject an unavailable selection, but the system must not silently reinterpret its ordinal as a different feature.

## 4. Text-only NLU contract

```text
Development: ASR text -> LLM NLU adapter        -> acts + slots
Deployment:  ASR text -> production NLU adapter -> acts + slots
                                                   |
                                            Same dialogue core
```

The runtime interface is:

```text
interpret(text) -> acts + slots
```

The development LLM can be configured with the agreed semantic schema and static vocabulary. It must not receive the current Task, pending expectation, presented choices, or recent conversation as extra runtime context. Contextual references are resolved by the dialogue core.

### 4.1 Initial act vocabulary

| Act | Meaning | Examples |
| --- | --- | --- |
| `request` | Request an operation | `request(operation=browse, theme=family)`; `request(operation=demonstrate)` |
| `inform` | Supply information | `inform(part=back-seat)` |
| `select` | Select a referenced alternative | `select(ordinal=2)` |
| `affirm` | Affirm a proposition established by context | `affirm` |
| `deny` | Reject a proposition established by context | `deny` |

The initial task-request operations are `browse`, `explain`, and `demonstrate`.

The NLU representation and product behavior for controls such as stop or cancel have not been settled. The existence of `flow.stop()` does not, by itself, define their user-act encoding or Task semantics.

### 4.2 Agreed examples of contextual binding

| User says | NLU output | Dialogue core resolves |
| --- | --- | --- |
| "The second one" | `select(ordinal=2)` | Which feature occupies position 2 in the relevant presented choices |
| "Back seats" | `inform(part=back-seat)` | Which Task parameter this updates |
| "Yes" | `affirm` | What proposition or question is being affirmed |
| "No, the front seats" | `deny` + `inform(part=front-seat)` | Whether this corrects the current selection or filter |

**The NLU recognizes the expression; the dialogue core binds it to conversational state.**

### 4.3 Multi-act turns and contract fidelity

Process the complete turn before choosing the next system action.

```text
deny + inform(part=front-seat)
```

can jointly represent a correction. Reacting to `deny` first and running policy before seeing the `inform` would lose that meaning.

The NLU adapter must preserve associations between acts and their values. A single flat slot map can be insufficient when one utterance answers a question and requests another operation.

The examples require the NLU contract to preserve references, affirmation/denial, and associations between acts and slot values. For an aside versus replacement, the dialogue manager also needs enough information to distinguish "before that" from "instead". How production NLU exposes that distinction remains to be agreed; no additional modifier enum is prescribed here.

Do not give the LLM mock semantic capabilities through its contract that the production adapter cannot supply. Direct act injection can test a dialogue behavior before production NLU supports its required distinctions. Report that integration gap explicitly in evaluation.

### 4.4 Validation

Validate the incoming payload before binding. Resolve feature mentions against the catalog and validate any supplied IDs. Preserve ambiguity instead of inventing a unique reference.

An unknown reference, invalid ordinal, malformed result, or unsupported interpretation needs an explicit repair path. A syntactically valid result is not proof of correct understanding. Evaluate NLU quality separately from dialogue-core correctness.

## 5. Dialogue state and Task focus

### 5.1 Task state and the focus stack

The conceptual state is a stack of Tasks. The top has conversational focus; lower entries are saved continuations.

```text
Task:
  kind                    # browse | explain | demonstrate
  parameters              # remembered values, defined by Task kind
  expectation             # the current question/interaction, when present
```

This is a conceptual sketch, not a complete storage schema. Dialogue state also needs to retain the offered choices after their question is answered. Section 6 describes that requirement without imposing a registry, a Task-status enum, or a presentation-ID structure.

A Task can lose conversational focus while its flow continues running. The callback still needs to be associated with the relevant operation/Task; the mechanism depends on the flow API.

### 5.2 Task parameters

Initial handlers need approximately:

| Task kind | Relevant parameters |
| --- | --- |
| `browse` | Optional part/theme filters; optionally a selected `feature_id` |
| `explain` | A resolved `feature_id` |
| `demonstrate` | A resolved `feature_id`; optional filters used to find it |

The examples use part/theme filters. How multiple values are combined, including AND versus OR behavior, has not been settled.

NLU slots such as `part` and `theme` map to the appropriate Task filter fields through binding rules. These are controlled mappings, not arbitrary model-generated state patches.

Browsing filters are optional. Missing filters do not automatically create questions. Demonstrating or explaining a particular feature requires that feature to be resolved.

### 5.3 Task-oriented policy does not mean pushing for every turn

Task-oriented describes how behavior is organized. The stack describes where conversation should return.

| Situation | Operation |
| --- | --- |
| User answers a question | Update the current/relevant Task |
| User supplies extra information | Update all applicable parameters before selecting the next action |
| User corrects a value | Update the Task and invalidate dependent values or choices |
| Assistant asks for clarification | Record an expectation on the existing Task |
| User asks an aside and intends to return | Push another Task |
| Aside finishes | Pop it and resume the saved Task |
| User replaces the request | Replace the focused Task, abandoning the replaced context as appropriate |

An assistant utterance or ordinary clarification does not require another Task.

```text
[Browse]
    "Before choosing, explain the headlights."
[Browse, Explain]
    explanation finishes
[Browse]
```

The table covers clear answers, corrections, asides, and replacements. A default for ambiguous or unmarked topic changes has not been chosen.

### 5.4 Completion is not necessarily abandonment

Answering a question or finishing a demo does not, by itself, supersede its offered choices. The agreed retention rule still applies: keep them until superseded or their conversational context is abandoned.

This does not prescribe keeping completed Tasks on the stack, archiving all Tasks, or transferring data between Task kinds. Those storage and transition details remain open.

## 6. Expectations and retained choices

### 6.1 Why a null parameter is insufficient

Both of these interactions can occur while `feature_id` is null:

> "Which feature: rear displays or rear audio?"

> "Did you mean rear audio?"

But:

- `select(ordinal=2)` resolves against the first interaction.
- `affirm` resolves against the second interaction.

For the second interaction:

```yaml
expectation:
  kind: confirmation
  parameter: feature_id
  proposed_value: rear_audio
```

`feature_id: null` describes missing knowledge. It does not identify which question was issued, what alternatives were offered, or which value is being confirmed.

**Task parameters describe what we know or still need. An expectation records the conversational context established for obtaining or resolving it.**

### 6.2 Choice expectation example and naming

The choice-question example discussed was:

```yaml
expectation:
  kind: choice
  parameter: feature_id
  choices: [rear_displays, rear_audio]
```

| Field | Meaning |
| --- | --- |
| `kind` | The answer form, here choosing among alternatives |
| `parameter` | The Task parameter receiving the resolved answer |
| `choices` | The actual offered values in their presented order |

`choice` describes the answer form; `feature_id` describes its destination. For a confirmation, `proposed_value` supplies the proposition being affirmed or denied.

This illustrates the pending interaction, rather than finalizing the retained-choice storage layout. The later agreement to retain choices after answering means that resolving the question must not discard those values or their context. The data can be shared; it does not require duplicate lists.

### 6.3 One current expectation, multiple useful acts

The working MVP approach discussed is one outstanding expectation per Task. A suspended Task keeps the context needed to continue. Missing several parameters does not mean several questions are outstanding.

The current expectation guides binding; it does not restrict all permissible input. Relevant overanswers, corrections, and new requests remain possible.

### 6.4 Lifecycle of the question and its choices

1. Policy selects a question and the choices to offer, where applicable.
2. State bookkeeping records the corresponding expectation and offered order.
3. The output adapter renders those same choices in that order.
4. A resolved answer updates the relevant Task parameter and resolves the expectation.
5. The offered choices remain available for later references until superseded or their context is abandoned.
6. A correction can invalidate choices or values that depended on the corrected information.

Example after answering:

```text
Task parameter: feature_id = rear_audio
Outstanding question: none
Retained choices: [rear_displays, rear_audio]
```

Now:

> "Actually, the first one."

can still resolve to `rear_displays`. The resolved question is not reopened merely to interpret the reference. State-update rules handle the new selection, and policy chooses the next action.

The agreement establishes reference resolution. It does not settle whether changing a selection while a demo runs automatically stops or replaces that demo.

### 6.5 Clarification versus a separate form Task

For this MVP, ordinary clarification belongs to the existing Task:

```text
Task: demonstrate
Missing information: feature_id
Next action: ask which feature
```

The goal remains the same. Several parameters can be collected over multiple turns, updating the one outstanding expectation as needed.

A separate Task is appropriate when an interaction has its own substantial, independently managed lifecycle and continuation. If such a configuration procedure is added later, it can be another Task kind. It does not require introducing a separate fundamental Form abstraction.

Rule of thumb:

> A question about the current Task belongs in that Task. A separate interaction that needs its own continuation may deserve another Task.

## 7. Binding and state-update rules

### 7.1 Binding

Binding resolves meaning without executing app operations or creating Tasks.

Use the relevant Task, its outstanding expectation, and retained choice context to resolve the turn. The current question provides context without preventing extra information or a new request. Do not broadcast a slot value to every Task that has a similarly named parameter.

The discussion established these responsibilities and examples, rather than a complete priority algorithm for searching every saved Task.

Examples:

- `select(ordinal=2)` uses the relevant offered list's exact order; ordinals are one-based.
- A named selection resolves against the offered choices and catalog aliases.
- `affirm` binds to an outstanding confirmation; it does not choose an arbitrary item from a choice question.
- `deny + inform(part=front-seat)` is considered as a complete turn before deciding whether it is a correction.
- With no applicable expectation or retained choices, an ordinal remains unresolved.

### 7.2 State update

Apply the complete bound turn before next-action selection:

- Create a Task for a new goal according to the request and focus rules.
- Update explicitly targeted or contextually bound parameters.
- Resolve or invalidate expectations as appropriate.
- Replace corrected values and invalidate dependent selections or offered choices.
- Preserve unrelated valid parameters.
- Push an aside, replace an abandoned request, or resume a saved continuation according to explicit rules.

For example, changing a part filter from back-seat to front-seat can invalidate a selected rear-seat feature and the old filtered choice list. It should not silently retain that feature just because its ID is still valid in the overall catalog.

For the agreed correction example, the new part replaces the corrected part constraint. A general language for adding/removing collection-valued filters has not been specified.

### 7.3 Overanswering example

> Assistant: Which area interests you?
>
> User: The back seats, especially entertainment for kids.

Possible semantic content:

```text
inform(part=back-seat)
inform(themes=[entertainment, family])
```

The pending area question is answered and the theme information is also applied. Policy runs after both updates, so it does not ask for information already supplied.

### 7.4 Focus-change example

> Assistant: Front or rear seats?
>
> User: Rear, and something for kids.
>
> Assistant: Here are two matching features: rear entertainment and rear climate controls.
>
> User: Before choosing, what does the headlight demo do?
>
> Assistant: [Catalog-backed explanation.] Back to the rear-seat features: entertainment or climate controls?
>
> User: The first one.

Mechanism:

- The first reply supplies both location and theme.
- The explanatory aside temporarily takes conversational focus.
- The browse Task retains its parameters, offered choices, and outstanding expectation.
- Returning to that Task restores the relevant choice context.

The dialogue core determines which existing Task is suspended and later resumed. The production NLU representation needed to distinguish the aside request must be checked with the NLU team.

## 8. Core entry points and system actions

### 8.1 Outer text entry point

```text
handle_input(text)
  -> NLU.interpret(text)
  -> handle_user_turn(state, acts)
```

The inner entry point remains available for direct act injection and core testing.

Conceptually:

```text
handle_user_turn(state, acts)
  -> updated_state, system_actions

handle_flow_event(state, notification)
  -> updated_state, system_actions
```

The catalog is data available to the core. The implementation also exposes the binding, state-change, and decision information needed for tracing.

If these entry points share one internal function, an `Input` wrapper means exactly one of:

- A user turn containing NLU acts and slot values.
- A flow notification containing an application occurrence.

It does not mean raw narration should go through NLU.

### 8.2 System actions

| System action | Implementation responsibility |
| --- | --- |
| Ask for a choice | Record the expectation and offered order; output those same choices |
| Ask for a value or confirmation | Record the expectation; output the corresponding question |
| `Reply(content)` | Print, display, or speak selected content |
| `PresentFeatures(choices)` | Present the selected alternatives and remember their order |
| `StartDemo(id)` | Adapter calls `flow.start(id, listener)` |
| `StopDemo()` | Adapter calls `flow.stop()` according to the actual API contract |

Offering a list can establish reference context even when it is not phrased as a direct question. Asking a choice question establishes both the outstanding expectation and its choices.

System actions are descriptions of selected operations. Policy returns them; adapters perform the actual calls. A response or presentation is an output operation, while start/stop are flow operations.

`flow.list()` retrieves data. `PresentFeatures(...)` presents selected information. They are different operations in the agreed API.

### 8.3 Recording issued actions

The returned state must reflect the actions being issued:

```text
bind the user turn
apply state changes from that turn
select the next system action
record the action's state consequences
dispatch the action through an adapter
```

Record which question was issued so its answer can be bound correctly. Associate flow callbacks with the relevant operation/Task. The exact scheduling and callback-association mechanism depends on the actual API.

This bookkeeping does not broaden the meaning of policy: policy still selects the next action.

## 9. Flow execution and streamed notifications

### 9.1 Runtime loop

```text
CLI / ASR text
      |
      v
handle_input(text)
      |
      v
NLU: text -> acts + slots
      |
      v
Dialogue core
  binding -> state update -> policy
      |
      v
System actions
      +--> Output adapter --> CLI / UI / speech
      |
      +--> Flow adapter --> flow.start() / flow.stop()
                                |
                                v
                             listener
                                |
                                +--> Dialogue core
```

### 9.2 Listener handling

Use the actual callback data provided by the flow API. The agreed categories are:

| Notification content | Dialogue handling |
| --- | --- |
| Lifecycle information | Update relevant Task knowledge; policy can respond to the reported outcome |
| Progress | Display or otherwise use the progress information where useful |
| Narration | Present supplied content; it does not establish a user question |

Completion and failure are examples of outcomes the integration may report. This table does not prescribe an internal lifecycle enum or require additional SDK events.

Policy may select no system action. An unrelated progress callback must not repeat an outstanding question or restart a demonstration.

Flow notifications do not pass through NLU and do not automatically create Tasks or expectations. Flow narration is output content, not a user utterance.

### 9.3 Execution bookkeeping

The flow module owns execution. Keep only the callback association and information actually needed by the dialogue rules; use the real flow API to determine that minimum.

A callback needs to reach the relevant operation/Task even if conversational focus has changed. A listener's captured context may be sufficient. A separate flow-state machine, command registry, run-ID scheme, or Task archive is not part of the settled MVP design.

Changing conversational focus does not itself mean pausing or stopping a demo. Conversely, knowing which feature was selected does not mean it has started successfully; use the actual API result/notification.

### 9.4 What remains an integration decision

The API names alone do not settle concurrency, replacement of a running demo, stop acknowledgment, or callback scheduling. In particular, the discussion did not establish a mandatory stop/wait/restart sequence or require serializing the entire NLU request with callbacks.

The agreed boundary is that user turns and flow notifications both reach the dialogue core, where state updates and next-action selection happen. The integration must preserve the context needed to interpret each input correctly.

## 10. Initial Task policies

### Browse

- Query the catalog using optional filters.
- Present a useful shortlist when there are matches.
- Remember the exact shortlist and its order.
- Ask a narrowing question only when useful; do not require every filter to be filled.
- On no matches, explain the result and offer a specific adjustment. Do not silently relax constraints.
- A selection updates the selected feature/context. Starting a demo requires a demonstration request or an explicitly established demonstration-selection context.

### Explain

- Resolve the requested feature.
- Clarify consequential ambiguity.
- Use catalog-backed explanation content.
- Complete an aside and return to its saved Task where applicable.

### Demonstrate

- Resolve the requested feature; use optional filters to offer relevant choices.
- Ask only for information required to select/start the requested demonstration.
- Start once when ready and execution permits it.
- Observe lifecycle callbacks; do not declare successful execution merely because a command was sent.
- Resolve changed selections against retained choices. Behavior when an existing demo is running remains to be decided with the flow integration.
- On completion, retain useful reference context until it is superseded or abandoned.

Shared repair functions can handle unresolved references, invalid selections, no matches, NLU failures, and execution failures. Their details are supplied by the relevant Task.

## 11. Worked end-to-end examples

### 11.1 Start a demo, choose, and execute

```text
User: Start a demo.
NLU: request(operation=demonstrate)

Binding:
  identify the requested operation; no feature supplied

State update:
  create Task(kind=demonstrate, feature_id=unknown)

Policy:
  ask the user to choose rear displays or rear audio

Bookkeeping/output:
  record the ordered choices and their expectation
  render the same ordered choices
```

```text
User: The second one.
NLU: select(ordinal=2)

Binding:
  position 2 in the offered list is rear_audio
  destination is the demonstration Task's feature_id parameter

State update:
  feature_id = rear_audio
  expectation = null
  retain the offered choices

Policy:
  StartDemo(rear_audio)

Bookkeeping/execution:
  associate the listener with the requested operation/Task
  adapter calls flow.start(rear_audio, listener)
```

Subsequent lifecycle, progress, and narration notifications follow the flow-event path.

### 11.2 Change the choice after starting

```text
Retained choices: [rear_displays, rear_audio]
Selected/running: rear_audio
Outstanding expectation: none

User: Actually, the first one.
NLU: select(ordinal=1)

Binding:
  retained choices resolve the reference to rear_displays

State update:
  apply the new selection to the relevant Task

Policy:
  choose the next action using the Task and actual flow behavior
  automatic interruption/restart has not been specified
```

This is why offered choices must outlive their answered question. The example establishes the reference's meaning, not a stop/restart protocol.

### 11.3 Confirmation is different from choosing

```text
Assistant: Did you mean rear audio?

expectation:
  kind: confirmation
  parameter: feature_id
  proposed_value: rear_audio

User: Yes.
NLU: affirm

Binding:
  affirm the proposed value rear_audio

State update:
  feature_id = rear_audio
  resolve the confirmation
```

After "Which feature: rear displays or rear audio?", the same `affirm` does not select a feature. The expectation shape determines the interpretation.

### 11.4 Correction and invalidation

```text
Current filters: part=back-seat
Current choices: rear-seat features

User: No, the front seats.
NLU: deny + inform(part=front-seat)

Binding/state update:
  interpret the complete turn as a correction where context supports it
  replace the part constraint
  clear an incompatible selected feature
  invalidate the old filtered choices and their question

Policy:
  query/present appropriate front-seat features
```

## 12. First-class observability

Observability is part of the processing contract from the first CLI slice.

The trace should make the following visible:

- User text and NLU output.
- The expectation/retained choices used and the resulting binding.
- State changes.
- Which policy rule selected the next action and why.
- Requested system actions and relevant flow callbacks/results.
- Enough turn/context association and timing to understand the sequence.

Use stable rule IDs, for example:

```text
browse.present_matches
demonstrate.ask_feature
demonstrate.start
explain.respond
repair.unresolved_reference
```

The core exposes these details and the surrounding application records them. Exact log storage, format, and diagnostic command syntax are implementation choices.

The goal is to distinguish a parsing, binding, state-update, policy, output, or execution problem. Direct act injection and conversation scenarios support this from the first version. Full deterministic replay tooling was discussed as a possible aid, not agreed as a required MVP subsystem.

## 13. CLI-first implementation

The CLI is the first interactive interface, not a late integration step.

Useful testing capabilities discussed:

| Capability | Behavior |
| --- | --- |
| Normal text | Pass text through the configured NLU adapter |
| Direct act injection | Supply a user turn's acts and slots without NLU |
| State inspection | Inspect Tasks, focus, expectations, and retained choices |
| Trace inspection | Follow interpretation, state changes, and selected actions |

The exact CLI commands have not been fixed. A small fake flow adapter can supply catalog data and representative callbacks while the core is developed; an extensive lifecycle simulator is not a prerequisite.

Direct act injection answers the important debugging question:

> Did NLU misunderstand this, or did the dialogue manager mishandle the intended interpretation?

## 14. Suggested implementation sequence

### Milestone 1: contracts and one observable CLI slice

1. Bring up the CLI and tracing with one Task and a small real or representative catalog.
2. Connect the text-only LLM NLU adapter using the five discussed acts, while aligning its payload with the NLU team. Allow handwritten acts for independent core testing.
3. Connect a real or small fake flow adapter and make this sequence observable:

```text
request demonstration
-> offer choices
-> select a choice
-> request flow start
-> receive flow notifications, narration, and progress
```

### Milestone 2: conversational behavior

- Add browse and explain handlers around the same core contracts.
- Support extra information in one turn, corrections, confirmations, and invalid references.
- Add explicit push/pop/replace behavior and returning from an explanatory aside.
- Support retained choice references after answering and during/after a demonstration.

### Subsequent integration

- Run the production NLU through the same CLI and conversation scenarios.
- Resolve the remaining API-dependent behavior using the actual flow contract.
- Connect Android output and input adapters while preserving the core contracts.
- Evaluate production NLU separately from the core scenarios exercised with handwritten acts.

## 15. MVP acceptance scenarios

Verify state changes, selected feature IDs, focus behavior, and app operations; do not require exact wording except where presentation ordering matters.

1. A direct demonstration request for a resolved feature starts the intended flow once.
2. A demonstration request with no resolved feature offers relevant choices.
3. "The second one" resolves to the second offered choice, not the second current catalog entry.
4. "Back seats" binds to the relevant Task parameter.
5. "Yes" resolves a confirmation but does not arbitrarily answer a choice question.
6. `deny + inform(part=front-seat)` is processed together as a correction where appropriate.
7. Overanswering supplies both location and theme before policy chooses another action.
8. A correction invalidates incompatible selections and filtered choices.
9. An aside preserves and restores the parent Task's context.
10. Explicit replacement abandons the replaced context; stale choices are not implicitly reused.
11. A choice remains referenceable after its question is answered, until superseded or abandoned.
12. Flow progress/narration does not repeat questions, restart flows, or pass through NLU.
13. Flow notifications are handled in their relevant Task/operation context, even if focus changed.
14. No matches, ambiguous references, invalid ordinals, malformed NLU output, and execution errors can be diagnosed through the trace.

## 16. Decisions not yet settled

These are gaps to resolve as needed, rather than hidden defaults in the MVP:

- **NLU payload details:** association of acts and values, expression of corrections and aside/replacement cues, and the encoding of conversational controls.
- **Retained-choice layout:** how outstanding expectations and longer-lived choices share storage, including when a conversational context is considered abandoned. The retention behavior is agreed; a Presentation entity, IDs, Task archive, and completed-Task retention strategy are not.
- **Flow behavior:** actual callback payloads, start/stop return guarantees, and what should happen when a user changes selection during a running demo. Concurrency limits and a stop/wait/restart protocol have not been chosen.
- **Query behavior:** how multiple part/theme values combine. No AND/OR default has been agreed.
- **Ambiguous focus changes:** the default when a new request does not clearly indicate an aside or replacement.
- **Concrete integration details:** available `flow.list()` metadata, callback scheduling/association, and CLI/log formatting.

Keep these decisions small and tied to observed scenarios. Preserve the agreed vocabulary and the separation of binding, state update, and next-action policy.
