---
feat: FEAT-0016
domain: backend
adrs: [0002, 0008, 0022, 0023]
status: todo
depends_on: [TASK-0001, TASK-0002]
---

# The paged, ordered and counted reads, built around one predicate

`JdbcVisibleLicitacionRepository`: one page of one Órgano's licitacións of one year, in one of four
orderings, under at most two narrowings, with the count of the **whole selection**.

It is a class of its own rather than reads folded into `JdbcLicitacionRepository`. The contratos
menores precedent puts both on one class because *"a page of contratos menores and a batch of them
are two questions of one table"*; here the browse read spans five tables and the write adapter
upserts an aggregate, so that reason does not carry.

> **Nothing writes a licitación row until FEAT-0015's
> [TASK-0014](../FEAT-0015-licitacions-initial-import/TASK-0014-reconciling-a-restated-procedure.md)
> lands, and `licitacion_award.operador_economico_id` stays null until its
> [TASK-0012](../FEAT-0015-licitacions-initial-import/TASK-0012-resolve-the-awardee.md) does.** This
> task's tests seed their own rows, so it is buildable now; what it cannot do is prove anything about
> real data.

## Scope

- **The predicate, written once** and shared textually by the page and the count:

  ```sql
  organo_id = ? AND publication_year = ? AND withdrawn = FALSE
  ```

  ❗ **Three conjuncts, and no fourth.** `JdbcContratoMenorRepository.VISIBLE_WHERE` is four and reads
  as the obvious starting point; adapting it carries `amount IS NOT NULL` and
  `operador_economico_id IS NOT NULL` across by inertia, and the result withholds exactly the rows
  #20 and #36 require to be **shown**. R25 makes an interpretable publication date the whole test —
  an award, an awarded amount and a resolvable awardee are all deliberately not required.
- **The page**, carrying no `ORDER BY` of its own until the clause
  [TASK-0001](TASK-0001-selection-value-types-and-read-ports.md) answers is appended, plus
  `LIMIT`/`OFFSET`, and joined to:
  - `licitacion_state`, for the code and label the row carries;
  - a **`LEFT JOIN LATERAL` over `licitacion_award`** (non-withdrawn) answering the awarded sum, the
    count of **awarded lotes**, and the **distinct non-null** awardee operador ids;
  - a **`LEFT JOIN LATERAL` over `licitacion_lote`** (non-withdrawn) answering the stored lote count;
  - `operador_economico`, joined **only when there is exactly one** awardee id, for that operador's
    name and its nullable fiscal identifier.

  ❗ **Laterals, never plain joins.** `JOIN licitacion_award` would put a five-lote procedure in the
  page **five times** and count it five times — the direct violation of #10's *"neither appears more
  than once in any list or count"*. The rule for this whole feature is: **aggregates come from a
  lateral, membership comes from an `EXISTS`, and neither is ever a join in the page or the count.**
- **The two narrowings**, both optional and both applied to the whole selection:
  - **CPV** — an `EXISTS` over non-withdrawn `licitacion_cpv`, matching a classification hanging off
    **a lote or off the procedure as a whole** (`lote_id IS NULL`). It must not require a lote and
    must not join `licitacion_lote`: measured on procedure 822054, a procedure with two lotes and two
    separate awards published **every** CPV row against itself, and R8's amendment admits exactly
    that;
  - **state** — an equality on the state's **code**, resolved by a scalar subquery so an unknown code
    matches nothing rather than raising.
- **The count**, over the same predicate and the same narrowings, and **joining nothing at all**.
  FEAT-0011 measured that adding its awardee join took the planner off the partial index and onto a
  heap scan; here the laterals are worse, because they do per-row aggregate work for a number that
  cannot depend on them.
