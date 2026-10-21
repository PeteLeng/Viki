# Showroom dialogue MVP — Java

A reusable Java dialogue core and an early CLI prototype.

## Design status

[`MVP_DESIGN.md`](MVP_DESIGN.md) defines the **target v0 contract**;
[`DIALOGUE_EXAMPLES.md`](DIALOGUE_EXAMPLES.md) supplies its conversation examples.

The runnable commands and implementation notes below describe the earlier Java
prototype: `operation=browse|explain|demonstrate`, user `select(ordinal=...)`,
request relations, and retained choices. Migration follows the rule-module design
discussion. The new semantic notation is not yet accepted `/inject` JSON, and
pause/resume are target API additions.

## Build and run

Use JDK 21 (available in the development environment) and the included Gradle
wrapper. Nothing needs to be installed globally.

```sh
./gradlew test
./gradlew :cli:run --console=plain -q
```

On Windows, use `gradlew.bat` for the same commands. From Git Bash,
`bash ./gradlew ...` also works.

There are just two modules:

- `core`: a dependency-free Java library, compiled against Java 8 APIs and
  bytecode for consumption by the Android app.
- `cli`: a Java application. Gson handles the development adapter's JSON.

There is no Android app scaffold, Android Gradle plugin, or SDK configuration.
This follows the selected Java-library build target. The SDK was located using
`es` at `C:\Users\ghost\AppData\Local\Android\Sdk`; this pure Java build does
not need it. Android UI, ASR, and the real flow implementation are integration
points, not implementations supplied by this repository.

## Try the CLI without an LLM

The default flow adapter reads sample feature data from
`cli/src/main/resources/catalog.json`. Its demonstrations synchronously emit
narration, progress, and completion. They are fake showroom data/operations.

Paste these lines into the CLI:

```text
/inject {"acts":[{"type":"request","slots":{"operation":"demonstrate"}}]}
/inject {"acts":[{"type":"select","slots":{"ordinal":"2"}}]}
/inject {"acts":[{"type":"select","slots":{"ordinal":"1"}}]}
/state
```

The first request offers the catalog choices. The next line selects rear audio.
The fake flow completes. The following line selects rear displays using the
**same retained order**, even though the original question was answered.

Commands:

| Command | Purpose |
| --- | --- |
| Normal text | Pass only that text through the configured NLU adapter |
| `/inject JSON` | Supply acts directly, bypassing NLU |
| `/aside JSON` | Shorthand for adding `relation: "aside"` to the request act |
| `/replace JSON` | Shorthand for adding `relation: "replace"` to the request act |
| `/state` | Inspect Tasks, expectations, retained choices, and outstanding demo requests |
| `/trace` | Inspect the last turn or flow-notification trace |
| `/quit` | Exit |

The interactive CLI uses a single stdout writer so each trace appears before
its corresponding dialogue output, including flow callbacks and input errors:

```text
===== TRACE =====
{ ... pretty-printed trace ... }

===== OUTPUT =====
Which feature?
1. Rear displays [rear_displays]
2. Rear audio [rear_audio]
```

`/state` and `/trace` use labeled sections too. Diagnostic choice lists
contain ordered feature IDs, omitting static catalog metadata such as aliases,
categories, and descriptions. The LLM adapter still receives the full catalog.
Redirect stdout to retain the full labeled transcript. The previous stderr-only
trace redirection no longer applies to the interactive CLI; there is no separate
logging service or replay subsystem.

A short scripted example is in `examples/demo.txt`:

```sh
./gradlew :cli:run --console=plain -q < examples/demo.txt
```

## Temporary text-only LLM NLU

An optional adapter uses an OpenAI-compatible chat-completions HTTP endpoint
supporting JSON-object output. Configure the complete endpoint URL and model:

```sh
export NLU_URL="https://your-provider.example/v1/chat/completions"
export NLU_MODEL="your-model"
export NLU_API_KEY="your-key"
./gradlew :cli:run --console=plain -q
```

`NLU_API_KEY` can be omitted for a local endpoint. No live endpoint or credentials
are bundled. Without configuration, use act injection; there is no pretend
rule-based replacement for production NLU.

Each HTTP call has exactly a static system message and the current user text.
The system message contains the act schema and catalog vocabulary. It never
contains Task state, offered choices, or previous turns.

The temporary JSON envelope is:

```json
{
  "acts": [
    {"type": "deny", "slots": {}},
    {"type": "inform", "slots": {"part": "front-seat"}}
  ]
}
```

The five acts are `request`, `inform`, `select`, `affirm`, and `deny`.
Slot values are strings. Implement `Nlu.interpret(String)` to normalize the
production team's payload into the same `List<Act>`. This envelope is local
adapter syntax, not a claim that the external team has approved that format.

