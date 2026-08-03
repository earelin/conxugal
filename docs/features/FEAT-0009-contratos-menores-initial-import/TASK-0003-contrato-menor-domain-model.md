---
feat: FEAT-0009
domain: backend
adrs: [0002, 0008, 0019]
status: todo
depends_on: []
---

# `ContratoMenor` domain model + repository port

The aggregate a stored contrato menor is, and the port that stores it. Domain only — no
JDBC, SQL, HTTP or transport, which is [TASK-0004](TASK-0004-contratos-menores-store.md)'s.
Governed by [ADR-0002](../../architecture/0002-hexagonal-architecture.md); the aggregate
carries its own mapping annotations for a 1:1 single-table mapping per
[ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md).

Every field below is a value the source actually publishes, measured in
[`design/source-contract.md`](design/source-contract.md).

**Prerequisite outside this feature:**
[FEAT-0010 TASK-0002](../FEAT-0010-operadores-economicos-base/TASK-0002-operador-domain-model.md)
declares `OperadorEconomico`, the aggregate the optional reference below associates to, and its
`OperadorId` identity. FEAT-0010's base lands before this aggregate exists so that the foreign
key is **created with** `contrato_menor`
rather than added to a table of millions later.

## Scope
- **`ContratoMenorId`, a record wrapping a `UUID`**, declared beside the aggregate, plus the
  `AttributeConverter` that maps it to the `uuid` column — the pattern
  [ADR-0019](../../architecture/0019-typed-aggregate-identifiers.md) decides. The identifier
  property stays `@Id @GeneratedValue @Nullable`, so the database still assigns it and an
  aggregate is still built with a null id.
  - **This task proves the mechanism before anything is built on it.** ADR-0019 records that
    Micronaut Data returning a `@GeneratedValue` key *through* a converter is undemonstrated in
    its documentation. Establish it here — insert an aggregate with a null id and assert the
    returned instance carries the generated one — and if it does not hold, stop and raise it:
    the fallback is application-assigned identifiers, which is a different decision needing an
    ADR that supersedes 0019, not a workaround chosen in passing.
- The `ContratoMenor` aggregate:

  | Field | Type | Notes |
  | --- | --- | --- |
  | `id` | `ContratoMenorId` | System-assigned identity, `null` only until the database assigns it |
  | `sourceId` | `long` | The source's own `id`. **The stable identity across imports** (R11) and what the uniqueness of R12 is enforced on |
  | `organoId` | `UUID` | The awarding Órgano's **UUID**, never its source key |
  | `publicationDate` | `LocalDate`, nullable | Interpreted at the adapter from the source's `DD-MM-YYYY` text. **One field, not a pair** — the published text is not retained |
  | `obxecto` | `String`, nullable | As published, at whatever length the source publishes it — no cap of our own |
  | `amount` | `Money`, nullable | Published as a JSON **number**, VAT-inclusive — see below |
  | `duration` | `String`, nullable | As published, free text, **capped at 64 characters** by the adapter before it reaches the aggregate |
  | `operadorEconomico` | `OperadorEconomico`, nullable | **A foreign-key association** to the operador catalogue, not a copy of its data |

- **The awardee is a relationship, not columns on this row.** The property is
  `@Relation(Relation.Kind.MANY_TO_ONE)` mapped by `@MappedProperty("operador_economico_id")`, so
  `contrato_menor` holds one foreign key and the awardee's name and fiscal identifier live
  **once**, on the `operador_economico` row that
  [FEAT-0010](../FEAT-0010-operadores-economicos-base/README.md) owns. **The schema is
  normalised**: no awardee value is duplicated per contract, and the millions of contracts one
  large Órgano publishes carry a UUID each instead of two padded strings each.
- It is **nullable by requirement**: SPEC-0006 R5 stores an award whose identifier is unusable
  under *no* operador rather than an invented one, and
  [ADR-0018](../../architecture/0018-operadores-as-a-stored-projection.md) keeps the link
  nullable for exactly that branch.

