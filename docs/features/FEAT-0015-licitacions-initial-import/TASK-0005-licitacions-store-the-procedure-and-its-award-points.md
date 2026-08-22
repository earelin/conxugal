---
feat: FEAT-0015
domain: backend
adrs: [0002, 0008]
status: todo
depends_on: [TASK-0003, TASK-0004]
---

# Licitacións store: the procedure and its award points

The tables and JDBC repositories behind
[TASK-0003](TASK-0003-licitacion-domain-model.md)'s aggregate and the award-point half of
[TASK-0004](TASK-0004-award-points-and-competition-value-types.md)'s types. Storage only: nothing
retrieves, nothing parses, and the reconciliation that decides *what* to write is
[TASK-0014](TASK-0014-reconciling-a-restated-procedure.md)'s.

Under [ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md),
the domain records map their own tables; under
[ADR-0002](../../architecture/0002-hexagonal-architecture.md) the SQL lives in `infrastructure`
behind TASK-0003's port.

## Scope

- **A migration** (next free `V` across `db/migration` **and** `db/migration-local`, taken at merge
  time) creating:
  - **`licitacion`** — `id UUID PRIMARY KEY DEFAULT uuidv7()`, `publication_id BIGINT NOT NULL
    UNIQUE` as the natural key, an FK to `organo_contratacion`, a nullable `publication_date`, a
    `state_id` FK, three nullable type FKs, the rest of R7's fields, and the withdrawal marker.
    Exactly `contrato_menor`'s shape, whose `source_id BIGINT NOT NULL UNIQUE` beside a surrogate
    `id` is the precedent.
  - **The four vocabulary tables TASK-0003's entities map**, each `id UUID PRIMARY KEY DEFAULT
    uuidv7()` beside its published natural key:

    | Table | Natural key | Other columns |
    | --- | --- | --- |
    | `licitacion_state` | `code INT NOT NULL UNIQUE` | `label TEXT` — **deliberately not unique** |
    | `licitacion_contract_type` | `name TEXT NOT NULL UNIQUE` | — |
    | `licitacion_procedure_type` | `name TEXT NOT NULL UNIQUE` | — |
    | `licitacion_tramitacion_type` | `name TEXT NOT NULL UNIQUE` | — |

    **The state's label carries no constraint, and that is the point.** Codes 101 and 102 are both
    published as *Histórico*, so a `UNIQUE` there would reject the second real state the source
    publishes.

    **None of the four is seeded.** The state set is not closed — code 7 was never observed and
    higher ones may exist — so the upsert creates a row for a value the source has not published
    before, inside the transaction that stores the procedure. A seeded catalogue would turn an
    unseen code into a foreign-key violation and a rejected procedure, which is the harm R33's
    store-as-published exists to prevent.
  - **`licitacion_lote`** — its `licitacion_id`, its identifier as **`TEXT`** (measured: `OU0028`,
    `LU4001` and `CO0642` are all real lote identifiers), the two optional extras and the
    withdrawal marker;
  - **`licitacion_cpv`** and **`licitacion_nut`** — `licitacion_id`, the code, the diffusion date, a
    **nullable** `lote_id` and the withdrawal marker;
  - **`licitacion_award`** — `licitacion_id`, a nullable `lote_id`, the resolution, its date, the
    amount, the execution period, the published awardee name, a nullable `operador_economico_id`
    FK, the resolution path and the withdrawal marker;
  - **`licitacion_formalisation`** — `licitacion_id`, a nullable `lote_id`, the date, contratista
    name, published fiscal identifier (nullable), nationality, amount and the withdrawal marker.
- **Every child carries `licitacion_id NOT NULL`, and a null `lote_id` means *the procedure as a
  whole* rather than *unattached*.** An earlier draft gave three of these tables only a nullable
  `lote_id`, which left the lotless procedure — **85 of 100 measured, the ordinary case** — with
  nowhere to hang its award, its formalisation or its classification, and made this task's own
  procedure-level criterion unwritable.
- **The natural key each child upserts on**, stated here because the idempotence criteria cannot be
  run without one and no other task supplies them:

  | Table | Natural key |
  | --- | --- |
  | `licitacion_lote` | `(licitacion_id, lote_identifier)` |
  | `licitacion_cpv` / `licitacion_nut` | `(licitacion_id, lote_id, code)`, `NULLS NOT DISTINCT` |
  | `licitacion_award` | `(licitacion_id, lote_id)`, `NULLS NOT DISTINCT` |
  | `licitacion_formalisation` | `(licitacion_id, lote_id)`, `NULLS NOT DISTINCT` |

  `NULLS NOT DISTINCT` is load-bearing: PostgreSQL treats NULLs as distinct by default, so without
  it the procedure-wide row of a lotless procedure would insert afresh on every re-import — which is
  every procedure, on every run.
- **No vocabulary table carries a withdrawal marker.** The four are not parts of a procedure the
  source restates; they are the values it publishes, and a state or a type no procedure references
  any more is still one the source published. R13's withdrawal has nothing to say about them.
- **The withdrawal marker is on every one of the five child tables**, defaulting to *not withdrawn*.
  TASK-0014 needs it on the lote and the classifications too — SPEC-0008 #16 names the lote
  explicitly — and an earlier draft put it only on three. On `licitacion` itself it is created
  empty, on `V13`'s stated reasoning that adding a column later to a table of millions is a
  different operation from creating it now; nothing in **this** feature writes it, since R14 retains
  an absent licitación unchanged and R15's removal is a later feature's.
- **Two tables are separate on purpose.** A procedure can be awarded without being formalised — of
  284 measured award rows, **112** sat on procedures in a state that has no formalisation
  (*Adxudicado* 107 plus *Adxudicado provisional* 5), and **120** had no identifier reachable by
  route A — and the two can name different parties, which
  [TASK-0012](TASK-0012-resolve-the-awardee.md) has a rule for.
- **Indexes: the unique natural keys above, and the foreign keys. Nothing else.** An earlier draft
  added `licitacion (organo_id, publication_date)` "for the year-scoped read", which is exactly what
  `V13` created for `contrato_menor` and exactly what **`V16` dropped** — *"Subsumed by the three
  above… its `publication_date` column is what neither of them needed from it"* — replacing it with
  partial indexes on a generated `publication_year`. The browsing feature measures R32 over its own
  queries and adds what those measurements ask for.
- **JDBC repositories** implementing TASK-0003's port and the award-point ports.
- FKs are plain, with no `ON DELETE CASCADE`: no import deletes a procedure, so a cascade would
  stand in for a path nothing has.

**Out of scope:** the competition tables
([TASK-0006](TASK-0006-licitacions-store-the-competition-tables.md)), every read endpoint, and every
browse-shaped query.

## Acceptance criteria

- The migration applies to an empty database and to one already at the current head, and a migration
  integration test pins each new table's exact column set with `containsExactlyInAnyOrder`.
- **A lotless procedure stores its award, its formalisation and its CPV row**, each with a null
  `lote_id` and a non-null `licitacion_id`, and all three read back attached to that procedure. This
  is the ordinary case and the one an earlier draft could not express.
  ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #9, #10)
- Storing a procedure twice under the same publication identifier leaves **one** row, refreshed in
  place, with no duplicate lote, classification, award or formalisation — including for a **lotless**
  procedure, where every child's key has a null component. (SPEC-0008 #17)
- A CPV row stores with a null `lote_id` **on a procedure that has lotes**, and reads back as
  classifying the procedure as a whole. This is procedure 822054 and the case a `NOT NULL` column
  would have lost. (SPEC-0008 #10 as amended)
- A lote stores under the identifier `OU0028`, and under `05`. (SPEC-0008 #10, #44)
- An award stores with a null operador and with a null lote, independently. (SPEC-0008 #20)
- A procedure with two lotes stores two awards, and neither is duplicated at procedure level: a
  query for procedure-level awards on that procedure returns none. *This is the regression test for
  the R8 invariant, which the parse enforces rather than the schema.* (SPEC-0008 #9)
- A formalisation stores with no fiscal identifier — the cell whose trailing token was not
  identifier-shaped. (SPEC-0008 #46)
- The withdrawal marker exists on all five tables and defaults to *not withdrawn* on insert, so
  nothing an import stores is born invisible. (SPEC-0008 #16)
- Integration-tested against PostgreSQL (Testcontainers).
