# Showroom dialogue MVP — Java

A small, observable dialogue core and CLI. The first rewrite slice implements:

```text
greeting → ask name → offer Play → yes → choose a flow → start
direct Play request → choose a flow (or start a named flow)
theme/part supplied to Play → narrow its choices
```

Start with [RULES_REWRITE.md](RULES_REWRITE.md) for the implemented design and code
reading order. [MVP_DESIGN.md](MVP_DESIGN.md) describes the wider target contract;
[DIALOGUE_EXAMPLES.md](DIALOGUE_EXAMPLES.md) contains both slice walkthroughs and
the broader reference conversations.

## Build and run

Use JDK 21 and the included Gradle wrapper. The dependency-free `core` library
targets Java 8 APIs/bytecode; `cli` uses Gson and a small fake Flow adapter.

```sh
bash ./gradlew test
bash ./gradlew :cli:run --console=plain -q
```

On Windows outside Git Bash, use `gradlew.bat`.

## Try the greeting handoff

Paste these lines into the CLI; no LLM is required:

```text
/inject {"acts":[{"type":"greet"}]}
/inject {"acts":[{"type":"inform","slots":{"name":"Sam"}}]}
/inject {"acts":[{"type":"affirm"}]}
/inject {"acts":[{"type":"inform","slots":{"theme":"family"}}]}
/inject {"acts":[{"type":"inform","slots":{"part":"back-seat"}}]}
/inject {"acts":[{"type":"inform","slots":{"choice":"2"}}]}
/state
```

The first two questions are sequential: name, then the Play offer. Accepting the
offer completes Welcome and admits Play. The final answer selects rear audio
from the actual offered order. The fake adapter synchronously emits narration,
progress, and completion.

The same script is in `examples/demo.txt`:

```sh
bash ./gradlew :cli:run --console=plain -q < examples/demo.txt
```

## Direct Play and value confirmation

Start a known flow directly:

```text
/inject {"acts":[{"type":"request","slots":{"task":"play","flow_id":"headlights"}}]}
```

Or, in a fresh session, request discovery:

```text
/inject {"acts":[{"type":"request","slots":{"task":"play"}}]}
/inject {"acts":[{"type":"inform","slots":{"theme":"visibility"}}]}
/inject {"acts":[{"type":"affirm"}]}
```

Only headlights matches visibility, so Play proposes it for confirmation. Its
`flow_id` stays unset until acceptance. A bare yes after a multi-flow selection
does not select anything; the same choices are repeated.

## Input and output contract

The local injection/NLU envelope is an `acts` array with `type` and string-valued
`slots`. The core currently accepts **one act per turn**:

| Input | Use |
| --- | --- |
| `greet` with no slots | Start Welcome, or acknowledge a greeting during Play |
| `request`, `task=play` | Admit/reuse singleton Play; optional `flow_id`, `part`, `theme`, or `choice` |
| `inform`, `name=Sam` | Supply/correct the session name during Welcome |
| `inform`, `theme=family` | Supply one Play value; `part` and `flow_id` work similarly |
| `inform`, `choice=2` | One-based ordinal reference to the pending selection |
| `affirm` / `deny` with no slots | Accept/reject an applicable goal/value proposal |

A request can contain several named arguments. An `inform` carries one value in
this slice. `choice` is a temporary reference, never a stored task parameter.
`part=any` and `theme=any` mean explicit no preference.

System output uses the same `DialogueAct` type. A selection has
`slots={slot:flow_id}` and an ordered `choices` list. A value confirmation has
`slots={flow_id:headlights}`; a goal offer has `slots={task:play}`. Commands to the
application are separate `FlowCommand` objects.

Old `operation=demonstrate`, `feature_id`, user `select`, request relations, and
retained-choice behavior are superseded. The initial scope is Welcome/Play;
List/Help, controls, interruptions, live switching, and composite input remain
target behaviors rather than implementations.

## State and processing

```text
handleUserTurn(state, acts):
    copy state once
    handleInput       # directly update values, resolve questions, admit tasks
    advance focus     # once, without replaying the input
    return state, dialogue acts, flow commands, trace
```

`DialogueState` holds the task stack, session `userName`, and one
`pendingInteraction`. Tasks hold accepted parameters, progress, and command
ownership. The question owns its proposed value/goal or choices. Clearing it
removes its ordinal context, even after execution completes.

Both direct requests and accepted offers call `TurnContext.activateTask`.
Omitted Play parameters are retained on repeated requests. Equivalent requests
during execution do not start again; changing live work is reported as unsupported.
After completion/failure, an explicit Play request can repeat/retry using the same
singleton task. Ordinary information and callbacks never restart it.

The host accepts returned state before rendering or dispatch. `START_REQUESTED`
means a command was issued, not that execution succeeded. Notifications use the
captured command association and update application-reported progress without
running the user-input policy again.

## CLI and trace

| Command | Purpose |
| --- | --- |
| Normal text | Send only that text to NLU |
| `/inject JSON` | Bypass NLU |
| `/state` | Inspect tasks, session name, pending question, execution progress |
| `/trace` | Inspect the latest turn/notification trace |
| `/quit` | Exit |

The console prints labeled TRACE and OUTPUT sections, with each trace before its
output, including synchronous Flow callbacks. Trace includes input, before/after
state, named handler steps, selected acts/commands, and elapsed time.

## Optional text-only LLM NLU

Configure an OpenAI-compatible chat-completions endpoint:

```sh
export NLU_URL="https://your-provider.example/v1/chat/completions"
export NLU_MODEL="your-model"
export NLU_API_KEY="your-key"
bash ./gradlew :cli:run --console=plain -q
```

`NLU_API_KEY` may be omitted for a local endpoint. Each request contains only a
static schema/catalog prompt and the current text. NLU does not receive task
state, pending questions, offered choices, or history. It preserves unsupported
or composite meanings for the core to report, rather than silently dropping acts.

Production uncertainty/confidence transport and Android/Flow integration remain
separate work. Core tests use handwritten acts; the HTTP adapter tests use a local
stub. Neither substitutes for evaluating a production NLU on held-out text.
