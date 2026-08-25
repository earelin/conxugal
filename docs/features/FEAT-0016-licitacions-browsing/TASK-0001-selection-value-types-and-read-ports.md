---
feat: FEAT-0016
domain: backend
adrs: [0002, 0019, 0022]
status: todo
depends_on: []
---

# The selection value types, the row, and the read port

What a licitacións read is **asked for** and what it **answers**, as types, before any statement
exists. Nothing here queries anything: the point is that the rules R22, R23, R24 and R20 impose are
held by the type system rather than remembered at each of the statements
[TASK-0003](TASK-0003-paged-ordered-counted-reads.md) and
[TASK-0004](TASK-0004-year-cpv-and-state-facets.md) write.

## Scope

- **`LicitacionsSelection`** — an `OrganoId`, a `YearSelection`, and **two optional narrowings**: a
  CPV code and a state code. The year has no absence, which is how R22's *there is no all-years list*
  becomes a fact about the type rather than a validation; the two filters are genuinely nullable,
  which is the whole difference between them.
- **`LicitacionStateCode`** — the source's own integer code. ❗ **Never the label.** Codes 101 and
  102 are both published *Histórico*, which is why V19 puts no unique constraint on the label; a
  filter keyed on the label merges two states the source distinguishes.
- **`CpvCode`** — the published code, canonicalised the way `Cpv.code` already is, so a code read
  back from the store and rebuilt is the same code.
- **`LicitacionSortKey`** — `PUBLICATION_DATE` and `AMOUNT`, a `parse` accepting only the wire
  spellings `publicationDate` and `amount`, and a method answering the **whole `ORDER BY` clause**
  for a key and a `Sort.Order.Direction`. Four compile-time constants, and the only place an ordering
  is expressed.

  **It answers a clause rather than a `Sort`, and that is deliberate.** `Sort.Order` cannot express
  `NULLS LAST`, which the amount key needs in both directions because R25 admits a visible procedure
  that states no figure at all. The feature README's *The ordering does not ride on a `Sort`* records
  this as a note against [ADR-0022](../../architecture/0022-paged-collection-contract-from-micronaut-data.md);
  what follows for this task is that **no `Sort` object is constructed here or anywhere downstream**,
  so there is no property name to validate and no allow-list to keep in step.
- **`AmountBasis`** — `AWARDED`, `BUDGET`, `UNSTATED` — and **`StatedAmount`**, holding the basis, a
  nullable `Money` and a `partial` boolean, with two invariants refused in the constructor:
  - the value is absent **exactly when** the basis is `UNSTATED`;
  - `partial` is only ever true with basis `AWARDED`.

  R24 forbids presenting the budget and the awarded sum as one figure and forbids adding them. A row
  carrying **both** figures leaves that rule to whoever renders it; carrying one figure and the basis
  that names it makes the rule unbreakable.
- **`Awardee`** (an `OperadorId`, the R4-selected name, and a **nullable** `FiscalIdentifier`) and
  **`Awardees`** (a count and a nullable sole `Awardee`), with the invariant that the sole awardee is
  present **exactly when** the count is 1 — which is R20's rule stated once.

  The fiscal identifier is nullable because V21 dropped `operador_economico.fiscal_id`'s `NOT NULL`
  so an unidentified consortium could be catalogued, and R20 says such an awardee "is named and
  offers a route like any other — what it lacks is a fiscal identifier to show beside its name, not a
  page to open". `VisibleContratoMenor`'s javadoc argues the opposite for its family; this is not
  that family.
