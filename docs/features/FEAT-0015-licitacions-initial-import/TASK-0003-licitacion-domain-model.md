---
feat: FEAT-0015
domain: backend
adrs: [0008, 0019, 0023]
status: done
depends_on: []
---

# The `Licitacion` aggregate, its published vocabularies, and their ports

The procedure itself: what R7 requires held as published, with the port the store implements. No
tables yet ([TASK-0005](TASK-0005-licitacions-store-the-procedure-and-its-award-points.md)), no
award points ([TASK-0004](TASK-0004-award-points-and-competition-value-types.md)) and no parsing
([TASK-0008](TASK-0008-record-source-port-and-the-labelled-fields.md)).

A typed identifier under
[ADR-0019](../../architecture/0019-typed-aggregate-identifiers.md) and mapping annotations on the
record itself under
[ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md), on
the `ContratoMenor` / `ContratoMenorId` precedent.

## Scope

- **`LicitacionId`** and its converter, on the `ContratoMenorId` shape exactly: a record wrapping a
  **database-assigned `UUID`**, `@Id @GeneratedValue @Nullable` on the aggregate, null until insert.

  **Not the source's identifier.** An earlier draft of this task made the publication identifier the
  aggregate's identity and called that "the `ContratoMenorId` shape"; both halves were wrong.
  ADR-0019 decides *"a record wrapping a `UUID`… The database keeps assigning the value"*, and
  `ContratoMenorId`'s own Javadoc says *"It is not the source's identifier: that is the contract's
  `sourceId`."* ADR-0023 gives the independent reason — keying on a published value puts it *"in
  every foreign key"* — which here would be six child tables.
- **`PublicationId` is the natural key** a re-import matches on, unique in the table, where
  `ContratoMenor.sourceId` is a bare `long`. Per
  [`design/source-contract.md`](design/source-contract.md) it is shared with contratos menores in
  **one id space**, so it names a publication rather than a licitación.

  **A type of its own, wrapping text**, on the `FiscalIdentifier` shape — a `@TypeDef` over a
  record holding one `String`, with a `toString` that prints the bare value. Two reasons, and the
  first is the stronger: a `LicitacionId` and a `PublicationId` identify the same procedure and
  mean opposite things, one assigned by this system and one by the source, and an earlier draft of
  this task got exactly that confusion wrong. A bare `String` beside a `LicitacionId` invites it
  back; two types make the mix-up a compile error.
- **It wraps text, not a `long`, though every identifier measured is an integer.** How the source
  mints its identifiers is the source's business and nothing published says the shape is fixed; the
  type holds what was published rather than a reading of it. An identifier that stopped being
  numeric then costs a parse at the adapter instead of a column type, a migration and a re-import
  of every procedure. Nothing sorts, sums or increments it — it is matched on and nothing else — so
  text gives up no property this model uses.

  **Two consequences, named here rather than met later.** The walk's resumption orders by the
  identifier *at the source*, which is unaffected: the listing endpoint does the ordering.
  SPEC-0006 R4's tie-break on *"the higher contract identifier"* is affected — it compares a
  licitación's identifier against a contrato menor's `long`, and `NomeRank` holds a `long`. That
  comparison is no longer free, and lexicographic order is not numeric order (`"9"` sorts above
  `"10"`).

  **Settled by [TASK-0021](TASK-0021-settle-the-licitacion-contract-identity-rank.md): the parse,
  which is the cost this bullet accepted.** `NomeRank.sourceId` stays a `long` and a licitación
  parses its `PublicationId` when it ranks — never compares it as text, which would corrupt the
  shipped contratos menores tie-break. The accessor lands with the first caller,
  [TASK-0012](TASK-0012-resolve-the-awardee.md).
- **Its converter carries one interface, not two.** The dual `AttributeConverter` +
  `TypeConverter` shape exists because a database-*generated* id is read back through the core
  conversion service; a publication identifier is supplied by the source and never generated, so
  the attribute half covers every path it travels — `MoneyConverter`'s reasoning, and its
  precedent for what a projection would later need.
