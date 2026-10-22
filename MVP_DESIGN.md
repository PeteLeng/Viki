# Showroom dialogue MVP: v0 contract

Build a small CLI-first dialogue manager with handwritten rules and text-only NLU. This is the **target contract**; the Java prototype still uses the earlier design. See [README.md](README.md) for its runnable commands and [DIALOGUE_EXAMPLES.md](DIALOGUE_EXAMPLES.md) for the target conversations.

## 1. Dialogue-act contract

| Concept | Meaning |
| --- | --- |
| `DialogueAct` | What a user or system communicates, with semantic content |
| `Task` | A goal instance with remembered parameters and progress |
| `FlowCommand` | An operation selected for dispatch to the application |

**Focused** describes conversational position. **Running/paused** describe execution. **Singleton** describes the number of Task instances.

The shared act representation is inspired by [Jurafsky and Martin, Appendix K, §K.1](https://web.stanford.edu/~jurafsky/slp3/K.pdf#page=4). The following domain-specific subset is authoritative for v0.

### Act forms

| Form | Meaning | Supported v0 use |
| --- | --- | --- |
| `request(task=T, ...)` | Request a goal or control operation | User → core |
| `request(slot=S)` | Request a parameter value | System → user |
| `inform(...)` | Supply values, facts, results, or a contextual answer | Both |
| `confirm(...)` | Ask whether a proposition is correct or acceptable | Both |
| `affirm(...)` | Accept the relevant proposition, optionally supplying more information | Both |
| `deny(...)` | Reject the relevant proposition or named values | Both |
| `select(S=[V1, V2, ...])` | Ask for one value from ordered alternatives | System only |

Reflection queries about remembered Task state are outside v0. Sharing the act representation does not require supporting every form in both directions.

| Utterance | Act |
| --- | --- |
| User: “What themes do you have?” | `request(task=list, entity=theme)` |
| User: “What does theme mean?” | `request(task=help, topic=theme)` |
| System: “Which theme interests you?” | `request(slot=theme)` |
| System: “Rear displays or rear audio?” | `select(flow_id=[rear_displays, rear_audio])` |
| User: “The second one.” | `inform(choice=ordinal(2))` |

### Grammar conventions

The notation describes semantic data, not Java calls or finalized JSON.

- A turn is `[Act, ...]`. Keep values attached to their acts and consider the complete turn before selecting output.
- A `request` uses either `task` or `slot`. The bare shorthand `request(theme)` is not canonical.
- A new request's arguments belong to that request, not another Task mentioned in the turn.
- `inform` supplies at least one item; `confirm` supplies a proposition. Asking a confirmation does not itself accept its proposed values.
- `affirm()` and `deny()` can have contextual referents. `affirm(theme=family)` can accept a proposal and add information.
- `deny(part=back-seat)` rejects back-seat. `[deny(), inform(part=back-seat)]` rejects a contextual proposal and supplies back-seat as its replacement. Neither automatically cancels the goal.
- `select` names one slot and a nonempty, ordered alternative list. It describes the system's question, not the user's selection.
- Several acts mean several communicated acts, not competing NLU interpretations.

### Values and references

| Form | Meaning |
| --- | --- |
| Absent/unset parameter | No remembered value established |
| `inform(theme=family)` | Named information |
| `inform(theme=any)` | Explicitly no theme preference |
| `inform(value=any)` | Unqualified “no preference”; destination requires context |
| `inform(choice=ordinal(2))` | Unresolved reference to an outstanding selection |
| `request(task=play, choice=ordinal(2))` | A goal argument referring to an outstanding selection |

`any` is distinct from unset and from a catalog ID. `value` and `choice` are act-level forms, not permanent Task parameters. Ordinals are one-based integers; their slot and value come from the applicable question. Arrays of offered/result values do not silently define AND/OR filter semantics.

### NLU boundary

`interpret(current_text)` receives only the current text. Static schema/catalog vocabulary is allowed; runtime Tasks, questions, choices, execution state, and conversation history are not.

NLU must not invent runtime IDs, resolve ordinals from hidden context, or emit `target=current_task`, `target=current_execution`, `task_ref(...)`, `aside`, or `replace` instructions. Known names may normalize to validated `flow_id` values. Unknown references and genuinely ambiguous wording must not become fabricated certainty.

The exact typed wire format and uncertainty/failure envelope remain integration decisions. Evaluate production NLU on held-out text separately from core tests using handwritten acts.

## 2. Requested operations and parameters

`task` names requested behavior; it does not require a new Task object for every request.

| Name | Inputs / defaults | Meaning |
| --- | --- | --- |
| `list` | `entity=flow` by default; `entity` is `flow`, `theme`, or `part`; optional `part`, `theme` | Enumerate catalog information |
| `play` | `flow_id`, or information/reference resolving it; optional `part`, `theme` | Resolve and execute a flow |
| `help` | `topic=overview` by default; a flow explanation needs a flow identifier/reference | Explain a concept, capability, procedure, or flow |
| `pause` | Global control; no NLU runtime selector | Ask to pause execution |
| `resume` | Global control; no NLU runtime selector | Ask to resume paused execution |
| `stop` | Global control; no NLU runtime selector | Ask to stop execution |
| `cancel` | Focus-local control | Ask to abandon the focused goal |

`part` and `theme` are optional discovery parameters. When both simple values are supplied, both constrain matches. Their absence does not make `list` incomplete. A missing `flow_id` is not a malformed play request, but execution needs a concrete resolved flow.

Defaults describe fresh requests; inheritance/replacement of values in an existing Task belongs to its rules. Rejected facts must retain their negative meaning; their persistence and general multi-value query operators remain undecided.

`help` covers definitions and guidance, while `list` enumerates available values. Help topics include `theme`, `part`, `flow`, and operation names such as `play` or `pause`. Its reusable mechanism can use domain-authored content and catalog descriptions.

Result fields include ordered `flows`/`themes`/`parts`, `description`, and application-supported `execution_status`. Policy may emit an `error` diagnosis. Complete integration enums and diagnostic codes are not fixed by the examples.

Canonical goal names are `list`, `play`, and `help`; ordinary utterances may still say “demo,” “start,” or “feature.” Use `flow_id` for the executable catalog ID. Any distinct production feature-to-flow mapping needs explicit agreement.

### Singleton Play and controls

**At most one play Task exists anywhere on the stack**, whether awaiting information, running, paused, finished-but-still-stored, or outside focus:

```text
Valid:   [Play(awaiting flow)]
Valid:   [Play(running), Help(focused)]
Invalid: [Play(running), Play(awaiting flow), Help]
```

Play-specific rules handle another play request against existing play work; they must not blindly push a second Play. This is not a generic “same kind means merge” rule.

For pause/resume/stop, Play policy inspects the singleton Task and execution facts and chooses a command, information, or no output. An awaiting play Task is not evidence of running execution. Controls do not create persistent control Tasks, answer an unrelated question, or mandate reminders.

`resume` concerns flow execution. There is **no `continue` task**: returning to saved conversational work is lifecycle/focus behavior. Basic cancellation is focus-local; canceling Help does not itself stop Play. Canceling live play work and explicitly qualified controls still need detailed rules.

## 3. State and expectation lifetime

```text
DialogueState:
  stack: [Task, ...]       # top has conversational focus

Task:
  id                      # assigned by the core
  kind                    # list | play | help for goal records
  parameters
  progress
  expectation?            # outstanding system question
```

An expectation can reuse the issued `request(slot=...)`, `select(...)`, or `confirm(...)` data. Its owning Task is core metadata, never an NLU-generated identity. Ordinary clarification does not create another Task.

**Clear an expectation when answered, rejected, invalidated, or superseded—not merely because input updated its Task.** A system turn may combine information with a question; each owner's current question must be unambiguous.

Expectations guide interpretation, not permitted answer syntax. Extra information, corrections, new goals, and controls remain possible. For example, `inform(part=back-seat, theme=family)` can help resolve a play goal even while its question asks for `flow_id`.

### Choice-memory scope

The choice list lives **only inside an outstanding `select` expectation**:

```text
expectation = select(flow_id=[rear_displays, rear_audio])
User: inform(choice=ordinal(2))
Update: flow_id = rear_audio; expectation = null
```

- After clearing, that list cannot resolve a later ordinal.
- `inform(flows=[...])` creates no expectation or ordinal-reference context.
- Resolve against the actual offered order, never a fresh catalog query or another list that happens to contain enough items.
- An unanswered question may remain on a suspended Task and be restored/reissued on return.
- Trace/history must not become a hidden fallback for cleared lists.

There is no `retained_choices` field. This intentionally withdraws the earlier post-answer retention requirement; [C27](DIALOGUE_EXAMPLES.md#c27) and [C28](DIALOGUE_EXAMPLES.md#c28) specify the resulting limits.

Task completion is a domain decision, not a consequence of every `inform`. Help can finish and restore saved work. Retiring play work must preserve required application-result association.

## 4. Processing shape

The first [task-rules rewrite proposal](RULES_REWRITE.md) folds contextual interpretation into input rules that update a private working state. A separate whole-turn `bind` phase is optional; individual lookups and matching checks stay read-only.

The core applies the complete input, asks task policy for a `Decision`, records issued outputs and selected lifecycle changes, then returns state, outputs, and trace. Adapters execute after the host accepts that state. The proposal contains the step sketch and Java rule-table shape; detailed interfaces and runner semantics remain design work.

Required behavior:

- Interpret existing references using pre-turn question/focus context; process the complete turn before choosing output.
- Preserve act-local ownership, including an existing answer plus a new request, or cancellation plus a new goal.
- Record issued questions and command ownership before dispatch. The host accepts returned state before invoking adapters.
- Policy uses parameters **and progress/input**; a remembered `flow_id` must not trigger repeated starts.
- Immediate completion/refocus may require a follow-up decision, without reapplying the original turn or creating a return Task.
- Unresolved conversational meaning is an ordinary outcome for policy, not necessarily an exception or an error utterance. Do not commit guessed values or half of a coupled correction.

A selection reference still needs a valid outstanding selection; an unqualified `any` needs a destination; accepting an affirmation needs an identifiable proposition. These prerequisites do not impose a rigid question-answer script.

### The central example

Given an existing play Task awaiting a flow:

| Turn | Act / consequence |
| --- | --- |
| User: “Something for the family, in the back seats.” | `inform(theme=family, part=back-seat)` updates useful parameters |
| System: “Rear displays or rear audio?” | `select(flow_id=[rear_displays, rear_audio])` becomes its expectation |
| User: “Yes.” | `affirm()` supplies no particular flow; the question is not answered |
| System repeats the choices, if policy chooses | Same outstanding `select`; no guessed value or new Task |

If the user instead says “the second one,” its reference resolves through that expectation, sets `flow_id=rear_audio`, and clears the question. Policy then decides whether execution may proceed. Detailed phase changes are in [C10](DIALOGUE_EXAMPLES.md#c10) and [C11](DIALOGUE_EXAMPLES.md#c11).

## 5. Integration and observability

Use `flow.list()` as catalog data, supplemented where aliases/descriptions/categories are missing. Matching `part` and `theme` means comparing Task values with that metadata; it needs no separate dialogue abstraction. Retrieving data, presenting `inform(flows=...)`, and asking `select(flow_id=...)` are different operations.

| Selected command | Application boundary |
| --- | --- |
| `FlowCommand.start(flow_id)` | `flow.start(id, listener)` |
| `FlowCommand.pause()` | Planned pause capability |
| `FlowCommand.resume()` | Planned resume capability |
| `FlowCommand.stop()` | `flow.stop()` under its actual contract |

The current prototype implements list/start/stop at the Flow boundary. Pause/resume signatures, acknowledgment guarantees, and event mappings require integration work.

The application owns execution truth. Command issuance records a request, not successful running/paused/stopped state. Notifications reach their owning play work even when another Task has focus; captured listener context may suffice. They bypass NLU and must not automatically repeat questions, restart flows, or create Tasks.

The CLI needs text input, direct act injection, state inspection, and trace inspection. Trace text/acts, resolution outcomes, parameter/expectation/progress/focus changes, rule IDs, selected outputs, and application results with enough association/timing to explain them. Malformed input, unresolved meaning, application failure, and broken internal invariants are different conditions.

The [36 reference cases](DIALOGUE_EXAMPLES.md) are the behavior-check basis. Verify state, acts, commands, and offered order rather than exact phrasing. Handwritten acts test the core; held-out text/ASR paraphrases test NLU. Neither documentation nor prototype tests establish conformance to this new contract.

## 6. Decisions for the next design discussion

1. **Rule interfaces:** refine [proposal v1](RULES_REWRITE.md): schemas, input-report/decision records, matching/consumption, mutation ownership, failure handling, and completion/refocus scheduling.
2. **Answer context:** eligibility/priority of suspended expectations. Focus-local answers were proposed, not agreed; do not guess or broadcast by slot name alone.
3. **Singleton admission:** parameter inheritance and new requests against pending/running/paused Play; switching/confirmation/stop/restart rules.
4. **Lifecycle:** retirement of list/help/root Tasks, cancellation of live play work, and callback ownership. Returning is internal behavior, not a new task kind.
5. **Value/turn semantics:** persistence of rejected facts, multi-value AND/OR/add/remove operators, conflicting input, and ordering of several new goals.
6. **Integrations:** typed NLU payload/unknown mentions/uncertainty; Flow concurrency and acknowledgments; feature-to-flow ID relationships; concrete CLI/log formats. Singleton Task count alone does not define SDK concurrency.

Next: review the standalone rewrite proposal against these examples, then migrate the core, adapters, CLI injection syntax, and tests together. The old user `select(ordinal=...)`, request relations, and post-answer choice behavior are legacy contracts, not extra supported aliases.
