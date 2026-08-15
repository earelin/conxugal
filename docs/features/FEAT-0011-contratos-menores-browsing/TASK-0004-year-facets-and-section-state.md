---
feat: FEAT-0011
domain: backend
adrs: [0002]
status: done
depends_on: [TASK-0001, TASK-0002]
---

# Year facets, and what the section says about itself

The read answering which years an Órgano has **visible** contratos menores in, and the use case
that turns it — with FEAT-0009's per-Órgano import state and the catalogue row — into everything
R18 and R19 decide about the section **before any contract is fetched**.

Kept apart from [TASK-0003](TASK-0003-paged-ordered-counted-reads.md) because it is a different
query shape with a different test surface: one distinct-value read with no paging, no ordering
key and no count.

## Scope

- **The facet read** on `VisibleContratoMenorRepository` — the read port
  [TASK-0001](TASK-0001-selection-value-types-and-read-ports.md) declared, not the store port
  beside it, since this asks the same Órgano-and-visibility question the four orderings do and
  `ContratoMenorRepository` is *"the port for storing contratos menores"* by its own javadoc.
  Unlike task 3's, it is declared and implemented here in one task, so nothing has to wait.
  Implemented in `JdbcContratoMenorRepository`:

  ```sql
  SELECT DISTINCT publication_year
    FROM contrato_menor
   WHERE organo_id = :organoId
     AND publication_year IS NOT NULL
     AND amount IS NOT NULL
     AND operador_economico_id IS NOT NULL
   ORDER BY publication_year DESC
  ```

  Answering `List<YearSelection>`, **newest first** — which is the order the chooser shows and the
  order R19's default reads from, so the **first** entry is the year the section opens on. It
  carries the same visibility predicate as every other read here, and it is an **index-only scan**
  because that predicate is the index's own.

  **The `publication_year IS NOT NULL` conjunct is load-bearing, and this is the only read that
  needs to write it.** Everywhere else the year is an equality test, which excludes an undated
  contract for free; here there is no equality test to do it. A contract holding an amount and an
  awardee but **no date** is an anomaly R28 withholds, yet it satisfies the index predicate, enters
  the index under a null year, and `DISTINCT` would answer that null *as a year* — **first**, since
  `DESC` orders nulls before every real one. So the section would open on a year that is not one,
  and `YearSelection`, which has no representable absence, refuses it: a 500 rather than a stray
  chooser entry. [TASK-0002](TASK-0002-visible-browse-schema-and-indexes.md)'s schema test pins
  this statement and asserts its **result** as well as its plan, for exactly this reason.
  PostgreSQL turns the conjunct into an index condition, so the index-only scan is unaffected.
- **`DescribeContratosMenoresSection`** in `gal.conxugal.domain.contrato` — a use case answering
  `Optional<ContratosMenoresSection>` for an Órgano, where the section is a record of:
  - the offered **years**, newest first;
  - **`partial`** — the Órgano's initial import has not completed, so what is shown is incomplete;
  - **`updating`** — the Órgano is still being refreshed.
- **Presence is derived, not asserted**: the use case answers `Optional.empty()` when the facet
  read returns no year, and there is **no separate flag** saying whether the Órgano has contracts.
  That is what makes *once the section is present it is never empty* true by construction — the
  chooser offers only years that have contracts, so no choice a reader can make produces an empty
  list — and it is what makes an Órgano holding **only** anomalous contracts indistinguishable
  from one holding none.
- **The two flags are derived, and they are two:**
  - `partial` is true when the Órgano's `ContratosMenoresImportState` is not `COMPLETE`, which
    includes **an Órgano with no state row at all** — that is how *never started* is represented,
    there being no stored value for it;
  - `updating` is true when the catalogue row is **active and marked**;
  - they are **orthogonal**, never collapsed into one status. An Órgano unmarked halfway through
    its initial import is both partial and no longer updated, and one enum would have to lie in
    exactly that case.
- **What is disclosed, and the narrowing that is deliberate.** Both flags are produced **only for
  an Órgano that already has a section** — only for one that already holds visible contracts — so
  R18's protected question, *is this Órgano imported at all*, stays unanswerable for an Órgano
  with none. Nothing here is added to `GET /api/organos`, and `updating` is named for what a
  reader needs to know — *this data is still being refreshed* — not for the ADMIN-only mark it
  happens to coincide with today.
