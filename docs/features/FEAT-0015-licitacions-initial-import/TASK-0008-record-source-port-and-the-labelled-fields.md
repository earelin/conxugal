---
feat: FEAT-0015
domain: backend
adrs: [0002, 0011, 0014]
status: done
depends_on: []
---

# `LicitacionRecordSource`: fetching one procedure, and its labelled fields

One procedure retrieved whole and its published scalars parsed. The expensive half of the
retrieval — **one request per stored record**, at a measured median of 138 KB — and the property
that drives this feature's cost, its resumption design and R29's deferral.

The record's tables are the next two tasks'
([TASK-0009](TASK-0009-record-parse-awards-formalisation-and-classifications.md),
[TASK-0010](TASK-0010-record-parse-bidders-and-consortium-detection.md)); this one establishes the
port, the fetch and the scalar fields, so those two have a document to read and a record
type to widen.

> **Amended 2026-08-24, against live captures.** An earlier draft of this task named *nine*
> `<dt>`/`<dd>` scalars including `Expediente` and `Estado do procedemento`. Measured, there is no
> `Expediente` label anywhere on the record, and the state is not a labelled pair. The Scope below
> is the corrected shape; `design/source-contract.md` carries the measurements.

**It depends on nothing.** The port answers a **source record**, not a `Licitacion` — the aggregate
is built by the store, and a port that answered one would put the model's shape inside the adapter.
The shipped analogue, FEAT-0009's source-port task, is likewise `depends_on: []` even though it
carries a `Money`.

## Scope

- **`LicitacionRecordSource`** — one method, one publication identifier, one source record, with a
  `LicitacionRecordUnavailableException` for a retrieval or parse that failed.
- **A `LicitacionRecordClient`** at `GET /licitacion?N={id}`, on the shared
  `@Client(id = "contratosdegalicia")` and `@ResilientClient`
  ([ADR-0014](../../architecture/0014-resilient-throttled-outbound-http-client.md)), returning
  **raw bytes**. There is no JSON equivalent: `api/v1/licitaciones/{id}` and four sibling shapes all
  answer `404`.
- **Decoded as ISO-8859-1**, which is what the response declares. Decoding it as UTF-8 corrupts
  every accented name and object, which in Galician is most of them.

  **This is not new ground.** `ContratosDeGaliciaOrganoSourceAdapter` already parses ISO-8859-1 HTML
  from this same host with jsoup, and `PortadaClient` already returns raw bytes precisely so the
  charset is decided at parse time. This adapter follows that precedent rather than inventing one.
- **The seven labelled scalars** — `Obxecto`, `Tipo de contrato`, `Tipo de procedemento`,
  `Tipo de tramitación`, `Nº lotes`, `Orzamento base de licitación` and `Valor estimado` — read
  from the `<dt>`/`<dd>` pairs **inside `div.infoConcurso`**, by label and not by position.

  **The container is load-bearing, not tidiness.** Three of those labels occur again elsewhere in
  the page: `Obxecto` twice more, empty, in the acordo-marco and sistema-dinámico blocks, and the
  two economic labels once more each, restated with a trailing colon under
  `class="detalleReestructuracion"`. A document-wide lookup answers with whichever copy the
  selector reached first. Matching the trimmed label **exactly** is what additionally keeps
  `Tipo de contrato` off `Tipo de contrato(réxime xurídico)`.
- **The reference is `Referencia`, and it is not one of them.** There is no `Expediente` label on
  the record. The value the aggregate calls `expediente` is published as `Referencia`, in its own
  `div.infoReferencia` block, and is present on a **minority** of records — three of ten sampled.
  Absent, it is null. It is **not** mined out of the object's text, which would find a date or a
  lote number as readily as a reference.
- **The state is a paragraph, not a pair.** `Estado do procedemento` is published as
  `<p>Estado do procedemento: <em>Formalizado</em></p>`, and only ever as the **label** — never a
  code.
- **Two of the seven are absent on a whole family.** `Nº lotes` and `Valor estimado` do not appear
  on a contrato menor's record, and the two families share one identifier space, so any check for
  "all seven present" would refuse a real publication. Absence is null, never a failure.
- **What the record does not carry, and who supplies it.** Not the last-modified date, and not
  the state **code** — the record publishes only the state's *label*, which for codes 101 and 102
  is ambiguous. It **does** publish `Data de difusión en Contratos Públicos de Galicia`, equal to
  the listing's `publicado`; an earlier draft of this task said it did not. It is still not read
  here, so that the aggregate has one origin per value rather than two and a precedence rule
  nobody has needed to write. Those values come from the **listing entry**
  ([TASK-0007](TASK-0007-listing-source-port-and-json-adapter.md)), and
  [TASK-0014](TASK-0014-reconciling-a-restated-procedure.md)'s `StoreLicitacion` takes both. A
  ledger retry has no listing entry, which is why
  [TASK-0002](TASK-0002-licitacions-per-organo-import-state.md)'s ledger stores those four beside
  the identifier.
