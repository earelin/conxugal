---
feat: FEAT-0009
domain: backend
adrs: [0002, 0008]
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

## Scope
- The `ContratoMenor` aggregate:

  | Field | Type | Notes |
  | --- | --- | --- |
  | `id` | `UUID` | System-assigned identity, `null` only until the database assigns it |
  | `publicationId` | `long` | The source's own `id`. **The stable identity across imports** (R11) and what the uniqueness of R12 is enforced on |
  | `organoId` | `UUID` | The awarding Órgano's **UUID**, never its source key |
  | `publicationDate` | `String` | As published — `DD-MM-YYYY` text, kept verbatim |
  | `interpretedPublicationDate` | `LocalDate`, nullable | R27's reading-for-ordering; null when the text cannot be interpreted |
  | `objeto` | `String`, nullable | As published, including the source's own 60-character truncation |
  | `amount` | `BigDecimal`, nullable | Published as a JSON **number**, VAT-inclusive |
  | `duration` | `String`, nullable | As published, free text |
  | `awardeeName` | `String`, nullable | As published, **space padding and casing intact** |
  | `awardeeFiscalId` | `String`, nullable | As published, space-padded to fixed width |

- **Only identity is required**: the publication identifier, the awarding Órgano and the
  published date text. Every other field is nullable and null means *the source published
  nothing there* — R7 obliges us to store what is published, not to invent what is not, and a
  `NOT NULL` on any of them would reject a real award over a field the source left blank. This
  is the same rule #42 states for the amount and the date, applied to the whole row rather than
  to two fields, and it is what [TASK-0004](TASK-0004-contratos-menores-store.md)'s columns
  mirror. A field absent **systematically** is a different matter — that is the adapter judging
  the response unusable ([TASK-0005](TASK-0005-source-port-and-adapter.md)), not a row stored
  half-empty.

- **Nothing is normalised on the way in.** No trimming, no case folding, no rounding, no
  inferring — R27 forbids it, and [SPEC-0006](../../specs/SPEC-0006-operadores-economicos.md)
  R3's match rule exists precisely because this padding survives.
- **Two columns for the date, one for the amount**, and the asymmetry is the point: the date
  arrives as text so the interpretation must not displace the publication, while the amount
  arrives as a number so there is no published spelling for a second column to preserve.
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
- An uninterpretable date leaves `interpretedPublicationDate` null and an absent or
  uninterpretable amount leaves `amount` null; neither is a reason to reject the contract, and
  the aggregate's invariants must permit both.
- **What the aggregate stores is the *storage* half of R7 and R11's identity** — the display
  obligations on every one of these values, and the VAT-inclusive labelling, are the browsing
  feature's.

## Acceptance criteria
- The aggregate carries a UUID identity distinct from `publicationId`, so the source's
  identifier is what matches a contract across imports while identity is the system's own.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #11 storage half, #16
  storage half)
- Constructing a contract from published values preserves them byte-for-byte — a space-padded
  fiscal identifier and awardee name come back padded, the object comes back at its published
  length, and the publication date comes back as its published text. (SPEC-0005 #40 storage
  half)
- A publication date that cannot be interpreted yields a contract with a null interpreted date
  and its published text intact; the same for an absent amount, and for any other value the
  source left blank. Nothing is rejected. (SPEC-0005 #42 storage half)
- The port exposes batch upsert with added/refreshed counts and a per-Órgano count, and
  exposes **no** operation that deletes a stored contract. (SPEC-0005 #17)
- Unit-tested without a database or HTTP server.
