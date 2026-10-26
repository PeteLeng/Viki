# voice agent talk

## What problems we're solving & what the product looks like

Examples of a conversation:

```
  User: hello.
  Sys: Hi, What's ur name?
  User: I'm Peter Parker.
  Sys: Hi Peter, how can I help you?
```

Variants1:\
multiple-questions

```
  User: hello.
  Sys: Hi, what's your name, How many I help you?
  User: I'm John, book me a flight
  Sys: Sure John, where're u going?
```

Variants2:\
partial answering/context switch

```
  User: hello.
  Sys: Hi, what's your name, How many I help you?
  User: book me a flight.
  Sys: Sure, where're u going?
```

Variants3:\
no fixed order

```
  User: book me a flight.
  Sys: Sure, where're u going?
```

## terms

- DiagMgr (DM)
- DiagAction (act)

## Typical Architecture

- [Survey study](2021_dialogue_management_review_tcds.pdf)
- [RavenClaw (classic)](2009_bohus_rudnicky_ravenclaw_csl_journal.pdf)
- [Dialogue Action (Young et al)](young-csl09.pdf)

## Core Concepts

### Task Tree

### DiagState {The Agent/Task Stack}

[WelcomeAgent, FlowAgent]

### Expecation Agenda

What the System expects to hear from the User at any given time.

### The Main Event Loop

- Binding
- Execute
- Focus

## File Structure

## Open Questions

### Input Handling

- narration filter 识别TTS
- barge-ins

### Err Handling (robustness)

### Data vs Code (maintainability)

### NLU training label ≡ DiagAct scheme (maintainability + robustness)

### Testing

### Logging (first-class citizen)

## QA

- "yimin, ..."
  - barge-in

  EventQueue, 语音 + 视觉\
  {Sentence1, Sentence2, ...}

  ASR:
  - 音频 -> 文字
  - segmentation;

  NLU:\
  文字 -> JSON {intent (查询), key-vals (机票，目的地：上海)}

  你好，早上好 -> Greet

  ```
  我想要从*洛杉矶*飞往*上海* NLU
  0 0 0 0 B I I  0 0 B I
          From       Dest
  ```
