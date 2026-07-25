---
feat: FEAT-0006
domain: backend
adrs: [0002, 0011]
status: done
depends_on: [TASK-0001]
---

# Source port + contratosdegalicia adapter

The `OrganoSource` port and the driven adapter that fetches and parses the published list.
Governed by [ADR-0002](../../architecture/0002-hexagonal-architecture.md) (the source is a
driven adapter behind a port); outbound retrieval is blocking I/O on the virtual-thread
executor per [ADR-0011](../../architecture/0011-blocking-io-virtual-threads.md).

## Scope
- `OrganoSource` port (in `domain`) returning the flat list of source entries — each a
  (`sourceKey`, `name`) value — independent of how they are fetched.
- Driven adapter that retrieves and parses the Órganos list published by
  contratosdegalicia.gal. The list is embedded directly in the static `portada.jsp`
  page's HTML (a `<select id="organoA">` of `<option>`s, one per body) and the source
  encodes text as **ISO-8859-1**.
- Derive each entry's stable `sourceKey` from the source's own identifier for the
  organism (the `<option>`'s `value`).
- Signal a clear, typed failure when the source is unreachable or returns an unusable
  response (including an empty or implausibly small list — see the feature's edge cases),
  rather than returning a partial/empty success.
- A follow-up migration on the `organo_contratacion` table (created by TASK-0002) drops
  the `acronym` column — the source's trailing `(XXXX)` convention has no deterministic
  rule for telling an acronym apart from an ordinary parenthetical qualifier — and flips
  `active`'s default to `false`, so a newly discovered Órgano starts inactive.

## Acceptance criteria
- The adapter returns each published Órgano as (`sourceKey`, `name`), decoding
  ISO-8859-1 so accented names are stored without mojibake.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #3)
- An unreachable source, or an unusable / empty / implausibly small response, surfaces as
  a failure — not an empty success that could later deactivate the catalogue. (SPEC-0004
  #13)
- Parsing and failure handling are integration-tested against a **stubbed** source
  (WireMock or similar) — no live network.