- **`Licitacion`**, carrying what R7 requires:

  | Field | Source | Note |
  | --- | --- | --- |
  | publication identifier | listing `id` | the natural key, a `PublicationId` wrapping text |
  | Órgano | the walk | FK |
  | publication date | **listing** `publicado` | **nullable** — an uninterpretable date stores null rather than rejecting the row |
  | last-modified date | **listing** `modificado` | what R11's incremental mode will order on |
  | state | **listing** `estado` + `estadoDesc` | **a reference** to `LicitacionState`, required |
  | expediente | record `Referencia` | free text, published on a minority of records |
  | object | record `Obxecto` | free text, no length cap |
  | contract type | record `Tipo de contrato` | **a reference** to `LicitacionContractType`, nullable |
  | procedure type | record `Tipo de procedemento` | **a reference** to `LicitacionProcedureType`, nullable |
  | tramitación type | record `Tipo de tramitación` | **a reference** to `LicitacionTramitacionType`, nullable |
  | number of lotes | record `Nº lotes` | the source's own figure |
  | base budget | record `Orzamento base de licitación` | `Money` |
  | estimated value | record `Valor estimado` | `Money` |

- **Four fields come from the listing entry, not the record**, and the table says so because it
  changes who supplies them. The record publishes only the state's *label*
  (`Estado do procedemento`) and neither date. So
  [TASK-0014](TASK-0014-reconciling-a-restated-procedure.md)'s `StoreLicitacion` takes **both** the
  listing entry and the parsed record, and
  [TASK-0002](TASK-0002-licitacions-per-organo-import-state.md)'s outstanding ledger carries these
  four values, because a retried record arrives with no listing entry beside it.
- **Four published vocabularies, each its own entity with its own table**, rather than columns on
  the procedure: the state and the three types. A value the source publishes on a thousand
  procedures is then held once, and the procedure refers to it.

  | Entity | Table | Natural key | Notes |
  | --- | --- | --- | --- |
  | `LicitacionState` | `licitacion_state` | `code` | plus a `label` the source repeats |
  | `LicitacionContractType` | `licitacion_contract_type` | `name` | required, non-blank |
  | `LicitacionProcedureType` | `licitacion_procedure_type` | `name` | required, non-blank |
  | `LicitacionTramitacionType` | `licitacion_tramitacion_type` | `name` | required, non-blank |

  Each keeps a **surrogate `UUID`** beside its published key, with an identifier type of its own
  under [ADR-0019](../../architecture/0019-typed-aggregate-identifiers.md) — the three type
  vocabularies are structurally identical, so only the compiler stops a procedure type reaching
  the contract-type reference.
- **The state is a code *and* a label, and both are stored.** Codes 101 and 102 both read
  *Histórico*, so the label is not a key: a store unique on it would reject a real row and a filter
  keyed on it would merge two states the source distinguishes. **`licitacion_state` therefore
  carries no constraint on its label**, and two rows holding one is the ordinary case. Code 7 was
  never observed and the set is **not closed** — an unknown code is stored as published under R33,
  so the code is not an enum and nothing seeds the table.
- **The state carries the source's own identifier; the three types have none to carry.** `estado`
  *is* the source's identifier for a state, held as `code` because that is what the source's JSON
  calls it. The three types are published as a bare label — no id in the record's `<dd>`, no type
  field anywhere in the listing endpoint — so their published `name` is what the import matches on.
  No `sourceId` column is added for a value the source is not known to publish; if one is ever
  measured, that is a column and a parse change made then.
- Both economic figures are **`Money`**, the existing value type, not `BigDecimal`.
- The withdrawal marker R13 needs is on the record, declared here so the aggregate is whole; its
  column is TASK-0005's and the reconciliation that writes it is TASK-0014's.
- **`LicitacionRepository`** — the port. `upsert` matching on `publicationId` and answering an
  `UpsertOutcome` (the identity the children attach to, and an `UpsertOperation` naming which
  branch the write took — `ADDED` or `REFRESHED`, on `UpsertCounts`' vocabulary — neither of which
  is recoverable after the write), plus `findByPublicationId`. The branch is an enum rather than a
  boolean because it is what the run's outcome counts, and a caller reading `false` would have to
  know which branch it stood for. No SQL and no Micronaut Data annotations on the interface itself.
