---
feat: FEAT-0015
domain: backend
adrs: [0017]
status: todo
depends_on: [TASK-0015]
---

# The outstanding-record ledger in the walk

A record whose retrieval or parse fails goes to the ledger and the walk carries on; a resumption
retries the ledger **before** the cursor; and `COMPLETE` is gated on nothing being outstanding.

The table and its port are
[TASK-0002](TASK-0002-licitacions-per-organo-import-state.md)'s; this task is what drives them, and
it is separate from [TASK-0015](TASK-0015-single-organo-initial-import.md) because it is a whole
mechanism rather than a clause of the walk — the one FEAT-0009 never had, and what makes #41's
*"retrieves it on a later run"* reachable at all.

**Why it is reachable no other way.** The cursor has long since advanced past the failure by the
time the walk ends, the incremental mode is a later feature, and R12's historical re-read is a later
feature too. Without the ledger, a record that failed once is never looked at again.

At 16 798 retrievals per Órgano an occasional failure is a **certainty rather than an incident**,
which is why this is designed rather than left to a log line.

## Scope

- **A record whose retrieval or parse fails is written to the ledger and the walk continues.** It is
  one procedure's failure, never its Órgano's (R30). The ledger row carries the identifier **and the
  four listing-sourced fields** the retry will need, since a retry has no listing entry.
- **A resumption retries the ledger first**, before continuing from the cursor. A record that parses
  on the retry is stored through `StoreLicitacion` and its ledger entry cleared; one that fails
  again stays.
- **`COMPLETE` only if nothing is outstanding.** The walk's exhausted-listing ending becomes
  `COMPLETE` when `hasOutstanding` is false and leaves the Órgano `INCOMPLETE` otherwise — which is
  honest, its history is not fully loaded, and is what makes it resumable.
- **It is a set, not a queue.** No attempt count, no backoff, no next-attempt time: the next run
  tries each entry once more. That machinery answers a problem nobody has measured.

**The cost of a record that fails for ever is stated, because it is not zero.** The Órgano stays
`INCOMPLETE` and is re-walked by every run. The retry itself is cheap — one record fetch plus a
listing walk, not 16 798 records — but `INCOMPLETE` is what SPEC-0008 #37 renders as *still
filling*, so one bad record out of 16 798 would make SERGAS's licitacións section announce itself
partial indefinitely. It clears when the record parses or when an administrator removes it under
R15, and **neither escape hatch is built here** — the second is a later feature's.

**Out of scope:** the walk itself (TASK-0015), and how a coverage row reports an Órgano left
`INCOMPLETE` ([TASK-0016](TASK-0016-multi-organo-orchestration-and-failure-isolation.md)).

## Acceptance criteria

- **One record that fails every time does not stop the Órgano and does not stop the walk**: the
  other 99 of its page are stored, the listing is walked to exhaustion, the failed identifier is in
  the ledger, and the Órgano ends **`INCOMPLETE`** rather than complete or failed.
  ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #41)
- **The next run retries the ledger before touching the cursor** — asserted on the order of calls
  the record port receives, since the whole property is that the retry happens first.
  (SPEC-0008 #41)
- A record that parses on the retry is stored, its ledger entry cleared, and — nothing else
  outstanding — the Órgano moves to `COMPLETE`. (SPEC-0008 #41, #12)
- A retry stores the procedure **from the ledger's own four listing fields**, with no listing
  request issued for it. (SPEC-0008 #41)
- An Órgano whose listing is exhausted with an empty ledger reaches `COMPLETE`; one with a single
  entry stays `INCOMPLETE`. (SPEC-0008 #12)
- The same failing record met on two consecutive runs leaves **one** ledger row, not two.
- A retrieval failure and a parse failure both land in the ledger — the walk does not distinguish
  them, because both leave the same gap. (SPEC-0008 #41)
- Integration-tested against PostgreSQL and a stubbed source, since the retry-before-cursor ordering
  and the completion gate are both about durable state across two runs.