> **Three spec obligations do not survive this, and they are named rather than discovered.**
> Normalising the awardee onto the operador means a contract no longer stores what **it**
> published, only which operador that resolved to.
>
> - **SPEC-0005 R7** requires each stored contrato menor to carry *its awardee's name and fiscal
>   identifier*; it now carries a reference instead.
> - **SPEC-0006 #5** required each contract row to display the identifier *exactly as published
>   for that contract*. Every row of an operador's history now shows that operador's **one
>   canonical fiscal identifier** (SPEC-0006 R3, trimmed and upper-cased) and the **one name**
>   R4 selects — the per-contract variance that criterion existed to expose is not stored, and
>   cannot be recovered without re-importing.
> - **SPEC-0005 #40** (*every value displayed matches what the source published*) holds for the
>   contract's own values and no longer for its awardee.
>
> A fourth consequence followed and has since been answered: with per-contract names gone, an
> operador's name could no longer be **re-derived** from stored data if its winning contract were
> withdrawn. **SPEC-0006 R15 now retains every name an operador has borne**, each with the rank it
> was last seen at, so the fallback is a choice among stored rows. The identifier never had the
> problem, R3 holding it canonical and reached identically from every contract.
>
> **The specs have been amended to match**: SPEC-0005 R7 now holds the awardee on the operador
> and names what that costs, R27 lists the awardee among its exceptions, and #11, #21,
> #39 and #40 follow; SPEC-0006 #5, #25 and R13 now describe rows showing the operador's name and
> canonical identifier. This task implements the amended rule, not a divergence from it.

- **Only identity is required**: the source identifier and the awarding Órgano. Every other
  field — the publication date included, since it can fail to parse — is nullable, and null means
  *the source published nothing there*: a `NOT NULL` on any of them would reject a real award
  over a field the source left blank. This is the same rule #42 states for the amount and the
  date, applied to the whole row, and it is what
  [TASK-0004](TASK-0004-contratos-menores-store.md)'s columns mirror. A field absent
  **systematically** is a different matter — that is the adapter judging the response unusable
  ([TASK-0005](TASK-0005-source-port-and-adapter.md)), not a row stored half-empty.

- **No published value is altered on the way in, beyond the whitespace R27 does not count as
  published.** Text values arrive already trimmed of leading and trailing whitespace — the adapter
  does that at the boundary ([TASK-0005](TASK-0005-source-port-and-adapter.md)), so this aggregate
  stores what it is handed and trims nothing itself. No case folding, no collapsing of internal
  spacing, no rounding, no inferring: R27 forbids all of those, and permits only the padding the
  source adds to serialise its fixed-width fields. (The awardee now lives on the
  `operador_economico` row rather than on the contract, which is what the note above records.)
- **A text value that was only whitespace is stored as null**, not as an empty string — it
  published nothing, and null is already this aggregate's word for that. One absent-value case,
  not two.
