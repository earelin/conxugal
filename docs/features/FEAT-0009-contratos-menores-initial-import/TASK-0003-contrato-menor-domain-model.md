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
declares `OperadorId`, the type the optional operador reference below carries. FEAT-0010's base
lands before this aggregate exists so that the foreign key is **created with** `contrato_menor`
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
  | `publicationId` | `long` | The source's own `id`. **The stable identity across imports** (R11) and what the uniqueness of R12 is enforced on |
  | `organoId` | `UUID` | The awarding Órgano's **UUID**, never its source key |
  | `publicationDate` | `LocalDate`, nullable | Interpreted at the adapter from the source's `DD-MM-YYYY` text. **One field, not a pair** — the published text is not retained |
  | `objeto` | `String`, nullable | As published, including the source's own 60-character truncation |
  | `amount` | `BigDecimal`, nullable | Published as a JSON **number**, VAT-inclusive |
  | `duration` | `String`, nullable | As published, free text |
  | `operadorEconomico` | `OperadorEconomico`, nullable | **A foreign-key association** to the operador catalogue, not a copy of its data |

- **The awardee is a relationship, not columns on this row.** The property is
  `@Relation(Relation.Kind.MANY_TO_ONE)` mapped by `@MappedProperty("operador_id")`, so
  `contrato_menor` holds one foreign key and the awardee's name and fiscal identifier live
  **once**, on the `operador` row that
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
> - **SPEC-0006 #5** requires each contract row to display the identifier *exactly as published
>   for that contract*, padding and casing included. Every row of an operador's history will now
>   show the one spelling the operador row holds — the variance that criterion exists to expose
>   is no longer stored anywhere, and cannot be recovered without re-importing.
> - **SPEC-0005 #40** (*every value displayed matches what the source published*) holds for the
>   contract's own values and no longer for its awardee.
>
> A fourth consequence has no spec to break because no feature owns it yet: with per-contract
> spellings gone, an operador's display name can no longer be **re-derived** from stored data if
> its winning contract is later withdrawn — the stored rank becomes the only memory of where the
> spelling came from. SPEC-0006 R7's lifecycle feature inherits that.
>
> **The specs have been amended to match**: SPEC-0005 R7 now holds the awardee on the operador
> and names what that costs, R27 lists the awardee as one of its two exceptions, and #11, #21,
> #39 and #40 follow; SPEC-0006 #5, #25 and R13 now describe rows showing the operador's
> spelling. This task implements the amended rule, not a divergence from it.

- **Only identity is required**: the publication identifier and the awarding Órgano. Every other
  field — the publication date included, since it can fail to parse — is nullable, and null means
  *the source published nothing there*: a `NOT NULL` on any of them would reject a real award
  over a field the source left blank. This is the same rule #42 states for the amount and the
  date, applied to the whole row, and it is what
  [TASK-0004](TASK-0004-contratos-menores-store.md)'s columns mirror. A field absent **systematically** is a different matter — that is the adapter judging
  the response unusable ([TASK-0005](TASK-0005-source-port-and-adapter.md)), not a row stored
  half-empty.

- **No published value is altered on the way in.** No trimming, no case folding, no rounding, no
  inferring — R27 forbids it. (The awardee's padding now survives on the `operador` row rather
  than on the contract, which is what the note above records.)
- **The publication date is stored interpreted, and only interpreted.** It arrives as
  `DD-MM-YYYY` text, is parsed at the adapter, and one nullable `LocalDate` holds the result;
  the published string is not kept.

  **R27 now names this as one of its two exceptions** — the interpretation replaces the published
  string rather than accompanying it — so a date that cannot be interpreted leaves the field null
  and that contract shows no date rather than the text the source published. What survives is the
  half that matters: the contract is **stored, never rejected** (#42), and a null date is exactly
  what R19's *undated* selection reads, so no contract becomes unreachable.
- The amount needs no such consideration: the source publishes it as a JSON **number**, so there
  is no published spelling to lose and one nullable numeric column is both what was published and
  what R19 sorts on.
- **The association is declared here and never resolved here.**
  [FEAT-0010 TASK-0004](../FEAT-0010-operadores-economicos-base/TASK-0004-derivation-during-import.md)
  is the only thing that ever fills it, and until it lands every contract stores a null — which
  means **no awardee is stored at all in the meantime**, since the contract keeps none of its
  own. That is a consequence of normalising, and it is the reason FEAT-0010's derivation should
  not lag far behind the first import.
- **The awarding Órgano is referenced by a raw `UUID`, not an `OrganoId`.** ADR-0019 converts a
  shipped aggregate only when a feature has reason to touch its identity, and typing the
  catalogue is not this feature's work — so the reference stays untyped until it is, and the
  asymmetry is deliberate rather than an oversight. `publicationId` stays a `long`: it is the
  source's natural key, not an identity this system assigns.
- **No column addresses the publication at the source.** R16's per-row link is
  `licitacion?N={publicationId}` — derivable from a field the row already carries.
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
- The aggregate carries a `ContratoMenorId` identity distinct from `publicationId`, so the
  source's identifier is what matches a contract across imports while identity is the system's
  own — and no method taking a `ContratoMenorId` can be handed another aggregate's identifier,
  which is a compile error rather than a missing row.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #11 storage half, #16
  storage half)
- The awardee is reachable as **one association** — `contrato.operadorEconomico()` yields the
  operador row, and the aggregate holds no awardee name or fiscal identifier of its own. A
  contract whose award has no usable identifier yields **null** there and is still a valid
  aggregate. ([SPEC-0006](../../specs/SPEC-0006-operadores-economicos.md) #8, no-operador half)
- Constructing a contract from published values preserves the **text** values byte-for-byte —
  the object comes back at its published length and the duration as published.
  (SPEC-0005 #40 storage half, **except the publication date**, stored interpreted, **and the
  awardee**, which the operador row now holds)
- A publication date that cannot be interpreted yields a contract with a **null**
  `publicationDate`; the same for an absent amount, and for any other value the source left
  blank. Nothing is rejected. (SPEC-0005 #42, stored-not-rejected half; its *displayed as
  published* half no longer holds for the date)
- The port exposes batch upsert with added/refreshed counts and a per-Órgano count, and
  exposes **no** operation that deletes a stored contract. (SPEC-0005 #17)
- Unit-tested without a database or HTTP server.