- **`VisibleLicitacion`** — an `@Introspected` record carrying the publication identifier, the
  publication date (**never null**: the year equality withholds an undated procedure), the object
  (nullable), the **state**, the `StatedAmount` and the `Awardees`. It carries **no awardee name of
  its own** (R18, #24) and no `expediente`, `estimatedValue`, type or `loteCount` — those are R21's
  page.

  ❗ **The state's code is never null; its label may be.** `licitacion.state_id` is `NOT NULL`, so
  there is always a state **row** — but `licitacion_state.label` is `TEXT` with no `NOT NULL`, and
  `LicitacionState`'s javadoc says null means the source published nothing there. An earlier draft of
  this task inferred the label's presence from the foreign key, which is a non-sequitur, and a
  constructor built on it would throw on real data. The label is nullable and a state with none is
  identified by its code.
- **`StateFacet`** (a code and a nullable label), **`CpvFacet`** (a code and a nullable description —
  `cpv.description` is nullable too) and **`LicitacionFilterOptions`** holding the two lists. They are
  declared **here, with the port**, so that nothing downstream has to add a method back to it.
- **`VisibleLicitacionRepository`** — the read port, declaring **all three** of its methods and their
  return types: one paged method taking a selection, a sort key and a direction; one answering an
  Órgano's year facets; one answering a selection's `LicitacionFilterOptions`.

  An earlier draft declared two here and deferred the third to
  [TASK-0004](TASK-0004-year-cpv-and-state-facets.md) along with its return types, which left the port
  uncompilable at this task's own landing point and hid a cycle `depends_on:` cannot express. The port
  is one file and it is declared once; TASK-0004 **implements** these, and adds nothing to the
  interface. FEAT-0011's precedent is the same — one port, declared whole in its first task.
- **The ADR-0022 note.** The departure this task implements — an ordering carried as a clause rather
  than on a `Pageable`'s `Sort` — is recorded as a note on
  [ADR-0022](../../architecture/0022-paged-collection-contract-from-micronaut-data.md), beside the
  narrowing FEAT-0011 already recorded there. Two other specs cite that record, so a reader of it must
  find both readings; the note lands with the code that makes it true rather than being owed by
  nobody.
- **`YearSelection` and `YearSelectionConverter` are promoted** out of `gal.conxugal.domain.contrato`
  into a shared domain package — `gal.conxugal.domain.browse`, named here rather than left to whoever
  picks the task up, since four later tasks import from it. It needs its own `@NullMarked`
  `package-info.java`, or the promoted types silently lose null-marking while every criterion below
  still passes. `domain/contrato/package-info.java` documents and `@link`s `YearSelection` and is
  updated with the move.

  **This is the one edit this feature makes to a shipped contratos menores file**, and it is a package
  move with no behaviour change: the `@TypeDef` names its converter by class literal, so the move is
  compile-time only.

  Duplicating it was the alternative and is rejected: it is one concept with no family-specific
  content, and the four-digit bound and the never-throwing `parse` are exactly the things two copies
  would drift on. Importing `domain.contrato` from `domain.licitacion` was the other, and it would
  make one family's section depend on the other's package for a concept neither owns.

**Out of scope:** every statement, every migration, the use cases, and any HTTP shape. Also
**`LicitacionsSection`**, which [TASK-0005](TASK-0005-the-licitacions-read-use-cases.md) owns beside
the use case that produces it, and which no port method here returns.

## Acceptance criteria

- A `StatedAmount` with basis `UNSTATED` and a value, or with a basis of `AWARDED` or `BUDGET` and no
  value, is **refused**. So is one marked `partial` whose basis is not `AWARDED`. Unit-tested per
  case, because these are the invariants the whole of R24 rests on.
  ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #35)
- An `Awardees` with a count of 1 and no sole awardee, or with a count other than 1 and one, is
  **refused**; a count of **0** with no sole awardee is accepted, because a procedure whose award
  resolved to nobody is a real, showable row. (SPEC-0008 #20, #29)
- An `Awardee` is constructible with **no fiscal identifier**, and one with a name and no identifier
  round-trips unchanged. (SPEC-0008 #20 unidentified-consortium half)
- `LicitacionSortKey.parse` accepts `publicationDate` and `amount` and **nothing else** — not a
  column name, not a differently-cased spelling, not an empty string — answering an empty `Optional`
  rather than throwing. (no criterion — SPEC-0008 has no analogue of SPEC-0005 #28; see the README's candidate-criteria table)
- The clause for each of the four (key, direction) pairs ends with the publication identifier in
  **the same direction as the key it breaks**, carries `NULLS LAST` on **both** amount orderings, and
  carries **no `NULLS` clause at all** on either date ordering. Asserted as four exact strings,
  because the feature's index argument and its default ordering both depend on the exact text.
  (SPEC-0008 #30)
- `LicitacionsSelection` is constructible with neither narrowing, with either alone, and with both;
  a `YearSelection` is always required and there is no way to express its absence. (SPEC-0008 #32)
- `VisibleLicitacion` refuses a null publication date, a null state and a null `StatedAmount`, and
  accepts a null `obxecto`. It exposes **no** awardee name, no expediente and no estimated value —
  asserted by the type's own component list, so a later field cannot be added without meeting #24.
  (SPEC-0008 #24, #36)
- A `StateFacet` is constructible with **no label** and a `CpvFacet` with **no description**, and both
  round-trip unchanged — the case a fixture written with fully populated vocabularies would never
  reach. (SPEC-0008 #33)
- **`VisibleLicitacionRepository` compiles with all three methods and every return type they name**,
  with no later task adding to it — asserted by the interface being unchanged after
  [TASK-0004](TASK-0004-year-cpv-and-state-facets.md) lands.
- The promoted package carries its own `@NullMarked` `package-info.java`, and a null passed where a
  promoted type expects a value still fails at the same boundary it did before the move.
- Every contratos menores use of `YearSelection` compiles and its existing tests pass unchanged after
  the promotion; `ContratosMenoresController` still parses and refuses a year exactly as it did.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #27 unchanged)
