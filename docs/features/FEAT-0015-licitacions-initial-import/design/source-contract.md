# The licitacións source contract

What contratosdegalicia.gal actually offers for an Órgano's **licitacións**, measured against the
live site on **2026-08-20**. This is the answer [FEAT-0015](../README.md) needs before its adapter
can be written, and it is recorded here rather than in a task because several of the feature's
decisions — and **two corrections to [SPEC-0008](../../../specs/SPEC-0008-import-browse-licitacions.md)** —
rest on it.

It is the sibling of
[FEAT-0009's contratos menores contract](../../FEAT-0009-contratos-menores-initial-import/design/source-contract.md),
and the differences from it are the point: this family is retrieved by **two mechanisms**, not
one, and only the first of them is JSON.

## The two retrievals

| | Listing | Per-procedure record |
| --- | --- | --- |
| Shape | JSON (DataTables `serverSide`) | **HTML**, ISO-8859-1 |
| Path | `api/v1/organismos/{id}/licitaciones/table` | `licitacion?N={id}` |
| Covers | one entry per licitación | the whole record |
| Cost | ~11 requests per 1 000 procedures | **one request per procedure** |
| Size | a few KB per page | **median 138 KB**, mean 168 KB, max 527 KB |

The listing alone satisfies almost nothing SPEC-0008 R7 asks for — it carries no expediente, no
contract/procedure/tramitación type, no budget, no estimated value, no lote count, no
classification, no award and no bidders. **Every one of those is on the HTML record and nowhere
else**, which is why this feature parses HTML and FEAT-0009 did not.

## The listing endpoint

```text
GET https://www.contratosdegalicia.gal/api/v1/organismos/{organismo}/licitaciones/table
    ?draw=1&start=0&length=100
    &columns[0][name]=id … &columns[5][name]=modificado
    &order[0][column]=5&order[0][dir]=desc
    &idioma=gl
```

Plain `GET`, no cookies, no session, no CSRF token, no referer check. The response is
`application/json` in **UTF-8**, unlike the site's HTML pages.

```json
{
  "draw": 1,
  "recordsTotal": 1065,
  "recordsFiltered": 1065,
  "data": [
    {
      "id": 822054,
      "objeto": "Actuacións de mellora de infraestruturas de eficiencia enerxética …",
      "importe": 3378552.09,
      "estado": 8,
      "estadoDesc": "Formalizado",
      "publicado": "08-03-2024",
      "modificado": "20-08-2026"
    }
  ]
}
```

### Fields

| Field | Type | Notes |
| --- | --- | --- |
| `id` | integer | The publication identifier. Stable and **totally ordered**, which is what SPEC-0006 R4's tie-break and this feature's resumption cursor both need — and, per *One id space* below, totally ordered **across both families**, not merely within this one. Observed to increase with publication date (2013 → ~18 700, 2026 → ~829 000), but nothing here depends on that. |
| `publicado` | string | Publication date, **`DD-MM-YYYY`**. Text, so it needs interpreting for R22's year scoping and R25's visibility test. |
| `modificado` | string | **When the entry was last updated**, same format. This is the field contratos menores lacks entirely and the one SPEC-0008 R11's incremental promise rests on. |
| `objeto` | string | Free text, no length cap. |
| `importe` | number | JSON number. **This is the base budget (orzamento base, VAT-inclusive)**, not the awarded amount — see the warning below. |
| `estado` | integer | The state **code**. |
| `estadoDesc` | string | The state label. **Not a key** — see below. |

### `importe` in the listing is the budget, not the award

Measured on procedure 822054: the listing's `importe` is `3378552.09`, which is the record's
**`Orzamento base de licitación: 3.378.552,09 con IVE`**. Its two lotes were awarded
`3.052.743,72` and `206.996,66` — a sum of `3.259.740,38`, which appears nowhere in the listing.

This matters because SPEC-0008 R24 makes the row's amount an **awarded** amount wherever there is
one, and every total sums awards only. **A feature that took the listing's `importe` for an
awarded amount would populate every cross-family total with budgets** — silently, and in a way
that looks right. The awarded amounts come from the record's resolution table and from nowhere
else, which is one more reason the per-procedure retrieval is not optional.

### The state vocabulary is published, and `estadoDesc` is not a key

Ten distinct codes observed over ~2 000 listing rows across five Órganos:

| Code | `estadoDesc` | n |
| --- | --- | --- |
| 1 | En curso | 26 |
| 2 | Pendente de adxudicar | 75 |
| 3 | Adxudicado provisional | 10 |
| 4 | Adxudicado | 955 |
| 5 | Deserto | 117 |
| 6 | Anulado/Desestimado | 54 |
| 8 | Formalizado | 727 |
| 9 | Renuncia | 6 |
| 101 | Histórico | 3 |
| 102 | Histórico | 12 |

**Codes 101 and 102 share one label.** So the pair is the fact and the label alone is not: a
filter keyed on `estadoDesc` would merge two states the source distinguishes, and a `UNIQUE`
constraint on the label would reject real rows. SPEC-0008 R23 offers "the source's own" states, so
**both are stored** and the code is what the system is unique on.

Code 7 was not observed. The set is not closed and nothing should treat it as one — R33 stores the
state as published precisely so an unseen code costs nothing.

### The listing returns the whole history, and date parameters are silently ignored

Confirmed as SPEC-0008 records it. `recordsTotal == recordsFiltered == 1065` with no filter, and a
request carrying `datestart=2020-01-01&dateend=2020-03-01` returns **`200` with all 1 065 entries**,
its first row published `21-04-2016`. The parameters are neither honoured nor rejected.

### Ordering works — but only with the full DataTables payload

This is the correction that matters most, because
[FEAT-0009's contract](../../FEAT-0009-contratos-menores-initial-import/design/source-contract.md)
records that "ordering parameters were not made to work" and concluded the source cannot sort.
**That conclusion was drawn from an incomplete request.**

| Request | Result |
| --- | --- |
| `order[0][column]=5&order[0][dir]=desc` alone | **`500`** |
| the same, plus every `columns[i][name]` | **`200`, correctly ordered** |

The server resolves the order column **by name**, so it needs the `columns[i][name]` array
DataTables always sends. Omit it and the sort target is unresolvable and the request faults. All
six columns are `orderable` and all six were verified to sort in both directions:

```text
order=modificado desc → id=822054 pub=08-03-2024 mod=20-08-2026
                        id=828959 pub=20-08-2026 mod=20-08-2026
order=modificado asc  → id=18747  pub=13-05-2013 mod=16-05-2013
```

That first row is exactly the case SPEC-0008 R11 promises to catch: a procedure published in 2024
and modified today, which no date-window walk would reach. **So R11 is buildable**, and the
incremental feature that will build it inherits this finding rather than rediscovering it.

The adapter must send the whole payload for **every** request, ordered or not, and must not treat
the short form as an equivalent shorthand.

### Measured limits

| Request | Result |
| --- | --- |
| `length=100` | `200`, returns 100 rows |
| `length=200`, `500`, `1000`, `2000` | **`500`** |
| `start=1000` in a 1 065-row history | `200`, 65 rows |
| `start=1060` | `200`, 5 rows |
| `estados=8` | `200`, `recordsFiltered: 1006` |
| `estados=1,2` | `200`, `recordsFiltered: 6` |

**A page is at most 100 rows**, offset paging works at depth, and the `estados` filter takes a
comma-joined list of codes. As with contratos menores, an over-wide `length` fails with a bare
`500` and no machine-readable body, so the adapter stays inside the limit by construction rather
than discovering it from the error.

## The per-procedure record

`GET https://www.contratosdegalicia.gal/licitacion?N={id}` answers `200 text/html; charset=ISO-8859-1`.
There is **no JSON equivalent**: `api/v1/licitaciones/{id}`, `…/licitacion/{id}`,
`…/concursos/{id}`, `…/concurso/{id}` and `…/licitaciones/{id}/detalle` all answer `404`.

The same URL is SPEC-0008 R20's per-row route to the official source, and the record publishes it
of itself, so it is **derivable from the stored `id`** with nothing extra captured at import time.

### One id space, shared with contratos menores

The `licitacion?N={id}` template is the one contratos menores already use for their own deep links
(`ContratosMenoresPublicationConfiguration`, `"%s/licitacion?N=%d"`), which raises a question
SPEC-0006 R4 depends on: its name tie-break is *"the higher contract identifier"*, and with a second
family feeding one catalogue an identifier shared between families would make that tie-break
ambiguous. The two families' observed id ranges **do overlap** — contratos menores run ~289 000 to
~2 001 000 and licitacións ~18 700 to ~829 000 — so the question is not idle.

Measured, it resolves cleanly. The same endpoint serves both families from **one publication id
space**, and each identifier returns its own family's record:

| Request | Answers |
| --- | --- |
| `licitacion?N=822054` | the **licitación** — 267 KB, `Nº lotes`, `Tipo de procedemento`, a bidder list |
| `licitacion?N=2001090` | the **contrato menor** — 78 KB, no lotes field |
| the same two with `&S=CM` appended | unchanged — the parameter does not select a family |

So an identifier denotes one publication, whichever family it belongs to, and no two publications
share one. **SPEC-0006 R4's tie-break therefore stays total across families** and `NomeRank` needs no
family discriminator. Recorded because the property is load-bearing and the overlapping ranges make
the opposite conclusion the natural guess.

**Everything is in the one response.** The lotes list, the bidder list and the resolution table
are pre-rendered into Bootstrap modals in the HTML — `mostrarInfoLotes()` and its siblings only
reveal markup that already arrived. No AJAX, no second request, and SPEC-0008's "nothing needs a
third request" holds.

### What it carries

Labelled `<dt>`/`<dd>` pairs, verified across several procedures:

| Label | Example | SPEC-0008 |
| --- | --- | --- |
| `Obxecto` | Actuacións de mellora de infraestruturas… | R7 object |
| `Expediente` | `2024/001` | R7 reference |
| `Tipo de contrato` | Obras | R7 |
| `Tipo de procedemento` | Abertos | R7 |
| `Tipo de tramitación` | Ordinaria | R7 |
| `Nº lotes` | `2` | R7 |
| `Orzamento base de licitación` | `3.378.552,09 con IVE` | R7 base budget |
| `Valor estimado` | `2.792.191,81 sen IVE` | R7 estimated value |
| `Estado do procedemento` | Formalizado | R7 state |

The two economic figures **label their own VAT treatment in the published text** (`con IVE` /
`sen IVE`), which is worth knowing: R7 requires the labelling, and it is a fact the source states
rather than one the system asserts.

### Tables

The record's data tables, each verified on procedure 822054:

| Table | Columns |
| --- | --- |
| Resolution (the award) | Lote, Part., Resolución, **Adxudicatario**, Importe, Data difusión, Prazo de execución, Recurso/Prazo |
| CPV | código CPV, **Lote**, Data difusión |
| NUT | NUT, **Lote**, Data difusión |
| Relación de lotes | Lote, Descrición, Valor estimado |
| Bidders (`tr.filaLic_*`) | Lote, **NIF**, Nome |

```text
Lote | Part. | Resolución | Adxudicatario                       | Importe          | Prazo
1    | 10    | Adxudicado | XESTION AMBIENTAL DE CONTRATAS, S.L.| 3.052.743,72 EUR | 12 meses
2    | 7     | Adxudicado | ESQUEIRO, SL                        |   206.996,66 EUR | 2 meses 7 días
```

So SPEC-0008 R8's "one place per thing awarded" is exactly how the source publishes it: **the award
table is keyed by lote**, and a procedure with no lotes has one row whose lote cell is empty.

**`Part.` is the count of bidders for that lote** (10 and 7 above), which is a free cross-check on
the parsed bidder list — a parse that produced a different count has failed and should say so
rather than store a short list.

#### Two places the per-lote model is looser than SPEC-0008 assumes

- **CPV and NUT carry a lote column, but it is `_` on this procedure** — which has two lotes and
  two separate awards. So classification is **not reliably per lote even where lotes exist**, and
  a model that requires a lote for every CPV row cannot store what the source publishes. The
  lote reference must be optional on a classification row, with `_` read as *the procedure as a
  whole*.
- **`Relación de lotes` was empty on the same procedure** — header row only, no descriptions, no
  estimated values — while `Nº lotes` said `2` and the award table named both. So a lote's
  existence is established by the **award table**, and its description and estimated value are
  optional extras that are frequently absent. A parse that discovered lotes only from the lotes
  table would have found none here and lost both awards.

### Lote frequency and cost

Measured over **100 procedures** across five Órganos (SERGAS included):

| Nº lotes | Procedures |
| --- | --- |
| none | 85 |
| 2 | 3 |
| 3 | 4 |
| 4 | 4 |
| 6 | 2 |
| 7 | 1 |
| 8 | 1 |

**15 of 100 had lotes**, against the *4 of 100* SPEC-0008 records from its own sample. The
direction is unchanged and the spec's design decision stands — lotes are the minority and the
lotless procedure is the plain case — but they are common enough that they are not an afterthought,
and this sample is the one to size against. (The samples differ in composition: the spec's took
three Órganos, this one is weighted toward the largest publishers, which run more multi-lote
procedures.)

**Record sizes**, same 100 procedures: min 88 KB, **median 138 KB**, mean 168 KB, max 527 KB.

For SERGAS (16 798 licitacións, confirmed against the live listing) an initial import is therefore
about **2.8 GB over 16 798 requests** — roughly **4.7 hours at one request per second**. That
figure is what SPEC-0008 R29's yielding exists for, and it is recorded here so the feature that
builds yielding argues from a measurement rather than an estimate.

## UTE identifiers: the finding that contradicts R17

SPEC-0008 R17 requires a UTE to be stored "as an operador, identified by its **own published
fiscal identifier** under SPEC-0006 R3", and its *What the source publishes* section states that
"a bidder row carries one fiscal identifier and one name for the UTE" and that such an identifier
"begins with `U`".

**A UTE does have a fiscal identifier of its own — but the source usually does not publish it.**
Measured over **240 procedures across eight Órganos**, yielding **41 UTE bidder rows**:

| What the NIF cell holds | Rows | Share |
| --- | --- | --- |
| `-` | 31 | 76% |
| `TEMP-00934`-style placeholder | 8 | 20% |
| A real `U…` identifier | 2 | 5% |

The two genuine ones are `U88779475` (UTE INSIDE OVIGA RIBADEO) and `U70551049` (UTE EPTISA
SERVICIOS DE INGENIERÍA — ACERARQ CORUÑA). So the `U` prefix is real and R17's mechanism is right
in principle; what fails is the **premise that the identifier is there to read**.

The members are unaffected: a UTE row publishes each member's own NIF and name inline, and those
are ordinary identifiers.

```html
<tr class="filaLic_1_1 filasLicitadores hidden">
  <td>1</td>
  <td>-</td>
  <td>
    <ul class='list-unstyled'>
      <li>UTE PRACE-TABOADA RAMOS</li>
      <ul>
        <li>A70319678 - PRACE SERVICIOS Y OBRAS SA</li>
        <li>B94181807 - CONSTRUCCIONES Y OBRAS TABOADA RAMOS SLU</li>
      </ul>
    </ul>
  </td>
</tr>
```

### Why this is a correctness problem and not a gap

[SPEC-0006](../../../specs/SPEC-0006-operadores-economicos.md) R5 defines an identifier as
unusable when it is "absent, or empty once surrounding whitespace is ignored", and states that
"nothing beyond the emptiness test is validated". `FiscalIdentifier.of` implements exactly that.

`-` is not empty. Neither is `TEMP-00934`. So under the rules as written:

- **every `-` UTE in the system collapses into one operador** whose fiscal identifier is `-`,
  carrying the bids and awards of dozens of unrelated consortia and displayed under whichever
  name was published last. On the measured share that is 76% of all UTEs;
- **every `TEMP-` placeholder becomes a catalogued operador**, which is the "invented or
  placeholder one" SPEC-0006 R5 exists to forbid. The six distinct `TEMP-` values in the sample
  did not repeat across procedures, but they are sequential within one procedure (`TEMP-00934`
  … `TEMP-00939`), so they read as per-publication allocations and **nothing observed rules out
  reuse across procedures** — which would merge unrelated consortia outright.

Both outcomes are silent. Nothing fails, a catalogue is produced, and it is wrong in a way that
only shows up as an operador with an implausible history.

**This is a SPEC-0006 change, not a decision this feature may take.** The narrowest fix is to
widen R5's unusable test from *empty* to *empty or a published placeholder*, with `-` and the
`TEMP-` form named — after which R16's existing rule carries the whole load with nothing else
altered: the UTE yields no operador, the licitación stays stored and visible, its members and
every other bidder are unaffected, and the 5% of UTEs that publish a real `U…` identifier are
catalogued exactly as R17 intends. [FEAT-0015](../README.md) names it as a prerequisite and does
not work around it.

## Other observations

- **Not every procedure has bidders.** 44 of the first 70 procedures sampled carried a bidder
  table; the rest are open, pending, deserted or withdrawn. That is R25's point — a procedure with
  no award and no bidders is a complete record of an undecided procedure, not an incomplete one.
- **Amounts are formatted, not numeric**, unlike the contratos menores listing: `3.052.743,72 EUR`
  in the record's HTML, Galician thousands/decimal convention. They are parsed at the adapter and
  stored as numbers under R33. The listing's `importe` *is* a JSON number.
- **Dates come in two forms**: `DD-MM-YYYY` in the listing, `DD-MM-YYYY HH:MM:SS` in the record.
- **Documents, mesas, appeals and the event history are all present** on the record and all out of
  scope under SPEC-0008. They are the bulk of the 138 KB, which is worth knowing: the parse reads a
  small part of a large page.
- **The record is ISO-8859-1** while the listing is UTF-8. Decoding the record as UTF-8 corrupts
  every accented name and object — and Galician names are full of them.

## Caveats

- **The `TEMP-` reuse question is open.** Six distinct values were observed and none repeated, but
  the sample is small and the values look allocated per publication. Nothing should depend on
  their uniqueness in either direction; treating them as unusable makes the question moot, which
  is another reason to prefer that fix.
- **Sizes and lote frequency were measured over 100 procedures** weighted toward large publishers.
  They are sound for costing an initial import of a large Órgano, which is what they are used for
  here, and should not be read as a distribution over the whole catalogue.
- **The `estado` code set is not closed.** Code 7 was not observed and higher `1xx` codes may
  exist. R33's store-as-published is what keeps an unseen code from being a failure.
- **`recordsTotal` is a live figure**, as it is for contratos menores: it moves while a multi-hour
  import runs, so it is a completeness test re-read on every response rather than a fixed target.
- **Whether the listing's offset paging is stable under concurrent publication was not measured.**
  Ordered by `id` ascending, new procedures take higher ids and append at the end, so a walk's
  earlier pages should not shift; the feature nevertheless overlaps its resumption by a page
  rather than assuming it, on the same reasoning FEAT-0009 applied to its window boundary.