A request can carry an optional string slot, `relation`, with value `aside` or
`replace`. It describes the user's explicit relationship between goals (such
as “before that” or “instead”), not a stack operation or Task ID. When omitted,
a same-kind request continues the focused Task; an unrelated new request asks
for clarification rather than assuming a focus transition. This normalized
qualifier needs to be aligned with the production team's payload.

Arguments of a requested goal belong in **that request's slots**. Separate
`inform`, `select`, `affirm`, and `deny` acts answer existing Task context. If
there is no existing Task, separate supplied values can bind to the requested
goal. This preserves the difference between answering current work and
describing a new request in the same turn.

| Text | Acts | Resolved by the core |
| --- | --- | --- |
| “Start a demo” | `request(operation=demonstrate)` | Create/update a demonstration Task |
| “The second one” | `select(ordinal=2)` | Resolve the actual offered feature at position 2 |
| “Back seats” | `inform(part=back-seat)` | Update the relevant Task parameter |
| “Yes” | `affirm` | Affirm a pending confirmation, not an arbitrary choice |
| “No, the front seats” | `deny` + `inform(part=front-seat)` | Apply the whole-turn correction before policy |

## Core responsibilities

| Term/step | Meaning |
| --- | --- |
| User act | What the user expressed this turn; several can occur together |
| Task | An ongoing goal with remembered parameters and progress |
| Binding | Resolve what the acts refer to, without mutating Tasks |
| State update | Apply values and create/update/push/replace Tasks |
| Policy | **Next-action selection only**; no state mutation or API calls |
| System action | Selected question, reply, presentation, or demo request |

The public processing API is:

```text
handleUserTurn(state, acts) -> updated state, system actions, trace
handleFlowEvent(state, originatingStartAction, event) -> updated state, system actions, trace
```

**All user turns use `handleUserTurn`**, including aside and replacement
requests. There are no `handleAside` or `handleReplacement` methods. The CLI
shortcuts only qualify the request act before calling the same entry point.
State update owns Task creation and stack transitions.

The caller records the returned state before dispatching the returned actions.
The core itself has no network, Android, or rendering dependencies. Binding,
state update, and policy are separate ordinary Java classes, not services.

### Binding destinations

One NLU output can bind to several Tasks. The internal binding result keeps:

```text
existing Task ID -> TaskBinding(values, affirmed, denied)
requested goal  -> TaskBinding(values), request kind, optional relation
```

The requested goal has no Task ID until state update resolves it to the current
Task or creates a new one. It is not merged with existing Task values merely
because it appears in the same turn. If both bindings resolve to the same Task,
different values for the same parameter are rejected atomically.

Binding uses the pre-turn state. For unqualified answers it checks compatible
expectations from the focused Task down through saved Tasks. Ordinals can also
use the nearest retained choices. Otherwise supplied information applies to the
focused Task. Affirmation needs a compatible outstanding confirmation. A denial
with a single unambiguous correction destination belongs to that correction.
If it could instead answer another Task's confirmation, or correct several
Tasks, the turn is rejected for clarification rather than guessed or broadcast.

State update applies all destinations before policy selects an action. Updating
only a saved Task does not repeat the focused Task's question. When an
explanatory aside finishes, resumption uses the parent's updated values.

Explanatory replies carry their owning Task ID. State update records the reply
and completes the explanatory aside; diagnostic rule identifiers do not drive
stack transitions.

An expectation records what was asked. A null feature alone cannot distinguish:

```text
“Which feature: rear displays or rear audio?”  -> select(ordinal=2)
“Did you mean rear audio?”                    -> affirm
```

Choice expectations share their immutable choice list with the Task. Resolving
the expectation leaves that list on the Task; no Presentation entity, IDs,
registry, or duplicate maintained lists are introduced.

`Flow.list()` supplies the catalog. `Flow.start(id, listener)` and `Flow.stop()`
are the app boundary. A start action is captured by its listener, so callbacks
refer to that exact request rather than whichever Task currently has focus.
`handleFlowEvent` returns state/actions/traces without calling NLU or rerunning
the user-turn policy.

## Examples

### Overanswer and correction

```text
/inject {"acts":[{"type":"request","slots":{"operation":"browse"}}]}
/inject {"acts":[{"type":"inform","slots":{"part":"back-seat"}},{"type":"inform","slots":{"theme":"family"}}]}
/inject {"acts":[{"type":"deny"},{"type":"inform","slots":{"part":"front-seat"}}]}
```

Both values are applied before presenting matches. The correction replaces the
part, retains the family theme, and invalidates the old filtered choices.

### Aside and return

```text
/replace {"acts":[{"type":"request","slots":{"operation":"demonstrate","part":"back-seat"}}]}
/aside {"acts":[{"type":"request","slots":{"operation":"explain","feature_id":"headlights"}}]}
/inject {"acts":[{"type":"select","slots":{"ordinal":"2"}}]}
```

