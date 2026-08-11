---
feat: FEAT-0011
domain: backend
adrs: [0002]
status: todo
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

- **The facet read** on `ContratoMenorRepository`, implemented in `JdbcContratoMenorRepository`:

  ```sql
  SELECT DISTINCT publication_year
    FROM contrato_menor
   WHERE organo_id = :organoId
     AND amount IS NOT NULL
     AND operador_economico_id IS NOT NULL
   ORDER BY publication_year DESC
  ```

  Answering `List<YearSelection>`, **newest first** — which is the order the chooser shows and the
  order R19's default reads from, so the **first** entry is the year the section opens on. It
  carries the same visibility predicate as every other read here, and it is an **index-only scan**
  because that predicate is the index's own.
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
