---
feat: FEAT-0015
domain: backend
adrs: [0002, 0008]
status: done
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
  - **`licitacion`** — `id UUID PRIMARY KEY DEFAULT uuidv7()`, `publication_id TEXT NOT NULL
    UNIQUE` as the natural key, an FK to `organo_contratacion`, a nullable `publication_date`, a
    `state_id` FK, three nullable type FKs, the rest of R7's fields, and the withdrawal marker.
    `contrato_menor`'s shape — a published unique key beside a surrogate `id` — with the key's
    type widened.

    **`TEXT`, not `BIGINT`, though every identifier measured is an integer.** How the source mints
    them is the source's business, and one that changed shape would otherwise cost a column type,
    a migration and a re-import of every procedure rather than a parse at the adapter. The column
    is matched on and never ordered, summed or incremented, so text costs nothing here; the
    listing endpoint does the ordering the walk resumes by. `contrato_menor.source_id` stays
    `BIGINT` — widening a shipped column of millions is not this task's change to make.
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
  - **`licitacion_lote`** — `id UUID PRIMARY KEY DEFAULT uuidv7()`, its `licitacion_id`, its
    identifier as **`TEXT`** in a `lote_identifier` column (measured: `OU0028`, `LU4001` and
    `CO0642` are all real lote identifiers), a **`lote_key TEXT NOT NULL`** carrying the same
    identifier reduced by TASK-0004's `LoteKey` (see *The lote's key* below), the two optional
    extras and the withdrawal marker;
  - **`cpv`** and **`nut`** — the two regulated European lists, each `id UUID PRIMARY KEY DEFAULT
    uuidv7()` beside `code TEXT NOT NULL UNIQUE` and a nullable `description` that is
    **deliberately not unique**, on the `licitacion_state` shape and for its reason: an import
    matches an entry on the code the list assigns, never on wording that is translated and revised
    and shared across sibling entries. **Neither is seeded** — CPV is versioned, the 2008 revision
    retired codes the 2003 one issued, and this system imports procedures published across both, so
    an unseen code creates its row inside the transaction that stores the procedure rather than
    failing a foreign key. Unprefixed, unlike the four `licitacion_*` vocabularies, because these
    are external standards rather than this source's own vocabulary;
  - **`licitacion_cpv`** and **`licitacion_nut`** — `id UUID PRIMARY KEY DEFAULT uuidv7()`,
    `licitacion_id`, a `cpv_id` / `nut_id` FK **NOT NULL**, the diffusion date, a **nullable**
    `lote_id` and the withdrawal marker. The code itself is not a column here: a classification row
    records that this procedure cites that entry, and the entry is the list's;
  - **`licitacion_award`** — `id UUID PRIMARY KEY DEFAULT uuidv7()`, `licitacion_id`, a nullable
    `lote_id`, the resolution, its date, the amount, the execution period, the published awardee
    name, a nullable `operador_economico_id` FK, the resolution path and the withdrawal marker;
  - **`licitacion_formalisation`** — `id UUID PRIMARY KEY DEFAULT uuidv7()`, `licitacion_id`, a
    nullable `lote_id`, the date, contratista name, published fiscal identifier (nullable),
    nationality, amount and the withdrawal marker.

  **Every one of the five carries a surrogate `id` beside its natural key, and the nullable
  `lote_id` is why.** Four of the natural keys below contain it, and PostgreSQL forbids a null in a
  primary key — so those keys are `UNIQUE … NULLS NOT DISTINCT` constraints and the primary key is
  the surrogate. [TASK-0004](TASK-0004-award-points-and-competition-value-types.md) mints a typed
  identifier for each, so the domain records carry `LoteId`, `CpvClassificationId`,
  `NutClassificationId`, `AwardId` and `FormalisationId` respectively.
- **The column names TASK-0004's records map to, where prose above would not settle them.** Under
  [ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md) the
  record *is* the mapping, so a migration naming a column differently is a runtime failure rather
  than a mismatch anyone reviews. Micronaut Data derives the rest from the component name; these
  are the five a reader would otherwise guess wrong:

  | Table | Prose above | Column the record maps to |
  | --- | --- | --- |
  | `licitacion_lote` | its identifier | `lote_identifier` |
  | `licitacion_cpv` / `licitacion_nut` | the diffusion date | `diffusion_date` |
  | `licitacion_cpv` / `licitacion_nut` | the entry it cites | `cpv_id` / `nut_id` |
  | `licitacion_award` | its date | `resolution_date` |
  | `licitacion_award` | the resolution path | `awardee_resolution_path` |
  | `licitacion_formalisation` | the date | `formalisation_date` |
  | `licitacion_formalisation` | published fiscal identifier | `fiscal_identifier` |

  **`fiscal_identifier`, not `fiscal_id`.** `operador_economico` spells the same concept the
  shorter way, and the divergence is deliberate rather than an oversight: the two hold different
  things — one is the catalogue's key, the other is what a single formalisation cell carried, which
  may identify nobody the catalogue holds. Renaming either to match would suggest they are the same
  column.