```text
[Demonstrate]
[Demonstrate, Explain]
[Demonstrate]             # original question and offered order restored
```

### Confirmation

```text
/replace {"acts":[{"type":"request","slots":{"operation":"demonstrate","part":"front-seat"}}]}
/inject {"acts":[{"type":"affirm"}]}
```

One candidate remains, so the policy asks “Did you mean Front-seat comfort?”
Affirm resolves that proposed feature. Denying it instead asks for a feature
value, rather than immediately repeating the same proposal.

### One NLU output, two Task destinations

After a demonstration request has offered rear-seat choices, the user can both
select one and ask for an explanatory aside:

```text
/inject {"acts":[{"type":"request","slots":{"operation":"demonstrate","part":"back-seat"}}]}
/inject {"acts":[{"type":"select","slots":{"ordinal":"2"}},{"type":"request","slots":{"operation":"explain","feature_id":"headlights","relation":"aside"}}]}
```

The selection binds to the existing demonstration Task's `feature_id=rear_audio`.
The request's `feature_id=headlights` belongs to the explanation. State update
preserves both destinations. The assistant explains the headlights, then resumes
the demonstration Task and requests rear audio. The rear-seat filter does not
leak into the headlight explanation.

Two **existing** Tasks can also receive answers in one turn:

```text
/replace {"acts":[{"type":"request","slots":{"operation":"demonstrate","part":"front-seat"}}]}
/aside {"acts":[{"type":"request","slots":{"operation":"explain"}}]}
/inject {"acts":[{"type":"affirm"},{"type":"select","slots":{"ordinal":"4"}}]}
```

With the sample catalog, affirmation answers the saved demonstration's
front-comfort confirmation. The ordinal selects headlights for the focused
explanation. Both values are applied before the explanation and resumed demo
actions are selected.

## Explicit choices and remaining limits

These are concrete first-slice choices where the discussion left details open:

- **Filters:** one part and one theme per Task; when both are supplied both must
  match. Different values for the same Task parameter in one turn are rejected
  without partial updates.
  General multi-value AND/OR semantics are not implemented.
- **Choice storage:** the Task keeps the offered list, shared with a choice
  expectation. A new offered list supersedes it. An incompatible filter change
  invalidates it. Replacing that Task abandons it.
- **Completion:** a top-level Task stays as the current reference context.
  A completed explanatory aside pops back to its parent. General completion of
  other aside kinds remains a separate gap. There is no Task archive,
  completed-Task registry, or Task lifecycle enum.
- **Focus:** a request for the current Task kind continues that Task. A different
  kind needs an explicit request `relation` or clarification. `/aside` and
  `/replace` are only injection shorthands for that qualifier. This slice still
  supports one requested goal per turn, together with updates to multiple
  existing Tasks. Multiple new requests in one turn remain unsupported.
- **Unresolved target:** multiple candidates produce a choice question; one
  candidate produces a confirmation. Browsing does not require filters or
  automatically start a selected feature.
- **Execution:** a Task stores the last start action and one boolean indicating
  an outstanding demo request. This does **not** assert that the app started.
  The sample adapter treats `completed` and `failed` notifications as terminal.
  Map real callback semantics at integration time; there is no execution-status
  enum, run-ID scheme, or command registry.
- **Running-demo replacement:** changing selection while its request is
  outstanding updates the selection and reports that replacement is not
  implemented. It issues no stop/restart sequence. Completion does not
  automatically launch a queued replacement.
- **Callbacks:** a monitor serializes CLI state application and dispatch; NLU
  runs outside it. A real host can supply its own scheduling. Notifications for
  obsolete start actions are ignored. The sample flow finishes synchronously;
  it makes no assumptions about real SDK concurrency/stop guarantees.
- **Controls:** `Flow.stop()` exists at the integration boundary, but spoken
  stop/cancel semantics and controls are not invented by this slice.

The next integration work is agreeing the production NLU payload (particularly
aside/replacement cues) and adapting the actual flow listener. This code does
not claim to have validated the Android host or a live LLM provider.

Other audit gaps remain explicit: named-feature resolution is still catalog-wide
rather than context-first, and there is not yet a stop system action. These are
separate from the unified user-turn entry point and targeted binding changes.

## Verification

```sh
./gradlew test
./gradlew :cli:installDist
```

Tests cover binding, atomic multi-act corrections, choice retention/order,
confirmation versus selection, multi-Task destinations, request-slot ownership,
aside return, explicit context replacement,
callback ownership, obsolete callbacks, unsupported inputs, trace contents,
the CLI loop, and a local HTTP test of the stateless LLM adapter. The HTTP tests
do not require credentials or an external model.

The installable CLI is generated under `cli/build/install/cli/`. Android can
consume the Java library produced by `:core:jar` or include the `core` module.
