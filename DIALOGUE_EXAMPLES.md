# Dialogue v0: conversation reference

These cases illustrate [MVP_DESIGN.md](MVP_DESIGN.md). They are intended interpretations, not measured NLU outputs or runnable `/inject` JSON. Each case is independent unless stated otherwise.

**U/S** give an utterance and its DialogueActs. **Core/App** describe commands and application evidence. Bracketed acts belong to one turn, not competing interpretations. `T1`/`T2` are internal Task IDs; NLU never invents them. Wording and optional policy replies are illustrative; state, meaning, and offered order are the checks.

Catalog fixture from `cli/src/main/resources/catalog.json`:

| Flow ID | Part | Themes |
| --- | --- | --- |
| `rear_displays` | `back-seat` | entertainment, family |
| `rear_audio` | `back-seat` | entertainment, family |
| `front_comfort` | `front-seat` | comfort, family |
| `headlights` | `headlights` | visibility |

| Cases | Coverage |
| --- | --- |
| [C01](#c01)–[C07](#c07) | List, help, and play goals |
| [C08](#c08)–[C17](#c17) | References, questions, corrections, and no preference |
| [C18](#c18)–[C21](#c21) | Multiple acts and interruptions |
| [C22](#c22)–[C26](#c26) | Controls and singleton Play |
| [C27](#c27)–[C30](#c30) | Reference limits and unsuccessful queries |
| [C31](#c31)–[C36](#c36) | Facts, notifications, NLU, and scope boundaries |

## C01

**List flows.** Discovery parameters are optional.

- **U:** “Show me the available features.” → `request(task=list)`
- **S:** “Rear displays, rear audio, front-seat comfort, and headlights.” → `inform(flows=[rear_displays, rear_audio, front_comfort, headlights])`

`entity` defaults to `flow`. This informational output creates no expectation or ordinal context.

## C02

**Enumerate facets.** Separate queries:

- **U:** “What themes do you have?” → `request(task=list, entity=theme)`
- **S:** “Entertainment, family, comfort, and visibility.” → `inform(themes=[entertainment, family, comfort, visibility])`
- **U:** “Which parts can I explore?” → `request(task=list, entity=part)`
- **S:** “Back seats, front seats, and headlights.” → `inform(parts=[back-seat, front-seat, headlights])`

Values come from catalog metadata, not concept definitions or remembered preferences.

## C03

**Explain a concept.**

- **U:** “What do you mean by a theme?” → `request(task=help, topic=theme)`
- **S:** “A theme groups features by interests such as family or entertainment.” → `inform(topic=theme, description="Themes group features by interests such as family or entertainment.")`

`inform(topic=theme)` alone would supply no explanation. Contrast C02.

## C04

**Explain usage.** Authored help content; neither answer executes a command.

- **U:** “How do I start a feature?” → `request(task=help, topic=play)`
- **S:** “Ask to play a feature by name, or choose when prompted.” → `inform(topic=play, description="Ask to play a feature by name, or choose when prompted.")`
- **U:** “How do I pause playback?” → `request(task=help, topic=pause)`
- **S:** “Say ‘pause playback’ while a flow is running.” → `inform(topic=pause, description="Say 'pause playback' while a flow is running.")`

## C05

**Explain a flow.**

- **U:** “What does the headlight demo do?” → `request(task=help, topic=flow, flow_id=headlights)`
- **S:** “It demonstrates the available lighting patterns.” → `inform(flow_id=headlights, description="Demonstrates the available lighting patterns.")`

The surface word “demo” does not imply execution; the canonical goal is `help`.

## C06

**Direct play.** Assume execution is permitted and no previous execution needs switching.

- **U:** “Start rear audio.” → `request(task=play, flow_id=rear_audio)`
- **Core:** selects `FlowCommand.start(rear_audio)` and records its owner before dispatch.
- **App:** confirms running.
- **S:** “Rear audio is running.” → `inform(flow_id=rear_audio, execution_status=running)`

No compulsory selection question. No duplicate start on unrelated inputs. Success requires application evidence.

## C07

**Play needs a flow.**

- **U:** “Play a feature.” → `request(task=play)`
- **S:** “Rear displays, rear audio, front-seat comfort, or headlights?” → `select(flow_id=[rear_displays, rear_audio, front_comfort, headlights])`

```text
T1: kind=play
    parameters={flow_id: unset, part: unset, theme: unset}
    progress={execution_status: not_started}
    expectation=select(flow_id=[rear_displays, rear_audio, front_comfort, headlights])
```

The question belongs to T1; it is not another Task. Progress fields here are illustrative.

## C08

**Answer the offered order.** T1 is a play Task awaiting a flow.

- **S:** “Rear displays or rear audio?” → `select(flow_id=[rear_displays, rear_audio])`
- **U:** “The second one.” → `inform(choice=ordinal(2))`

Resolve through that question: set `T1.parameters.flow_id=rear_audio`, clear `T1.expectation`. Play policy may then start when permitted. Do not use a fresh catalog ordering.

## C09

**The same reference answers a different slot.**

- **S:** “Family or entertainment?” → `select(theme=[family, entertainment])`
- **U:** “The second one.” → `inform(choice=ordinal(2))`
- **S:** “Rear displays or rear audio?” → `select(flow_id=[rear_displays, rear_audio])`

The first answer sets `theme=entertainment`; the later question asks for `flow_id`. C08 and C09 have identical user text/NLU output. The core supplies the contextual meaning.

## C10

**Family/back-seat overanswer.** Continue from C07.

- **U:** “Something for the family, in the back seats.” → `inform(theme=family, part=back-seat)`
- **S:** “Rear displays or rear audio?” → `select(flow_id=[rear_displays, rear_audio])`

| Phase | Consequence |
| --- | --- |
| NLU | Supplies named values, no Task ID or chosen flow |
| `copy(before)` | New working state version; same logical Task T1 |
| `resolve_input` | Associates useful information with T1 despite its question asking for `flow_id` |
| `apply_input_rules` | Sets part/theme; invalidates the old choice question whose candidates no longer fit |
| `choose_next` | Matches both supplied values; selects the two-flow question |
| `record_issued_outputs` | Stores the selected question as T1's expectation |
| Lifecycle/host | No Task added/completed; host accepts state, then renders output |

Result: `flow_id=unset`, `part=back-seat`, `theme=family`, execution not started. The expectation itself contains the two choices; no additional list memory exists.

## C11

**“Yes” after selection.** Continue from C10.

- **U:** “Yes.” → `affirm()`
- **S:** “Rear displays or rear audio?” → `select(flow_id=[rear_displays, rear_audio])`

| Phase | Consequence |
| --- | --- |
| Resolution | No particular proposal/flow is supplied by affirming this selection |
| Input update | No accepted parameter change; expectation is not answered |
| Policy | Example choice: repeat the still-needed selection |
| Output recording | Reissued expectation has the same contents |
| Lifecycle | None |

Task values remain as in C10. Trace the unresolved answer; no exception, arbitrary choice, start command, or repair Task is required.

## C12

**Accept a proposed flow.** Given an unstarted play goal:

- **S:** “Did you mean headlights?” → `confirm(flow_id=headlights)`
- **U:** “Yes.” → `affirm()`

Accept `flow_id=headlights` and clear the confirmation. Policy may start when permitted. Asking the question did not itself commit its proposal.

## C13

**Affirm with more information.** Given a pending play goal:

- **S:** “Did you mean back-seat features?” → `confirm(part=back-seat)`
- **U:** “Yes, especially family features.” → `affirm(theme=family)`
- **S:** “Rear displays or rear audio?” → `select(flow_id=[rear_displays, rear_audio])`

Accept the proposed part and additional theme before choosing output. The new question replaces the resolved confirmation.

## C14

**Correction.** T1 already has `theme=family`.

- **S:** “Front-seat features?” → `confirm(part=front-seat)`
- **U:** “No, the back seats.” → `[deny(), inform(part=back-seat)]`
- **S:** “Rear displays or rear audio?” → `select(flow_id=[rear_displays, rear_audio])`

Use the replacement part, preserve the unrelated theme, and consider both acts before policy. Do not ask for a correction already supplied.

## C15

**Rejection without a replacement.**

- **S:** “Back-seat features?” → `confirm(part=back-seat)`
- **U:** “Not the back seats.” → `deny(part=back-seat)`
- **S:** “Which part would you prefer?” → `request(slot=part)`

The final question is an example policy choice. Do not assign back-seat, infer front-seat, or cancel the goal. Persistence of rejected facts remains a task-rule decision.

## C16

**No preference.**

- **S:** “Any particular theme?” → `request(slot=theme)`
- **U:** “No preference.” → `inform(value=any)`

The question supplies the destination: record `theme=any` and clear it. Contrast explicit wording:

- **U:** “Any theme is fine. Back seats, please.” → `inform(theme=any, part=back-seat)`

Unset and no preference differ. Without a contextual destination, unqualified `value=any` remains unresolved. `any` is never a literal flow ID.

## C17

**Named answer outside a shortlist.** A pending play goal has no part/theme restriction; policy offered only two candidates.

- **S:** “Rear displays or rear audio?” → `select(flow_id=[rear_displays, rear_audio])`
- **U:** “Headlights.” → `inform(flow_id=headlights)`

The known, compatible flow can be accepted. The shortlist is not a universal whitelist. This does not validate out-of-range ordinals or permit silently dropping conflicting constraints.

## C18

**A new goal references an outstanding question.** T1 is awaiting its flow to play.

- **S:** “Rear displays or rear audio?” → `select(flow_id=[rear_displays, rear_audio])`
- **U:** “Explain the second one.” → `request(task=help, topic=flow, choice=ordinal(2))`
- **S:** “Rear audio lets rear passengers listen through the rear-seat audio system.” → `inform(flow_id=rear_audio, description="Rear passengers can listen through the rear-seat audio system.")`

Resolve the reference from T1's question into Help's arguments. T1 has not thereby selected a flow to play; its question can remain outstanding and be reissued on return.

## C19

**Help interrupts an unanswered question.** T1 has back-seat/family parameters and no selected flow.

- **S:** “Rear displays or rear audio?” → `select(flow_id=[rear_displays, rear_audio])`
- **U:** “Before choosing, what does the headlight demo do?” → `request(task=help, topic=flow, flow_id=headlights)`
- **S:** “It demonstrates lighting patterns. Back to choosing: rear displays or rear audio?” → `[inform(flow_id=headlights, description="Demonstrates the available lighting patterns."), select(flow_id=[rear_displays, rear_audio])]`
- **U:** “The first one.” → `inform(choice=ordinal(1))`

Help's subject does not inherit T1's constraints. The informational output belongs to Help; the restored question belongs to T1. Its unanswered expectation survives suspension, so the final reference is valid. No `aside` field or `continue` Task.

## C20

**Answer plus separate Help in one turn.** T1 has the same selection as C19; no execution has started.

- **U:** “The second one. But first, explain headlights.” → `[inform(choice=ordinal(2)), request(task=help, topic=flow, flow_id=headlights)]`
- **S:** “It demonstrates the available lighting patterns.” → `inform(flow_id=headlights, description="Demonstrates the available lighting patterns.")`

T1 gets `flow_id=rear_audio` and clears its expectation; Help gets `flow_id=headlights`. Process the whole turn before output. After Help completes, Play policy may start rear audio. Do not reissue the answered question or retain its choices.

## C21

**Cancel and request another goal.** T1 is awaiting a flow and owns no execution.

- **U:** “Forget that request. Explain headlights instead.” → `[request(task=cancel), request(task=help, topic=flow, flow_id=headlights)]`
- **S:** “It demonstrates the available lighting patterns.” → `inform(flow_id=headlights, description="Demonstrates the available lighting patterns.")`

Cancel the pre-turn focus T1, not the newly created Help goal. Its question is abandoned; no execution needs stopping. No `replace` field is necessary.

## C22

**Stop while Help has focus.** Stack: `[T1: play(rear_audio, running), T2: help(awaiting flow_id)]`.

- **U:** “Stop playback.” → `request(task=stop)`
- **Core:** Play policy selects `FlowCommand.stop()`.
- **App:** confirms rear audio stopped.
- **S:** “Rear audio has stopped.” → `inform(flow_id=rear_audio, execution_status=stopped)`

Help's question remains unanswered. There is no Stop Task. Remembering T1's flow ID must not cause an automatic restart.

## C23

**Pause and resume.** Given T1 owns running headlights:

- **U:** “Pause playback.” → `request(task=pause)`
- **Core/App:** `FlowCommand.pause()`; application confirms pause.
- **S:** “Paused.” → `inform(flow_id=headlights, execution_status=paused)`
- **U:** “Resume playback.” → `request(task=resume)`
- **Core/App:** `FlowCommand.resume()`; application confirms running.
- **S:** “Resumed.” → `inform(flow_id=headlights, execution_status=running)`

These are target API additions. No runtime target slots or persistent control Tasks. Application evidence establishes outcomes.

## C24

**Control without applicable execution.** Either no play Task exists, or it is still awaiting a feature.

- **U:** “Pause playback.” → `request(task=pause)`
- **S, if policy chooses:** “Nothing is running.” → `inform(error=no_active_execution)`

No output is also permitted. The request is valid without NLU knowing execution state. Do not assume an awaiting Task is running or automatically clear/repeat its question. The same principle applies to resume/stop.

## C25

**Cancel Help while Play continues.** T1 owns running rear audio; focused T2 is asking which flow to explain.

- **S:** “Which feature would you like explained?” → `request(slot=flow_id)`
- **U:** “Never mind.” → `request(task=cancel)`
- **S, optionally:** “Rear audio is still running.” → `inform(flow_id=rear_audio, execution_status=running)`

Cancel T2 and its question, not T1's execution. Canceling T1 itself while execution is live needs a separately specified lifecycle rule.

## C26

**Singleton Play.** T1 is already awaiting a flow, with no part/theme constraint.

- **U:** “Play the headlights.” → `request(task=play, flow_id=headlights)`

Play rules may accept the value and start when permitted, but cannot push another Play. If T1 is already running rear audio, the same singleton invariant holds; switching/confirmation/command sequencing remains an open policy decision.

## C27

**Answered selections are forgotten as reference sources.**

- **S:** “Rear displays or rear audio?” → `select(flow_id=[rear_displays, rear_audio])`
- **U:** “The second one.” → `inform(choice=ordinal(2))`
- **State:** `flow_id=rear_audio`, `expectation=null`.
- **U, with no new question:** “Actually, the first one.” → `inform(choice=ordinal(1))`
- **S, for example:** “I can't identify that choice from an outstanding question.” → `inform(error=unresolved_reference)`

No old list, catalog reconstruction, or trace lookup may supply the reference. No guessed selection/start. Explicit named requests remain possible. This intentionally reverses the old retained-choice requirement.

## C28

**An informational list is not a selection question.**

- **U:** “Show me the features.” → `request(task=list)`
- **S:** “Rear displays, rear audio, front-seat comfort, and headlights.” → `inform(flows=[rear_displays, rear_audio, front_comfort, headlights])`
- **U:** “Play the second one.” → `request(task=play, choice=ordinal(2))`

The argument is unresolved: no `select` is outstanding. One possible repair admits the play goal and asks anew:

- **S:** “Rear displays, rear audio, front-seat comfort, or headlights?” → `select(flow_id=[rear_displays, rear_audio, front_comfort, headlights])`
- **U:** “The second one.” → `inform(choice=ordinal(2))`

Only this later answer uses the new selection. No expectation is attached retroactively to the earlier `inform`.

## C29

**Out-of-range ordinal.**

- **S:** “Rear displays or rear audio?” → `select(flow_id=[rear_displays, rear_audio])`
- **U:** “The fifth one.” → `inform(choice=ordinal(5))`
- **S, for example:** “Which one: rear displays or rear audio?” → `select(flow_id=[rear_displays, rear_audio])`

No parameter changes or start. The question remains unanswered. A larger catalog or another Task's list cannot make position 5 valid here. Trace the reason even without an error utterance.

## C30

**No matches.**

- **U:** “Show me front-seat visibility features.” → `request(task=list, part=front-seat, theme=visibility)`
- **S:** “There are no matching features.” → `inform(flows=[], part=front-seat, theme=visibility)`

Do not silently drop a constraint. Contrast an explicit new request:

- **U:** “Show me front-seat family features.” → `request(task=list, part=front-seat, theme=family)`
- **S:** “Front-seat comfort matches.” → `inform(flows=[front_comfort])`

## C31

**User confirmation of catalog facts.**

- **U:** “Is rear audio a back-seat feature?” → `confirm(flow_id=rear_audio, part=back-seat)`
- **S:** “Yes.” → `affirm(flow_id=rear_audio, part=back-seat)`
- **U:** “Is rear audio a front-seat feature?” → `confirm(flow_id=rear_audio, part=front-seat)`
- **S:** “No, it is a back-seat feature.” → `[deny(), inform(flow_id=rear_audio, part=back-seat)]`

These are catalog questions, not reflection, new preferences, or play requests. Shared act types do not imply identical state-update effects for both speakers.

## C32

**Notifications under another task's focus.** T1 owns running rear audio; T2 Help has an outstanding question.

- **App:** narration from T1's execution.
- **S, optionally:** “Rear passengers can listen through the rear-seat audio system.” → `inform(description="Rear passengers can listen through the rear-seat audio system.")`
- **App:** reports completion.
- **S, optionally:** “Rear audio has completed.” → `inform(flow_id=rear_audio, execution_status=completed)`

Update the owning play work. Bypass NLU; do not answer/repeat T2's question or restart T1.

## C33

**Execution failure.**

- **U:** “Play the headlights.” → `request(task=play, flow_id=headlights)`
- **Core:** selects and records `FlowCommand.start(headlights)`.
- **App:** reports start failure.
- **S:** “The headlights could not be started.” → `inform(flow_id=headlights, execution_status=failed)`

Distinguish understanding, command issuance, and execution outcome in the trace. No false success or automatic restart caused by mere failure handling.

## C34

**Outside v0 / still to specify.** These are boundary cases, not supported gold interpretations.

| Input | Required boundary |
| --- | --- |
| “Which theme did I choose earlier?” | Reflection excluded; no user `request(slot=theme, target=current_task)` |
| “Return to my saved request” | No explicit `continue` operation; ordinary Help completion can still restore a Task |
| Invented Task IDs or `task_ref(...)` | Not NLU output |
| “Stop headlights” while rear audio runs | Qualified controls need agreement; never silently erase the qualifier |
| “What does it do?” | Reference payload needs agreement; never fabricate an ID using hidden history |
| “List everything except back-seat features” | Preserve negation; compound negative-query representation is not settled |
| “Family or entertainment” versus “Both family and entertainment” | OR/AND must not collapse into the same unexplained array |

Unsupported/uncertain interpretation is an NLU-result condition, not a new dialogue-act tag.

## C35

**NLU contrasts.**

| Text | Intended interpretation |
| --- | --- |
| “What themes do you have?” | `request(task=list, entity=theme)` |
| “What do you mean by theme?” | `request(task=help, topic=theme)` |
| “How do I pause playback?” | `request(task=help, topic=pause)` |
| “Pause playback.” | `request(task=pause)` |
| “What does the headlight demo do?” | `request(task=help, topic=flow, flow_id=headlights)` |
| “Play the headlights.” | `request(task=play, flow_id=headlights)` |
| “The second one.” | `inform(choice=ordinal(2))`, independently of the unseen question |
| “No preference.” | `inform(value=any)`, without a guessed destination |

“Themes?” or “Tell me about themes” may mean enumeration or explanation. Candidate turns `[request(task=list, entity=theme)]` and `[request(task=help, topic=theme)]` are alternatives, not a two-request turn. Uncertainty transport and any default/clarification policy need agreement. Evaluate held-out text/ASR paraphrases, not just these examples.

## C36

**Request-shaped answers and multiple goals.** Given an existing play selection:

- **S:** “Rear displays or rear audio?” → `select(flow_id=[rear_displays, rear_audio])`
- **Possible U:** “Rear audio.” → `inform(flow_id=rear_audio)`
- **Alternative U:** “Play rear audio.” → `request(task=play, flow_id=rear_audio)`

Text-only NLU need not relabel the second form from hidden context. Play rules reconcile it with the singleton; neither form should cause duplicate Tasks/starts.

“Play headlights, then rear audio” needs explicit scheduling semantics. A list of requests is not automatically an execution schedule, and two play Tasks remain prohibited. General sequencing, saved-task answer priority, and partial-turn handling are next rule-design decisions.
