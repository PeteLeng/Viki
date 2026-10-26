# ndialog — agent-stack dialogue engine

A minimal dialogue core where conversational goals are agents on a stack. See
[DESIGN.md](DESIGN.md) for the pipeline and invariants. This slice implements
Welcome/Flow only: greeting, name, goal offer, flow selection/confirmation,
execution handoff, and completion.

## Build and run

JDK 21 and the included Gradle wrapper; `core` is dependency-free Java 8,
`cli` adds Gson and a synchronous fake application port.

```sh
bash ./gradlew test
bash ./gradlew :cli:run --console=plain -q
```

On Windows outside Git Bash, use `gradlew.bat`.

## Try it

```text
/inject {"acts":[{"type":"greet"}]}
/inject {"acts":[{"type":"inform","slots":{"name":"Sam"}}]}
/inject {"acts":[{"type":"affirm"}]}
/inject {"acts":[{"type":"inform","slots":{"theme":"family"}}]}
/inject {"acts":[{"type":"inform","slots":{"part":"back-seat"}}]}
/inject {"acts":[{"type":"inform","slots":{"choice":"2"}}]}
/state
```

Or start a known flow directly:

```text
/inject {"acts":[{"type":"request","slots":{"task":"flow","flow_id":"headlights"}}]}
```

## Vocabulary

One representation, `Act`, carries language meaning in both directions. Slot
values are lists: a singleton is a value, an empty list is unset, and multiple
values are offered choices. Input carries singletons only; lists are the
system's question vocabulary.

| Input act | Use |
| --- | --- |
| `greet` | Greeting / session start |
| `request` (`task`=`flow`, optional `flow_id`, `part`, `theme`) | Request the flow goal with arguments |
| `inform` (one of `name`, `flow_id`, `part`, `theme`, `choice`) | Supply or correct one value; `choice="2"` is a one-based ordinal |
| `affirm` / `deny` | Answer the open confirmation; a deny clears the proposed value |
| `request` with other `task` (`stop`, `resume`, `quit`, `help`, `list`) | Global controls — reported unsupported in this slice |

System output uses the same acts: `request(flow_id=[])` asks, 
`select(flow_id=[a,b,c])` offers, `confirm(flow_id=[x])` or `confirm(task=[flow])`
proposes, and `inform` states. Application work is requested through
`state.starts()`, which the host dispatches after accepting the returned state.

## CLI

| Command | Purpose |
| --- | --- |
| Normal text | Send to the NLU |
| `/inject JSON` | Bypass the NLU with acts |
| `/state` | Print the agent stack, open question, session name |
| `/quit` | Exit |

## Optional opencode interpreter

```sh
export NLU_BACKEND=opencode
bash ./gradlew :cli:run --console=plain -q
```

| Variable | Use |
| --- | --- |
| `NLU_OPENCODE_DIR` | Working directory; omit to use the process working directory |
| `NLU_OPENCODE_CMD` | Full command override |
| `OPENCODE_PERMISSION` | Passed through when unset; otherwise `{"external_directory":"allow","edit":"deny"}` |

Each utterance spawns one stateless `opencode run` subprocess with the static
prompt plus the current text — no conversation memory. Non-zero exit, timeout
(120 s), or JSON-less reply is an input error that does not end the chat.