- **Every child carries `licitacion_id NOT NULL`, and a null `lote_id` means *the procedure as a
  whole* rather than *unattached*.** An earlier draft gave three of these tables only a nullable
  `lote_id`, which left the lotless procedure — **85 of 100 measured, the ordinary case** — with
  nowhere to hang its award, its formalisation or its classification, and made this task's own
  procedure-level criterion unwritable.
- **The natural key each child upserts on**, stated here because the idempotence criteria cannot be
  run without one and no other task supplies them:

  | Table | Natural key |
  | --- | --- |
  | `licitacion_lote` | `(licitacion_id, lote_key)` |
  | `cpv` / `nut` | `code` |
  | `licitacion_cpv` / `licitacion_nut` | `(licitacion_id, lote_id, cpv_id)` / `(…, nut_id)`, `NULLS NOT DISTINCT` |
  | `licitacion_award` | `(licitacion_id, lote_id)`, `NULLS NOT DISTINCT` |
  | `licitacion_formalisation` | `(licitacion_id, lote_id)`, `NULLS NOT DISTINCT` |

  `NULLS NOT DISTINCT` is load-bearing: PostgreSQL treats NULLs as distinct by default, so without
  it the procedure-wide row of a lotless procedure would insert afresh on every re-import — which is
  every procedure, on every run.

  **The lote's key is the reduced spelling, not the published one, and this is the decision the
  task owed an answer to.** `lote_identifier` holds what the source printed — `05` stays `05`,
  which this task's own criterion requires — while
  [TASK-0004](TASK-0004-award-points-and-competition-value-types.md)'s `LoteKey` holds that `05`
  and `5` are one lote. The two disagree, and the disagreement is reachable: a procedure whose
  award table spells one lote both ways, or a source that respells the padding between imports,
  would insert a second lote row where the normaliser says there is one — the artefact `LoteKey`
  was measured over 240 procedures to eliminate, and it would pass a no-duplicate criterion keyed
  on the published spelling while the source could still break it.

  So `licitacion_lote` carries **both**: `lote_identifier` as published, for display, and
  `lote_key` as the unique key. **The adapter writes `lote_key` from `LoteKey.normalise`**, rather
  than the column deriving it in SQL, so the rule stays in one language: a `GENERATED ALWAYS AS`
  expression would restate the same reduction where the two spellings of it could drift apart with
  nothing to notice. `lote_key` is therefore the one column no record component maps —
  [ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md)
  reads on the write path here, and every write in this task is a hand-written statement rather
  than a derived `save`. The upsert refreshes `lote_identifier` from the incoming row, so a
  respelling moves the published spelling on the lote already stored instead of adding a twin, and
  it **refuses** an identifier that reduces to nothing (`_`, `-`): those are how the source spells
  *the procedure as a whole*, which hangs off the procedure with no lote row at all.

  It was **not demonstrated**: the measured padding varies between procedures rather than within
  one, so this is a re-import hazard rather than a defect anyone had seen. The criterion it adds is
  **one procedure whose award table names `01` and `1` leaves one lote**, below.
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
- **JDBC repositories** implementing TASK-0003's ports and the award-point ports — including one
  per vocabulary, each upserting on its published key (`code` for the state, `name` for a type,
  `code` for a CPV or NUTS entry) and answering the stored value with its identity.

  **The two regulated lists need their ports declaring here.**
  [TASK-0004](TASK-0004-award-points-and-competition-value-types.md) puts every repository out of
  scope, so it ships `Cpv` and `Nut` as entities and no `CpvRepository` / `NutRepository` beside
  them. This task owns both, in `domain` under ADR-0002, on the shape TASK-0003's four vocabulary
  ports set: an `upsert` and nothing else. A CPV upsert **must not** write the description — it
  matches on the code and leaves whatever wording is already stored alone, so an import cannot
  blank a description something else supplied, and it therefore answers the **stored** description
  rather than the one it was handed.

  **So do the five award-point ports**, for the same reason: TASK-0004 ships `Lote`,
  `CpvClassification`, `NutClassification`, `Award` and `Formalisation` as entities with no
  repository beside them, so `LoteRepository`, `CpvClassificationRepository`,
  `NutClassificationRepository`, `AwardRepository` and `FormalisationRepository` are declared here
  too — seven new ports in all, each an `upsert` answering the stored value with its identity, and
  none with a delete.
