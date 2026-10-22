# Design — agent stack

Distilled from the original `NDialogCore` sketch. The engine is one loop over
one procedure; everything else is polymorphism on agents and on the open
question.

## Pipeline

```text
handleUserTurn(state, segments):
    working = copy(state)              # the copy starts with fresh output lists
    for each segment:                  # the interpreter pre-segments the utterance
        if global keyword: report unsupported, continue
        absorb(segment)                # the open question translates answers
        else route(segment)            # stack walk (nearest first), else spawn
        refocus()                      # the question must belong to the focus
        executeFocus()                 # the focus performs application side effects
        cleanup()                      # remove finished agents (idle -> running -> done/failed)
        onFocus()                      # the focus takes initiative when nothing is open
    return working                     # state carries this turn's acts and starts
```

## The pieces

- **`Segment`** — user input unit: a `key` plus string slots. The interpreter
  (opencode NLU today) is stateless and produces segments in order.
- **`DialogState`** — the single shared object: agent stack (top is focus), the
  one `openQuestion`, session `name`, and the current turn's `acts` and `start`
  requests. `ask()` stores and speaks a question in one step; `copy()` snapshots
  without the previous turn's output.
- **`Agent`** — a conversational goal. Subclasses own their fields and override
  `bind` / `execute` / `onFocus` / `rejected`. `bind` returns `NOT_MINE`
  (walk continues), `BOUND` (values applied), or `REJECTED` (error act emitted,
  walk stops, nothing applied).
- **`OpenQuestion`** — the adapter: `confirm(id=X)` + `yes` becomes
  `value{id=X}`; `select(id=[A,B])` + `choice{n=2}` becomes `value{id=B}`;
  a goal offer + `yes` becomes a bare `flow` segment that spawns. Outcomes:
  translated / rejected / repeated / error / miss. It always belongs to the
  focus; refocus clears it when focus moves.

## Invariants and disciplines

- Binding precedence: global keywords, then the open question, then the stack
  walk (nearest first), then spawning. Ambiguity is not guessed.
- Spawned agents enter the stack immediately, before execute, so a direct
  `flow{id=...}` starts in the same turn it was requested.
- Validate a complete argument set before assigning any of it; invalid
  arguments leave the state untouched.
- The question is cleared when answered, rejected, or invalidated by changed
  filters; an unrelated value (a name correction) does not clear it.
- Only an outstanding selection resolves ordinals; a bare `yes` under a
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
  name corrections walk back to it. It completes only when its goal offer is
  denied.
- Finished agents are removed, so a retry after completion spawns a fresh
  agent; parameters from the previous run are not retained.
- The interpreter receives no dialogue context in this slice (`read(raw,
  openQuestion)` is future work); the question adapter resolves form on the
  engine side instead.

## Not in this slice

Composite multi-segment handling beyond sequential processing, List/Help
(merged per plan), controls and live switching, suspended questions across
focus shifts, and any uncertainty/confidence transport.