- **Two parsing hazards live here**, because both pass every test written against an ASCII stub:
  - **amounts are Galician-formatted text** in the record (`3.378.552,09 con IVE`, and
    `206.996,66 €` in the award table — a euro-sign entity, not the literal `EUR` an earlier draft
    showed), where the listing's are JSON numbers. The `con IVE` / `sen IVE` suffix is the source
    **labelling its own VAT basis** and is parsed off the number rather than into it. **The basis
    varies between procedures** — 822054 publishes its base budget `con IVE` and 18747 publishes
    its `sen IVE` — so it is a value read and carried, not a constant the system may assume;
  - **the record's dates come in three widths**, not one: `DD-MM-YYYY`, `DD-MM-YYYY HH:MM` (the
    most common) and `DD-MM-YYYY HH:MM:SS`, against the listing's `DD-MM-YYYY`. A formatter
    accepting only the widest reads none of the others. None of this task's own scalars is a date —
    the record's dated fields are its tables' — but the format belongs here with the rest of the
    record's text handling, so it ships here and TASK-0009 is its first caller.
- **Text is taken as published, trimmed at the ends only.** jsoup's `Element.text()` collapses
  internal whitespace runs and normalises newlines, so a value with a double space or a line break
  comes back altered and every ASCII fixture still passes — which is why #44's *"byte-for-byte as
  published"* needs its own criterion here rather than being assumed.

  **The parse still owes the trim, but no longer carries the whole weight of it.**
  [TASK-0003](TASK-0003-licitacion-domain-model.md)'s vocabulary records and its `PublicationId`
  are natural keys, and each strips surrounding whitespace on the way in — on
  `FiscalIdentifier`'s reasoning, that a rule holding only when its caller has already stripped is
  one that silently mismatches the day a caller has not. So a padded value from this parse reduces
  to the row already stored rather than keying a second one beside it. That is a backstop, not a
  licence: the trim is specified here because #44 is about what this port answers with.
- **The parse is narrow on purpose.** Documents, mesas de contratación, appeals and the event
  history are the bulk of the 138 KB and are excluded by SPEC-0008's Scope.
- A record whose labels cannot be found, or whose response is not a record at all, raises rather
  than yielding a half-built procedure. **A partly parsed procedure is worse than none**: it stores
  as authoritative and nothing ever returns to it.

**Out of scope:** the five tables, the walk, and any decision about what to do with a failed
record — the ledger is TASK-0002's and writing to it is
[TASK-0023](TASK-0023-the-outstanding-record-ledger-in-the-walk.md)'s.

## Acceptance criteria

- A stored fixture of a real record — accented Galician text included — parses with **every**
  accent intact, and the same bytes decoded as UTF-8 are demonstrably wrong, so the charset is
  asserted rather than assumed.
  ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #7 per-field half, #44)
- All seven labelled scalars are read by label; reordering the `<dt>`/`<dd>` pairs in the fixture
  changes nothing. A page carrying the duplicate `Obxecto` and the colon-suffixed restatements of
  both economic labels still reads the procedure's own values, and `Tipo de contrato(réxime
  xurídico)` does not answer for `Tipo de contrato`. (SPEC-0008 #7 per-field half)
- The `Referencia` block is read where the record publishes one and absent where it does not, and
  the state comes from its paragraph as the published label. (SPEC-0008 #7 per-field half)
- A record carrying neither `Nº lotes` nor `Valor estimado` — which is every contrato menor served
  from the shared identifier space — is answered with those values absent, not refused.
- A `<dd>` whose text contains a double space and a line break yields the value **trimmed at the
  ends only, with internal spacing preserved**; a value empty once trimmed is absent, not an empty
  string. (SPEC-0008 #44)
- `3.378.552,09 con IVE` parses to the amount `3378552.09` with the VAT basis read as a separate
  published fact, `2.792.191,81 sen IVE` reads as the opposite basis, and `206.996,66 €` parses to
  `206996.66` with no basis at all. A naive locale-default parse would answer `3.378` and is what
  this asserts against. (SPEC-0008 #44)
- Each of the record's three date widths parses, and a time-bearing value is not silently
  truncated to a different day; an impossible date is absent rather than clamped. (SPEC-0008 #44)
- A `404`, a `500`, a connection fault and a body that is not a record each raise
  `LicitacionRecordUnavailableException`. **The last is the whole of the not-found path**: an
  unknown identifier answers `200` with a ~24 KB error page carrying no `div.infoConcurso`, so no
  status check can tell a missing procedure from a present one. (SPEC-0008 #41)
- The client binds `id = "contratosdegalicia"`, so it is configured as the same source as the
  listing and catalogue clients, and the record walk draws on the same rate budget as both. *The
  budget is shared because the resilience advice injects unqualified policy singletons, not because
  of the id — a different id would buy separate transport settings and go on sharing this budget.*
- Integration-tested against a **WireMock** source serving real captured records as ISO-8859-1
  bytes, on the `ContratosDeGaliciaContratoMenorSourceAdapterIntegrationTest` precedent — including
  the source's own error page, served under the `200` it really answers with, and a contrato menor
  served from the shared identifier space.