- **`findByPublicationId` must fetch-join all four vocabulary references**, and the joins must be
  **left**:

  ```java
  @Override
  @Join(value = "state", type = Join.Type.LEFT_FETCH)
  @Join(value = "contractType", type = Join.Type.LEFT_FETCH)
  @Join(value = "procedureType", type = Join.Type.LEFT_FETCH)
  @Join(value = "tramitacionType", type = Join.Type.LEFT_FETCH)
  public abstract Optional<Licitacion> findByPublicationId(PublicationId publicationId);
  ```

  **This is not a tuning choice, it is what makes the read work at all.** Micronaut Data has no
  implicit to-one fetch: unjoined, the mapper tries to build each reference as an id-only stub,
  which it can only do for an entity whose constructor takes nothing or takes the identity alone.
  None of the four qualifies, so all four come back null, and `Licitacion`'s constructor refuses
  the null state — on every stored row, not an unlucky one. The three nullable types would have
  failed *silently* instead, arriving null with their columns populated.

  `ContratoMenor` is not a precedent to lean on here: nothing reads it back through its port, so
  its `@Relation` has never been exercised on a read path. `ContratoMenorTestRepository` and
  `OrganoRepository` are, and both declare `LEFT_FETCH`.

  **Left** rather than the default inner join, because three of the four references are nullable:
  an inner join would drop any procedure whose record published no contract type from a read that
  asked for it by its identifier. How often that happens is not measured — `Tipo de contrato` is
  recorded in [`design/source-contract.md`](design/source-contract.md) as published, with no
  figure for how often it is absent — and the join does not need it to be common to be wrong.
- **The write refuses a procedure whose state or any named type carries no identity.** Storing the
  vocabularies first is the caller's job and the port documents it, but a null there would reach
  the database as a null in a `NOT NULL` foreign key, whose error names the column rather than the
  mistake. `JdbcOperadorRepository.retainName` throws for the same class of error and is the shape
  to copy.
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
- **One procedure whose award table names `01` and `1` leaves one lote**, stored under the spelling
  the source published. This is what keying on `lote_key` rather than on `lote_identifier` buys, and
  the test that would fail if the key moved back. (SPEC-0008 #10, #44)
- An award stores with a null operador and with a null lote, independently. (SPEC-0008 #20)
- A procedure with two lotes stores two awards, and neither is duplicated at procedure level: a
  query for procedure-level awards on that procedure returns none. *This is the regression test for
  the R8 invariant, which the parse enforces rather than the schema.* (SPEC-0008 #9)
- A formalisation stores with no fiscal identifier — the cell whose trailing token was not
  identifier-shaped. (SPEC-0008 #46)
- The withdrawal marker exists on **all five child tables** — and on `licitacion` itself, created
  empty — and defaults to *not withdrawn* on insert, so nothing an import stores is born invisible.
  No vocabulary table has one. (SPEC-0008 #16)
- **`findByPublicationId` reads a stored procedure back with all four vocabulary references
  populated**, and reads back a procedure that published none of the three types with its state
  populated and those three null. This is what proves the fetch joins: without them the first case
  throws and the second silently loses data, and without the joins being *left* the second case
  returns no row at all. (SPEC-0008 #7 per-field half)
- **Codes 101 and 102 both store, both labelled *Histórico***, and reading either back gives the
  code it was stored under. The label carries no unique constraint, and this is the test that
  would fail if one were added. (SPEC-0008 #44)
- **A state code the table has never held — say `7` — stores inside the transaction that stores the
  procedure**, creating its row rather than failing the foreign key. Same for a contract type name
  nobody has published before. This is R33's open set, and a seeded catalogue would fail it.
  (SPEC-0008 #44)
- **Re-storing a procedure naming a vocabulary value that already exists leaves one row** in each of
  the four tables, matched on the published key — `code` for the state, `name` for a type — so a
  run over thousands of procedures does not grow the vocabularies. (SPEC-0008 #17)
- **The write refuses a procedure whose state or type carries no identity**, rather than writing a
  null into a `NOT NULL` foreign key — the diagnosis belongs where the mistake is, as
  `retainName` already does for an unstored operador.
- Integration-tested against PostgreSQL (Testcontainers).