- **One port per vocabulary** — `LicitacionStateRepository` matching on `code`, and one each for
  the three types matching on `name`. **Each has an `upsert` and nothing else**: no finder, because
  the upsert already answers the stored value carrying the identity the procedure refers to, so
  nothing needs to look one up first; and no delete, because a value no procedure references any
  more is still one the source published. The upsert is what makes an unseen value cost nothing —
  it runs inside the transaction that stores the procedure, so a state code or a type name the
  source has never published before simply creates its row rather than failing a foreign key.
- **The vocabularies must be stored before the procedure that names them**, and the ports' shape is
  what makes that natural rather than enforced: an upsert answers the value with its identity, and
  `LicitacionRepository.upsert` is documented to refuse a procedure whose state or type has none.
  Nothing in the domain checks it, because a `Licitacion` built around a freshly parsed vocabulary
  value is a perfectly good aggregate — it is the write that cannot take one.

**Out of scope:** every child of the procedure, every table (the four vocabularies' included —
their migrations are TASK-0005's), and every parse. The listing's
`importe` is **not** a field here — it is the base budget, which the record publishes properly, and
taking the listing's number for an award is the mistake
[`design/source-contract.md`](design/source-contract.md) warns about at length.

## Acceptance criteria

- A `Licitacion` round-trips every R7 field it was built with, including a null publication date and
  both halves of the state. ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #7
  per-field half, #44)
- `LicitacionId` wraps a `UUID` and is `@Nullable` on the aggregate: a `Licitacion` constructs with
  **no** id and is expected to receive one on insert, exactly as `ContratoMenor` does. Nothing
  assigns one in the domain. ([ADR-0019](../../architecture/0019-typed-aggregate-identifiers.md))
- `publicationId` is a `PublicationId` on the aggregate — its own type, distinct from the identity
  and impossible to pass where a `LicitacionId` is expected — and two `Licitacion` values carrying
  the same one are the same procedure to the repository's `upsert`. (SPEC-0008 #17)
- **Two states sharing one label are two states, and the store keys them separately.** 101 and 102
  both construct as *Histórico*, the second is not rejected for repeating the first's label, and
  nothing in the model treats the label as a key.

  Stated about the state rather than about two `Licitacion` values, because that is where it is
  true. A procedure carries no state label of its own — it reaches both halves through the one
  reference, which the component-list criterion below pins — and two procedures sharing an identity
  are one procedure however their states differ, which is what identity equality is for.
  (SPEC-0008 #44)
- An unseen state code (say `7`) constructs and stores without special-casing: nothing validates the
  code against a known set. (SPEC-0008 #44)
- **A procedure whose record published none of the three types constructs**, referring to none of
  them — the ordinary case, since a type is optional where the state is not. (SPEC-0008 #7
  per-field half)
- A type vocabulary entry holds its published name **stripped of surrounding whitespace and
  reduced no further** — no case folding, no collapsing of internal spacing, so two published
  spellings stay two entries — and refuses one that is **empty once stripped**, which would key an
  entry that is not a fact about anything. A padded name reduces to the entry already stored
  rather than keying a second one beside it. (SPEC-0008 #44)
- **A procedure whose publication identifier is not a number constructs and round-trips it**, a
  padded one reduces to the identifier already stored, and one empty once stripped is refused —
  it would collapse every procedure carrying it onto a single row. (SPEC-0008 #17, #44)
- **A `Licitacion` holds no component of its own for either half of the state, for any type name,
  or for anything R8 puts on a child.** Pinned against the record's component list, as
  `ContratoMenorTest` pins its own: a procedure holding the label instead of the reference could
  not tell 101 from 102. (SPEC-0008 #44)
- The base budget and estimated value are `Money`, and a procedure publishing neither constructs
  with both absent.
- Unit-tested with no database and no HTTP. The `upsert` half of the `publicationId` criterion is
  **not** among them: matching two readings of one publication needs a store, so it is
  TASK-0005's `UNIQUE (publication_id)` and its integration test that prove it. What is proven here
  is the aggregate's half — the natural key is a `PublicationId`, distinct from the identity.