- **The publication date is stored interpreted, and only interpreted.** It arrives as
  `DD-MM-YYYY` text, is parsed at the adapter, and one nullable `LocalDate` holds the result;
  the published string is not kept.

  **R27 now names this among its exceptions** — the interpretation replaces the published
  string rather than accompanying it — so a date that cannot be interpreted leaves the field null
  and that contract shows no date rather than the text the source published. What survives is the
  half that matters: the contract is **stored, never rejected** (#42), and a null date is exactly
  what R19's *undated* selection reads, so no contract becomes unreachable.
- **The amount is a `Money`, not a `BigDecimal`.** A record wrapping a `BigDecimal`, declared in
  the domain beside the other shared value types, with an `AttributeConverter` onto the
  unchanged `NUMERIC` column — the same mechanism
  [ADR-0019](../../architecture/0019-typed-aggregate-identifiers.md) uses for identifiers. It
  exists so that a contract's amount cannot be added to a count, a page number or a year by
  accident, and so that the one place amounts are summed — SPEC-0006 R9's per-family totals, and
  the browsing feature's — sums a type that knows it is money.
  - **No currency column.** Every figure this source publishes is in euros and the source states
    no currency; recording one per row would be storing a fact nobody published, and the system
    holds no second currency to distinguish it from. `Money` documents that it is euros; if a
    second currency ever arrives it becomes a field, and that is a change to make when it does.
  - **No rounding and no rescaling on the way in.** The published figure is kept exactly as the
    source gave it, R27's rule unchanged; `Money` fixes how amounts are *compared and added*
    (exact decimal arithmetic, never binary floating point), not what is stored.
  - It is **VAT-inclusive**, which R7 requires to be *labelled* wherever it or a total derived
    from it is shown — a display obligation, and so the browsing feature's, not a second field
    here.
  - The amount needs no as-published pair, unlike the date: the source publishes it as a JSON
    **number**, so there is no published spelling to lose.
- **The association is declared here and never resolved here.** [FEAT-0010
  TASK-0004](../FEAT-0010-operadores-economicos-base/TASK-0004-derivation-during-import.md) is the
  only thing that ever fills it, and until it lands every contract stores a null — which means **no
  awardee is stored at all in the meantime**, since the contract keeps none of its own. That is a
  consequence of normalising, and it is the reason FEAT-0010's derivation should not lag far behind
  the first import.
- **The awarding Órgano is referenced by a raw `UUID`, not an `OrganoId`.** ADR-0019 converts a
  shipped aggregate only when a feature has reason to touch its identity, and typing the
  catalogue is not this feature's work — so the reference stays untyped until it is, and the
  asymmetry is deliberate rather than an oversight. `sourceId` stays a `long`: it is the
  source's natural key, not an identity this system assigns.
- **No column addresses the publication at the source.** R16's per-row link is
  `licitacion?N={sourceId}` — derivable from a field the row already carries.
- `ContratoMenorRepository` port in `domain`:
  - a **batch upsert** taking the contracts of one page and reporting **how many were added
    and how many refreshed** — the counts R20's outcome states, which a fire-and-forget upsert
    cannot produce afterwards;
  - `countByOrganoId(UUID)` — what the walk tests against the source's `recordsTotal`;
  - **no delete of any kind.** R12 makes absence meaningless, and R13's explicit removal
    belongs to the later curation feature. A port with no delete is what stops one being
    written by accident.
- An uninterpretable date leaves `publicationDate` null and an absent or uninterpretable amount
  leaves `amount` null; neither is a reason to reject the contract, and the aggregate's
  invariants must permit both.
- **What the aggregate stores is the *storage* half of R7 and R11's identity** — the display
  obligations on every one of these values, and the VAT-inclusive labelling, are the browsing
  feature's.

## Acceptance criteria
- The aggregate carries a `ContratoMenorId` identity distinct from `sourceId`, so the
  source's identifier is what matches a contract across imports while identity is the system's
  own — and no method taking a `ContratoMenorId` can be handed another aggregate's identifier,
  which is a compile error rather than a missing row.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #11 storage half, #16
  storage half)
- The awardee is reachable as **one association** — `contrato.operadorEconomico()` yields the
  operador row, and the aggregate holds no awardee name or fiscal identifier of its own. A
  contract whose award has no usable identifier yields **null** there and is still a valid
  aggregate. ([SPEC-0006](../../specs/SPEC-0006-operadores-economicos.md) #8, no-operador half)
- Constructing a contract from published values preserves the **text** values byte-for-byte
  within their trimmed bounds — `obxecto` comes back at its published length, however long that
  is, and the duration exactly as the adapter handed it over. Neither the aggregate nor any
  construction path truncates, folds case, or collapses internal spacing; a value handed in with
  internal runs of spaces keeps them, and a duration arrives already capped rather than being
  capped here.
  (SPEC-0005 #40 storage half, **except the publication date**, stored interpreted, **the
  awardee**, which the operador row now holds, and **surrounding whitespace**, removed at the
  adapter)
- `Money` holds the published figure exactly — a value with more or fewer decimals than two
  round-trips unchanged, and no construction path rounds or rescales it. Two amounts add
  exactly, with no binary floating-point drift, and `Money` cannot be added to or compared with a
  plain number. (SPEC-0005 #40 storage half)
- A publication date that cannot be interpreted yields a contract with a **null**
  `publicationDate`; the same for an absent amount, and for any other value the source left
  blank. Nothing is rejected. (SPEC-0005 #42, stored-not-rejected half; its *displayed as
  published* half no longer holds for the date)
- The port exposes batch upsert with added/refreshed counts and a per-Órgano count, and
  exposes **no** operation that deletes a stored contract. (SPEC-0005 #17)
- Unit-tested without a database or HTTP server.
