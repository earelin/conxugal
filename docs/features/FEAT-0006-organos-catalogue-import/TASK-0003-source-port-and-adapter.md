---
feat: FEAT-0006
domain: backend
adrs: [0002, 0011]
status: todo
depends_on: [TASK-0001]
---

# Source port + contratosdegalicia adapter

The `OrganoSource` port and the driven adapter that fetches and parses the published list.
Governed by [ADR-0002](../../architecture/0002-hexagonal-architecture.md) (the source is a
driven adapter behind a port); outbound retrieval is blocking I/O on the virtual-thread
executor per [ADR-0011](../../architecture/0011-blocking-io-virtual-threads.md).

## Scope
- `OrganoSource` port (in `domain`) returning the flat list of source entries — each a
  (`sourceKey`, `name`, optional `acronym`) value — independent of how they are fetched.
- Driven adapter that retrieves and parses the Órganos list published by
  contratosdegalicia.gal. The list is served **dynamically** (it is not present in the
  static `portada.jsp`) and the source encodes text as **ISO-8859-1**.
- Derive each entry's stable `sourceKey`: the identifier the source itself uses for the
  organism when the retrieval exposes one, otherwise an accent- and case-folded,
  whitespace-collapsed normalisation of the name.
- Signal a clear, typed failure when the source is unreachable or returns an unusable
  response (including an empty or implausibly small list — see the feature's edge cases),
  rather than returning a partial/empty success.

## Acceptance criteria
- The adapter returns each published Órgano as (`sourceKey`, `name`, optional `acronym`),
  decoding ISO-8859-1 so accented names and acronyms are stored without mojibake.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #3)
- Two genuinely distinct bodies never reduce to the same `sourceKey`. (SPEC-0004 #3, #7)
- An unreachable source, or an unusable / empty / implausibly small response, surfaces as
  a failure — not an empty success that could later deactivate the catalogue. (SPEC-0004
  #13)
- Parsing and failure handling are integration-tested against a **stubbed** source
  (WireMock or similar) — no live network.
