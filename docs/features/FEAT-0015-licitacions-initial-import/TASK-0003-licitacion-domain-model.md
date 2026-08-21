---
feat: FEAT-0015
domain: backend
adrs: [0008, 0019]
status: todo
depends_on: []
---

# The `Licitacion` aggregate and its repository port

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
- **`publicationId`, a `long`, is the natural key** a re-import matches on, unique in the table,
  exactly as `ContratoMenor.sourceId` is. Per
  [`design/source-contract.md`](design/source-contract.md) it is shared with contratos menores in
  **one id space**, which is what keeps SPEC-0006 R4's higher-identifier tie-break total across both
  families.
- **`Licitacion`**, carrying what R7 requires:

  | Field | Source | Note |
  | --- | --- | --- |
  | publication identifier | listing `id` | the natural key |
  | Órgano | the walk | FK |
  | publication date | **listing** `publicado` | **nullable** — an uninterpretable date stores null rather than rejecting the row |
  | last-modified date | **listing** `modificado` | what R11's incremental mode will order on |
  | state **code** | **listing** `estado` | what the row is unique on |
  | state **label** | **listing** `estadoDesc` | stored beside it, never instead of it |
  | expediente | record `Expediente` | free text |
  | object | record `Obxecto` | free text, no length cap |
  | contract type | record `Tipo de contrato` | as published |
  | procedure type | record `Tipo de procedemento` | as published |
  | tramitación type | record `Tipo de tramitación` | as published |
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
- **The state is a code *and* a label, and both are stored.** Codes 101 and 102 both read
  *Histórico*, so the label is not a key: a store unique on it would reject a real row and a filter
  keyed on it would merge two states the source distinguishes. Code 7 was never observed and the
  set is **not closed** — an unknown code is stored as published under R33, so the code is not an
  enum.
- Both economic figures are **`Money`**, the existing value type, not `BigDecimal`.
- The withdrawal marker R13 needs is on the record, declared here so the aggregate is whole; its
  column is TASK-0005's and the reconciliation that writes it is TASK-0014's.
- **`LicitacionRepository`** — the port. `upsert` matching on `publicationId`,
  `findByPublicationId`, and the reads the reconciliation needs. No SQL and no Micronaut Data
  annotations on the interface itself.

**Out of scope:** every child of the procedure, every table, and every parse. The listing's
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
- `publicationId` is a plain `long` on the aggregate, distinct from its identity, and two
  `Licitacion` values with the same `publicationId` are the same procedure to the repository's
  `upsert`. (SPEC-0008 #17)
- Two `Licitacion` values differing only in state **code** are different — 101 and 102 do not
  collapse — and the label is carried beside the code, never instead of it. (SPEC-0008 #44)
- An unseen state code (say `7`) constructs and stores without special-casing: nothing validates the
  code against a known set. (SPEC-0008 #44)
- The base budget and estimated value are `Money`, and a procedure publishing neither constructs
  with both absent.
- Unit-tested with no database and no HTTP.
