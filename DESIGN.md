# Design — agent stack

Distilled from the original `NDialogCore` sketch. The engine is one loop over
one procedure; everything else is polymorphism on agents and on the open
question.

## Pipeline

```text
handleUserTurn(state, acts):
    working = copy(state)              # the copy starts with fresh output lists
    for each act:                      # the interpreter pre-segments the utterance
        if global task: report unsupported, continue
        translate(act)                 # the open question turns answers into bindable acts
        bind(act)                      # stack walk (nearest first), else spawn
        refocus()                      # the question must belong to the focus
        executeFocus()                 # the focus performs application side effects
        onFocus()                      # the focus takes initiative when nothing is open
        cleanup()                      # remove finished agents (idle -> running -> done/failed)
    return working                     # state carries this turn's acts and starts
```

## The pieces

- **`Act`** — the one representation of language meaning, both directions.
  Slot values are lists: a singleton is a value, an empty list is unset, and
  multiple values are offered choices. Input carries singletons only; the
  question family uses cardinality as meaning — `request(flow_id=[])` asks,
  `confirm(flow_id=[X])` proposes, `select(flow_id=[A,B,C])` offers.
- **`DialogState`** — the single shared object: agent stack (top is focus), the
  one `openQuestion`, session `name`, and the current turn's `acts` and `start`
  requests. `ask()` stores and speaks a question in one step; `copy()` snapshots
  without the previous turn's output.
- **`Agent`** — a conversational goal. Subclasses own their fields and override
  `bind` / `execute` / `onFocus`. `bind` returns `NOT_MINE` (walk continues),
  `BOUND` (values applied — a null value clears), or `REJECTED` (error act
  emitted, walk stops, nothing applied). What a cleared or updated value means
  is decided by the agent's own execute/onFocus.
- **`OpenQuestion`** — pure bookkeeping: the owner plus the expected act. It
  translates answers into directly bindable acts — `confirm(flow_id=[X])` +
  `affirm` becomes `inform(flow_id=X)`; `select(flow_id=[A,B])` +
  `inform(choice=2)` becomes `inform(flow_id=B)`; a goal offer + `affirm`
  becomes `request(task=flow)` which spawns; and a `deny` becomes
  `inform(flow_id=[])` — the value goes back to unset, and the agent decides
  what follows. It always belongs to the focus; refocus clears it when focus
  moves.

## Invariants and disciplines

- Binding precedence: global tasks, then the open question, then the stack
  walk (nearest first), then spawning. Ambiguity is not guessed.
- Spawned agents enter the stack immediately, before execute, so a direct
  `request(task=flow, flow_id=...)` starts in the same turn it was requested.
- Validate a complete argument set before assigning any of it; invalid
  arguments leave the state untouched.
- The question is cleared when answered or invalidated by changed filters; an
  unrelated value (a name correction) does not clear it.
- `affirm`, `deny`, and `choice` only mean something against the open
  question; otherwise they fail to bind and are unresolved.
- Only an outstanding selection resolves ordinals; a bare `affirm` under a
  selection repeats the offer.
- Flow notifications (`onFlowEvent`) bypass the input pipeline: they update a
  running agent's progress, emit an act, and clean up — nothing else.
- Unresolved/unsupported input is an ordinary outcome (an `inform(error=...)`
  act), never an exception.

## Deviations from the sketch, deliberately

- `execute()` requests a start through the state instead of calling the app
  directly: the host dispatches starts after accepting the returned state, so
  a synchronous application cannot mutate mid-turn state.
- Welcome does not complete when Flow spawns; it stays below on the stack so
  name corrections walk back to it. It completes when its goal offer is
  declined — modeled as binding `task` back to unset, not a rejection hook.
- Finished agents are removed, so a retry after completion spawns a fresh
  agent; parameters from the previous run are not retained.
- The interpreter receives no dialogue context in this slice (`read(raw,
  openQuestion)` is future work); the question bookkeeping resolves form on
  the engine side instead.

## Not in this slice

Composite multi-act handling beyond sequential processing, List/Help (merged
per plan), controls and live switching, suspended questions across focus
shifts, and any uncertainty/confidence transport.