- Unit-tested with the three ports stubbed by Mockito, over **every state combination**: complete
  and marked; complete and unmarked; incomplete and marked; incomplete and unmarked; inactive; and
  **no import-state row at all**. Plus an Órgano whose only contracts are anomalous, which must
  answer with no section.

## What building it found

> **Amended in two places, neither of them a change of design.** The section is still derived —
> presence from the years, `partial` and `updating` orthogonal and produced only where a section
> is — and every acceptance criterion below is met as written. What changed is which collaborators
> answer the two flags, and which layer reads a column of years.
>
> - **Two ports, not three.** The scope names FEAT-0009's per-Órgano import state and the catalogue
>   row as two sources and so implies two repositories to read them from. They are already one
>   read: `OrganoRepository.findById` carries the state on a `LEFT_FETCH` join every other reader
>   of an Órgano uses, and `OrganoDeContratacion` already answers both of this task's questions —
>   `importStatus()` reads a missing state row as `NEVER_STARTED`, which is exactly how *never
>   started* is represented here, and `eligibleForImport()` answers *active and marked* as one
>   fact. So `partial` is `importStatus() != COMPLETE` and `updating` is `eligibleForImport()`,
>   both off the aggregate. Injecting `ContratosMenoresImportStateRepository` beside it would
>   restate both derivations and give this use case its own opinion about what a missing row means
>   — which is the one thing `importStatus()` exists to stop each caller having.
>
>   The unit tests therefore stub **two** ports and still walk every state combination the scope
>   enumerates, the no-state-row case included: it is an Órgano whose `importState` is null.
> - **The facet read is the one browse read the framework can map**, and it is a `@Query` rather
>   than the hand-written statement [TASK-0003](TASK-0003-paged-ordered-counted-reads.md) had to
>   fall back to. Nothing that stopped the page being projected applies: a column of years is one
>   column, not a record whose mapping is built outside the container, so each value is rebuilt
>   through the core conversion service — the `TypeConverter` half
>   [TASK-0001](TASK-0001-selection-value-types-and-read-ports.md) added for exactly this read, and
>   which is now exercised by it rather than only by its own unit test.
>
>   One detail is worth recording because it cost a red test: a mapped `@Query` binds **named**
>   parameters, and a `?` in one is not a placeholder but a literal PostgreSQL refuses to bind. The
>   statement therefore says `:organoId` where [TASK-0002](TASK-0002-visible-browse-schema-and-indexes.md)'s
>   pinned SQL says `?` — and the statement *emitted* is byte-identical to the pinned one, which is
>   asserted character for character rather than trusted, exactly as task 3 does for the page and
>   the count.

## Acceptance criteria

- An Órgano with visible contratos menores answers with a section whose years are exactly the
  years it has visible contracts in, newest first, and nothing else — no *all years* entry and no
  *undated* entry.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #43)
- An Órgano with no contratos menores at all, and an Órgano holding **only** contracts missing a
  date, an amount or an awardee, both answer with **no section** and are indistinguishable from
  each other. (SPEC-0005 #50, facet half)
- `partial` is true for an Órgano whose import state is `INCOMPLETE` and for one with **no state
  row**, and false only for `COMPLETE`. (SPEC-0005 #26, state half)
- `updating` is true only for an Órgano that is both active and marked; unmarking it or its going
  inactive makes it false, and neither changes `partial`. (SPEC-0005 #7 third clause, #26)
- Both flags can be true at once, and `partial` true with `updating` false is a state the type can
  express — an Órgano unmarked halfway through its initial import. (SPEC-0005 #26)
- The facet read excludes anomalous contracts from the years it offers: an Órgano whose only 2023
  contract has a null amount offers no 2023. (SPEC-0005 #50)
- The use case answers no section — not an empty one — when there are no years, and exposes no
  *has contracts* flag beside them. (SPEC-0005 #26)
- Unit-tested with Mockito-stubbed repository ports, asserting on the answer rather than on calls.
