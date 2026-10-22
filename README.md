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
/inject {"segments":[{"key":"wakeup"}]}
/inject {"segments":[{"key":"value","slots":{"name":"Sam"}}]}
/inject {"segments":[{"key":"yes"}]}
/inject {"segments":[{"key":"value","slots":{"theme":"family"}}]}
/inject {"segments":[{"key":"value","slots":{"part":"back-seat"}}]}
/inject {"segments":[{"key":"choice","slots":{"n":"2"}}]}
/state
```

Or start a known flow directly:

```text
/inject {"segments":[{"key":"flow","slots":{"id":"headlights"}}]}
```

## Input vocabulary

User input arrives as **segments** — semantically coherent units produced by the
interpreter. One value per segment in this slice.

| Segment | Use |
| --- | --- |
| `wakeup` (optional `name`) | Greeting / session start; binds a name if supplied |
| `flow` (`id`, `part`, `theme`) | Request the flow goal with arguments |
| `value` (one of `name`, `id`, `part`, `theme`) | Supply or correct one value |
| `choice` (`n`) | One-based ordinal into the offered selection |
| `yes` / `no` | Answer the open confirmation |
| `stop`, `resume`, `quit`, `help`, `list` | Global controls — reported unsupported in this slice |

System output uses `DialogueAct` (greet/request/select/confirm/inform);
application work is requested through `Result.starts`, which the host dispatches
after accepting the returned state.

## CLI

| Command | Purpose |
| --- | --- |
| Normal text | Send to the NLU |
| `/inject JSON` | Bypass the NLU with segments |
| `/state` | Print the agent stack, open question, session name |
| `/quit` | Exit |

## Optional opencode interpreter

```sh
export NLU_BACKEND=opencode
bash ./gradlew :cli:run --console=plain -q
```

| Variable | Use |
| --- | --- |
| `NLU_OPENCODE_DIR` | Working directory; defaults to `D:/yleng/scratch/dm-master` |
| `NLU_OPENCODE_CMD` | Full command override |
| `OPENCODE_PERMISSION` | Passed through when unset; otherwise `{"external_directory":"allow","edit":"deny"}` |

Each utterance spawns one stateless `opencode run` subprocess with the static
prompt plus the current text — no conversation memory. Non-zero exit, timeout
(120 s), or JSON-less reply is an input error that does not end the chat.