- **The projection's three decided values**, assembled into
  [TASK-0001](TASK-0001-selection-value-types-and-read-ports.md)'s types:
  - the **amount** — `COALESCE(sum of non-withdrawn award amounts, base_budget)` — with basis
    `AWARDED` when **at least one award carries an amount**, `BUDGET` when none does and a budget is
    published, and `UNSTATED` when neither;
  - **`partial`** — true when the basis is `AWARDED`, the procedure has stored non-withdrawn lotes,
    and **fewer of them are awarded than it holds**. ❗ Computed from stored lotes, **never from
    `licitacion.lote_count`**, which is the source's own count: on 822054 it said `2` while the lotes
    table was empty and the award table named both;
  - the **awardees** — the count of distinct non-null awardee operador ids, and that operador when
    the count is 1. The null-exclusion is R20 word for word, and a procedure whose only award resolved
    to nobody states **zero**.

  A **procedure-wide award on a procedure that has lotes** counts toward the sum and **not** toward
  the awarded-lote count — which is why those are two expressions rather than one.
- **A `Page<VisibleLicitacion>`** assembled from the content and the count, per
  [ADR-0022](../../architecture/0022-paged-collection-contract-from-micronaut-data.md), read
  `@Transactional(readOnly = true)`.

**Out of scope:** the facets ([TASK-0004](TASK-0004-year-cpv-and-state-facets.md)), the semi-join
([TASK-0006](TASK-0006-licitacions-in-the-visible-set.md)), the use cases and every HTTP concern.

## Acceptance criteria

Integration-tested against PostgreSQL (Testcontainers), seeding one Órgano-year holding **all** of:

- a procedure with **no award at all** — shown, basis `BUDGET`, **zero** awardees;
  ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #36)
- one whose single award resolved to **nobody** — shown, its awarded amount stated, **zero**
  awardees, and its published `awardee_name` **not selected by any statement in this task**;
  (SPEC-0008 #20, #24)
- one with **neither** an award nor a base budget — shown, basis `UNSTATED`, no value; (SPEC-0008 #35)
- one with **no lotes** and one award — one row, basis `AWARDED`, not partial, **one** awardee named
  with its fiscal identifier; (SPEC-0008 #10, #29, #35)
- one with **five lotes awarded to one operador** — **one** row in the page and **one** in the count,
  the awarded sum, **one** awardee named; (SPEC-0008 #10, #29)
- one with **five lotes awarded to five operadores** — one row, awardee count **5**, no name;
  (SPEC-0008 #29)
- one **partly awarded** — three lotes, two awarded — stating the sum of the two, `partial` true;
  (SPEC-0008 #35)
- one whose `lote_count` says `2` while **no** lote row exists and two procedure-wide awards do —
  **not** marked partial, proving the marker reads stored lotes; (SPEC-0008 #35)
- one **withdrawn** procedure, one with a **withdrawn award**, one with a **withdrawn lote** and one
  with a **withdrawn CPV** — absent, or excluded from the aggregate, in **every** statement including
  the count; (SPEC-0008 #18 read half)
- one with **no publication date** — absent from every page and every count. (SPEC-0008 #36)

And:

- **The count of the selection equals the number of rows paging yields**, over every one of the four
  orderings and every filter combination, with none repeated and none skipped — including where a
  procedure carries the filtered CPV on **three** lotes, which contributes **one** to both.
  (SPEC-0008 #10, #28, #33)
- Sorting by amount descending puts the highest-amount licitación **of the whole year** on the first
  page, not of the page previously displayed; a row with an `UNSTATED` amount sorts **last in both
  directions**. (SPEC-0008 #34, #35)
- Two procedures published on the same date are ordered by publication identifier in the sort's own
  direction, so the order is determinate and paging over the tie repeats and skips nothing.
  (SPEC-0008 #30)
- A CPV filter matching a classification held **against a procedure that has lotes** returns that
  procedure; one matching a classification on **one of its lotes** returns it once. (SPEC-0008 #33)
- A state filter naming a code the year does not contain returns an empty page with a true total of
  **zero**, and does not raise. Filtering by code **101** does not return a procedure in code **102**,
  though both are labelled *Histórico*. (SPEC-0008 #33)
- A page beyond the last is **empty and carries the selection's true total**, rather than an error or
  a clamped page. (SPEC-0008 #28)
- The read **refuses any `ORDER BY` clause `LicitacionSortKey` could not have produced**, deriving
  that set from the enum itself — a second line of defence, since these are native statements and a
  property name would be interpolated verbatim.
