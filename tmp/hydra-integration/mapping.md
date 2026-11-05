# Hydra NLU → ndialog act mapping — the contract

Status: agreed. Source of truth for the HydraNlu adapter and its tests.
NLU vocabulary: `D:\yleng\proj\hydra\notebooks\m6_showroom_data\gen.md` + `gen-zh.md`.
Engine semantics: ndialog core.

## Server contract (dumb pipe)

`serve.py` wraps `predict()` and returns the prediction verbatim — the same
fields `predict.py` prints, no more:

```json
{"intent": "request_flow", "confidence": 0.998, "slots": [{"type": "flow_ref", "text": "cabin cinema", "start": 12, "end": 24}]}
```

- `POST /parse` with `{"text": ...}` → 200 with the prediction above
- `GET /health` → `{"ok": true}`
- `ValueError` from the model (empty text, over-long input) → 400
- No act knowledge, no slot normalization, no intent renaming on the server
  side. The prediction → Act translation lives engine-side, in the ndialog
  adapter.
- `--checkpoint` accepted exactly as `predict.py` accepts it; default is the
  latest completed run.

## Adapter shape

- `HydraNlu implements Nlu` — HTTP transport plus the prediction → `List<Act>`
  translation. The mapping lives in package-private pure statics, testable
  without sockets.
- `HydraValues` — normalization, contained scope: flow alias resolution against
  the catalog, part/theme canonical tables, choice ordinal parser. Pure
  functions, no IO.
- Lifecycle: `serve.py` is started manually; `HydraNlu` probes `/health` at
  construction and reports the start command on failure.

## Confidence

Stored on the Act, not used:

- `Act` gains a nullable `Double confidence` and a `withConfidence` copy
- `HydraNlu` stamps the prediction's confidence on every act it emits — both
  acts of the greet+name split carry it; `OpenCodeNlu` emits null
- `JsonCodec` / `/inject` stays strict: confidence is set by the NLU adapter,
  not by typed input

## Normalization rule (unified)

Canonicalize recognized aliases; otherwise pass the extracted slot text
through verbatim. The engine's existing validation produces the user feedback —
the adapter never silently drops what the user said.

## Intent mapping (9, total)

| hydra intent | slots (per gen.md) | → ndialog acts |
|---|---|---|
| `greet` | ∅ or `name` | `[GREET]` + `INFORM(name=text)` when the slot is present (gen.md:107 designs this split) |
| `affirm` | ∅ | `[AFFIRM]` |
| `deny` | ∅ | `[DENY]` |
| `inform` | exactly one of `name/flow_ref/part/theme/choice` | `[INFORM(slot=value)]` |
| `request_flow` | ∅ or ≤1 each of `flow_ref/part/theme` | `[REQUEST{task=flow, flow_id?, part?, theme?}]` |
| `request_stop` | ∅ | `[REQUEST{task=stop}]` |
| `request_resume` | ∅ | `[REQUEST{task=resume}]` |
| `request_quit` | ∅ | `[REQUEST{task=quit}]` |
| `UNK` | ∅ | `[INFORM(text=raw utterance)]` — binds nowhere; the engine answers "No agent wants that. Greet or request a flow first." |

## Slot normalization (open to changes)

| hydra slot | → ndialog slot | canonicalization | unrecognized value → what the user sees |
|---|---|---|---|
| `name` | `name` | verbatim (Act trims) | — |
| `flow_ref` | `flow_id` | case-insensitive match on catalog id / name / aliases (en+zh) | engine: `Unknown flow_id: <raw>` |
| `part` | `part` | `interior/cabin` → `inside`; `exterior` → `outside`; `车内/内饰/座舱里` → `inside`; `车外/外饰` → `outside` | engine: `No flows match. Try another part or theme.` |
| `theme` | `theme` | `family-friendly/families` → `family`; `家庭/全家/家用` → `family`; `娱乐` → `entertainment`; `隐私/私密` → `privacy` | engine: `no_matches` |
| `choice` | `choice` | ordinal parser → `"N"` | engine: parseInt fails → question stays open, unresolved fallback |

Canonical values are lower-cased; `name` keeps the spoken case.

## Choice ordinal parser

Precedence, first match wins:

1. `\d+` inside the extracted text
2. ordinal words: `first..tenth`
3. word numbers as whole words: `two..ten`
4. `one` only when the extracted text is exactly `one`

`the second one` → 2 (ordinal outranks the trailing "one"); `option two` → 2;
`number 3` → 3; `2nd option` → 2 (digit rule). Unparseable → raw passthrough.

## Edge cases

| Case | Behavior |
|---|---|
| `inform` with multiple slots (out-of-distribution; gen.md trains one) | one `INFORM` per slot, utterance order. The grammar keeps one value per inform because binders are shape-matching predicates: a multi-slot act binds to different agents depending on stack layout (`INFORM{name, part}` binds FlowAgent's part and silently drops the name correction). |
| duplicate slot type in one prediction (out-of-distribution) | first extracted slot in utterance order wins |
| `UNK` or unknown intent string | `INFORM(text=raw utterance)` → engine fallback |
| `request_stop/resume/quit` | engine: "control not implemented in this slice" — expected; the NLU side is correct |
| HTTP / JSON failure | `IOException` → CLI "Input error: ..." |

## Work packages (self-contained, in landing order)

| # | Package | Scope | LOC |
|---|---|---|---|
| 1 | FakeFlow → OpenCodeNlu severing | `JsonCodec.catalog(Reader)` overload; FakeFlow reads via `InputStreamReader`; `OpenCodeNlu.read` goes private | ~15 |
| 2 | Catalog → hydra vocabulary | `catalog.json` (4 flows, en+zh aliases, descriptions); CliTest script values | ~40 data + ~15 test |
| 3 | Dumb server | hydra `m3_training/serve.py` (new file only) | ~90 |
| 4 | Normalization + translation layer | `HydraNlu`, `HydraValues`, `Act.confidence`, `Main` registration, tests, README | ~240 + ~200 test |

## Verification probes (end-to-end, two terminals)

1. greet → name → affirm → select → "the second one" — full handoff
2. near-miss: "show me the massage chairs please" → `Unknown flow_id: massage chairs`
3. out-of-domain: "what is the weather in paris" → UNK → engine fallback
4. `NLU_BACKEND=opencode` still functional — A/B baseline
