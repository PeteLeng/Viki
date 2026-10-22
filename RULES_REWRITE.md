# Task rules rewrite — lean first slice

**Implemented: Welcome → name → Play offer, direct Play, and single-value Play input.**
One user act per turn, one named value per `inform`, and one pending question at a time.
A request may carry its own `flow_id`, `part`, and `theme` arguments together.

This follows the simplified contracts in `5452e65` and task-local direction in
`f3788b9`, with the subsequent discussion's smaller processing model. The wider
target remains in [MVP_DESIGN.md](MVP_DESIGN.md). Composite turns, List/Help,
controls, saved-question interruptions, and live switching are later slices.

## 1. Read the code in this order

All core files are in `core/src/main/java/showroom/dialogue/`:

| File | Responsibility |
| --- | --- |
| `DialogueCore.java` | Copy state, route one input, advance the resulting focus once |
| `WelcomeRules.java` | Greeting, session name, Play offer and its answer |
| `PlayRules.java` | Play admission, named values/references, confirmation, next question/start |
| `TurnContext.java` | Direct helpers for admission, completion, questions, commands, and trace |
| `PendingInteraction.java` | Owner plus the issued question's semantic payload |
| `DialogueAct.java` / `FlowCommand.java` | Communication versus application requests |

The handlers use ordered Java branches and named methods. A general rule-table
runner can wait until repeated patterns justify it.

## 2. One procedure, two substeps

```text
handleUserTurn(before, input):
    c = TurnContext(copy(before), catalog)
    if handleInput(c, input):
        advanceFocusedTask(c)
    return c.state, c.acts, c.commands, trace
```

Both substeps may mutate the private working state. `handleInput` can accept a
value, resolve a question, complete Welcome, and admit Play in one call. An
input handler returning false has handled the turn without requesting advancement;
it may have selected a diagnostic or just a greeting.

`advanceFocusedTask` inspects the resulting task once. It does not consume the
input again. Accepting Welcome's offer therefore cannot also answer Play's new
selection. There are no intermediate resolution reports, `Decision` objects,
property observers, or internal event queues in this slice.

`TurnContext` is a temporary argument bundle: working state, catalog, collected
outputs, and trace steps. Conversational context is the task stack, session name,
and pending interaction already in that state; there is no second context store.

## 3. Minimal memory and ownership

```text
DialogueState:
    tasks                    # top is conversational focus
    userName                 # session value, not a Welcome/Play parameter
    pendingInteraction?      # one active reply target

Task:
    id, kind                 # WELCOME or PLAY
    parameters               # Play: flow_id, part, theme
    progress                 # pending / start requested / app-reported outcome
    startCommand?            # exact request captured by the application listener

PendingInteraction:
    ownerTaskId
    question: DialogueAct    # sole proposal/choice payload
```

The interaction reuses the issued act instead of copying its proposal into
another object. Its owner is core metadata, never an NLU-produced Task ID.
The handler determines scope: `name` is stored in the session; a Play answer is
stored in that Play's parameters.

| Question | Meaning of its answer |
| --- | --- |
| `request(slot=name)` | Store `state.userName` |
| `select(flow_id=[A,B])` | Resolve an ordinal using exactly `[A,B]` |
| `confirm(flow_id=A)` | Accept/reject a proposed value, still unset until accepted |
| `confirm(task=play, ...)` | Accept/reject a proposed goal, not a boolean task parameter |

`confirm(task=play)` is the first-slice shorthand for a goal-adoption proposition.
It is emitted by Welcome; it does not itself create Play or start execution.
`greet()` and the session `name` are the other small additions to the v0 vocabulary.

Standing input rules live in the modules. For example, Play accepts a named theme
while its question asks for a flow. No separate global expectation registry or
concept-value subscription is needed.

## 4. Shared admission and bookkeeping

`activateTask(PLAY, arguments)` is used by direct requests and accepted offers:

1. Find the singleton Play anywhere on the stack, or prepare a new one.
2. Let `PlayRules.admit` validate/reconcile arguments.
3. After successful admission, complete Welcome and clear its question if present.
4. Add or refocus the admitted Play.

Invalid arguments leave Welcome and its question intact. A request does not
mean "push another task".

The first-slice Play admission choices are explicit:

- An existing pending Play retains omitted parameters. Supplied values update it.
- A request equivalent to outstanding execution does not issue another start.
- Changing outstanding execution is reported as unsupported without changing it.
- After completion/failure, an explicit Play request reopens the same task,
  retaining omitted parameters. This is the retry/repeat path.
- An ordinary information update or callback cannot restart completed work.

Other helpers combine mutation with the bookkeeping it requires:

```text
ask(question):
    store pendingInteraction
    append the same question act to output

start(play):
    record the owning startCommand and START_REQUESTED progress
    append the command to output

completeTask(task):
    clear its question
    remove the task
```

These helpers collect output; adapters run after the host accepts the returned
state. The Flow listener captures the command object, so callbacks associate
with that particular request. Application evidence establishes running, completed,
or failed; issuance alone does not establish running.

## 5. Happy-path traces

| Input | Input handler | Advancement |
| --- | --- | --- |
| `greet()` | Admit Welcome | Ask for name |
| `inform(name=Sam)` | Store session name; clear name question | Offer Play |
| `affirm()` | Admit Play through the shared helper; complete Welcome | Ask for flow |
| `inform(theme=family)` | Update this Play; invalidate dependent flow question | Offer matching flows |
| `inform(choice=ordinal(2))` | Assign the actual second offered flow; clear question | Record/start that flow |

Direct `request(task=play)` goes through the same admission helper and starts at
the flow question. `request(task=play, flow_id=headlights)` can start immediately.
Direct Play can bypass Welcome before or after the name is supplied.

The name is optional for Play. Greeting after a name was collected goes straight
to the offer. A greeting while Play exists is simply acknowledged.

One matching catalog flow is proposed with `confirm(flow_id=...)`; acceptance
sets the value, denial asks for a replacement. This exercises value confirmation
without inventing NLU confidence. An uncertainty envelope remains integration work.

## 6. Small but important disciplines

- Validate a complete request's arguments before assigning any of them. Unknown
  fields and conflicting constraints are not silently dropped.
- Clear a question when answered, rejected, superseded, or invalidated. An unrelated
  name correction does not clear the Play offer.
- Only an outstanding selection resolves ordinals. Its choices disappear as
  reference context when cleared; neither trace nor catalog ordering is a fallback.
- `affirm()` after `select` repeats that same selection, without guessing a flow.
- A theme alone does not create Play. It is information for an established goal.
- `any` is remembered as an explicit no-preference filter, distinct from unset.
- Callback handling bypasses user handlers/advancement. Obsolete and terminal
  command callbacks cannot affect a newer start.
- Unsupported composite input is rejected as a whole before mutation. Its NLU
  representation is preserved rather than partially executed.

Malformed JSON is an adapter error; unsupported/unresolved meaning is an ordinary
core outcome (`inform(error=..., description=...)`); application failure is a Flow
notification; broken internal invariants remain programming errors.

## 7. Verification and next boundary

`bash ./gradlew test` checks the greeting handoff, direct admission, themes,
confirmation versus selection, offered-order lifetime, original-state preservation,
start ownership/retries, and the CLI/NLU boundaries. Tests assert semantic state,
acts, and commands rather than requiring particular core phrasing.

The next design decisions are scoped additions: List/Help lifecycle, suspended
interactions, controls/live switching, then multi-act ownership. The broader
[reference cases](DIALOGUE_EXAMPLES.md) describe those target behaviors; passing
this slice does not establish conformance to all 36 cases.
