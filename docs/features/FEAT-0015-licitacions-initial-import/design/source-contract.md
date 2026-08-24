# The licitacións source contract

What contratosdegalicia.gal actually offers for an Órgano's **licitacións**, measured against the
live site on **2026-08-20**, with the lote-spelling and join measurements added on **2026-08-22**.
This is the answer [FEAT-0015](../README.md) needs before its adapter
can be written, and it is recorded here rather than in a task because several of the feature's
decisions — and **seven corrections to [SPEC-0008](../../../specs/SPEC-0008-import-browse-licitacions.md)
or a sibling document** — rest on it.

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

> **Re-measured 2026-08-24** against live captures of procedures 18747, 20000, 822054, 825000 and
> 828959, plus contratos menores served from the same endpoint. An earlier draft of this section
> listed nine `<dt>`/`<dd>` pairs including `Expediente` and `Estado do procedemento`. Two of those
> nine are not labelled pairs, and one of them does not exist under that name at all. This is the
> corrected shape.

**Seven** labelled `<dt>`/`<dd>` pairs, inside `<dl>` blocks under **`div.infoConcurso`**:

| Label | Example | Present on | SPEC-0008 |
| --- | --- | --- | --- |
| `Obxecto` | Actuacións de mellora de infraestruturas… | every record | R7 object |
| `Tipo de contrato` | Obras | every record | R7 |
| `Tipo de procedemento` | Abertos | every record | R7 |
| `Tipo de tramitación` | Ordinaria | every record | R7 |
| `Nº lotes` | `2` | licitacións only | R7 |
| `Orzamento base de licitación` | `3.378.552,09 con IVE` | every record | R7 base budget |
| `Valor estimado` | `2.792.191,81 sen IVE` | licitacións only | R7 estimated value |

The other two R7 values are published outside that list:

| Value | Where | Present on |
| --- | --- | --- |
| **`Referencia`** — R7's reference | `div.infoReferencia > dl > dt` | **a minority**, 3 of 10 sampled |
| **`Estado do procedemento`** — R7's state | `<p>Estado do procedemento: <em>Formalizado</em></p>` | every record |

**There is no `Expediente` label anywhere on the record.** The value SPEC-0008 R7 calls the
reference is published as `Referencia`, in a block of its own. On procedure 822054 the string
`Expediente 2024/001` does appear — but inside the *object's* free text, where it is prose rather
than a field, and mining it out would find a date or a lote number as readily as a reference.

#### Three labels repeat, and a fourth nearly does

This is why the container matters and why the label must match exactly:

| Label | Occurrences | The copies |
| --- | --- | --- |
| `Obxecto` | **3** | two empty ones in the acordo-marco and sistema-dinámico blocks |
| `Orzamento base de licitación` | **2** | restated as `Orzamento base de licitación:` under `class="detalleReestructuracion"` |
| `Valor estimado` | **2** | likewise, with a trailing colon |
| `Tipo de contrato` | 1 | but `Tipo de contrato(réxime xurídico)` sits beside it and means something else |

Scoping to `div.infoConcurso` removes every duplicate — measured across the captures, that block
holds each label exactly once and holds none of the copies. Exact equality on the trimmed label
handles the colon-suffixed restatements and the `(réxime xurídico)` near-miss. A parse doing
neither answers an empty object for any procedure whose blocks are ordered differently.

#### The VAT basis varies, and is not a per-field constant

The two economic figures **label their own VAT treatment in the published text** (`con IVE` /
`sen IVE`) — R7 requires the labelling, and it is a fact the source states rather than one the
system asserts. **It is not the same statement every time:** 822054 and 828959 publish their base
budget `con IVE`, and **18747 publishes its `sen IVE`**. A parse that recognised the marker and
discarded it — on the reasoning that a base budget is always VAT-inclusive — would store that
procedure's budget under a basis the source contradicts. The marker is read and carried.

`Valor estimado` is also published as `_` on records that state none, which is a placeholder and
not a figure.

#### The markup breaks a figure across lines

```html
<dd>
    3.378.552,09


        con IVE

</dd>
```

So a parse taking `Element.text()`, or splitting on a single space, fails on the record's own base
budget. `Valor estimado` on the same procedure is written inline (`<dd>2.792.191,81 sen IVE</dd>`),
so both layouts occur and neither can be assumed.

#### One value carries a double space, and jsoup will eat it

Procedure 822054's object contains `dentro do  programa` — a genuine double space in the published
text. `Element.text()` collapses internal whitespace runs and normalises newlines, so it silently
returns something the source did not publish, and every fixture written by hand still passes.
`wholeText()` plus an end-only trim is required, not merely preferable (SPEC-0008 #44).

#### Dates come in three widths

`DD-MM-YYYY`, `DD-MM-YYYY HH:MM` and `DD-MM-YYYY HH:MM:SS` all occur on one page — 97, 46 and 11
occurrences respectively across three captures. A formatter accepting only the widest reads none of
the others. Read with `uuuu` and a strict resolver, so a published `31-02` is refused rather than
clamped to a day nobody published.

#### The record does publish a diffusion date

`Data de difusión en Contratos Públicos de Galicia: 08-03-2024 08:01` is a labelled pair on the
record, and equals the listing's `publicado` for the same procedure. FEAT-0015 states that the
record does not carry the publication date; that is wrong, and recorded here so it is not
rediscovered. The listing remains the source of record for it, so the aggregate has one origin per
value rather than two and a precedence rule.

### An unknown identifier answers `200`, not `404`

`licitacion?N=999999999` answers **`200 text/html`** with a ~24 KB error page carrying no
`div.infoConcurso` and zero `<dt>` elements.

This is load-bearing enough to state on its own, because every adapter written against this source
so far treats a status of `400` or above as the failure signal, and **here that signal never
arrives**. Whether a record exists is a question only the parse can answer, and the structural
check is what answers it: measured over 23 fetched identifiers, all three of `div.infoConcurso`,
the state paragraph and the `Obxecto` label were present on 18 of 18 real records — licitación and
contrato menor alike — and `div.infoConcurso` was absent on all 5 error pages.

The status checks are still worth making: they catch the source being down, or the endpoint moving.
They are simply not what finds a record that is not there.

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
Lote | Part. | Resolución | Adxudicatario                       | Importe        | Prazo
1    | 10    | Adxudicado | XESTION AMBIENTAL DE CONTRATAS, S.L.| 3.052.743,72 € | 12 meses
2    | 7     | Adxudicado | ESQUEIRO, SL                        |   206.996,66 € | 2 meses 7 días
```

So SPEC-0008 R8's "one place per thing awarded" is exactly how the source publishes it: **the award
table is keyed by lote**, and a procedure with no lotes has one row standing for the procedure as a
whole.

The `Importe` column's currency mark is published as the entity `&#8364;` — a euro sign, not
the literal `EUR` an earlier draft of this document showed. jsoup decodes it to `€` whatever
the document's charset is, so the parse sees a real euro sign; a fixture that writes one as a
literal and encodes it as ISO-8859-1 does not, because ISO-8859-1 cannot represent it.

**`Part.` is the count of bidders for that lote** (10 and 7 above), which is a free cross-check on
the parsed bidder list — a parse that produced a different count has failed and should say so
rather than store a short list.

#### How each table spells a lote, and what the join costs if you believe them

Measured over **240 procedures** across ten Órganos on **2026-08-22**, counting the literal cell
value in each table's lote column. This supersedes an earlier draft of this document, which said
the award table's lotless cell is *empty*; it is not.

| Table | Procedure-wide row | Per-lote rows |
| --- | --- | --- |
| Award (resolution) | **`_`** × 189 | `1`…`7`, and **`01`, `02`, `03`, `05`** |
| Formalisation | **`_`** × 99 | `1`…`10` |
| NUT | **`_`** × 217 | `1`…`8` |
| Bidders (`tr.filaLic_*`) | **`-`** × 274 | `1`…`10` |

Three things follow, and the third was not anticipated:

- **The procedure-wide marker is not one character.** The award, formalisation and NUT tables write
  `_`; the bidder table writes `-`. A parse that hard-codes either loses the other.
- **Zero-padding varies *within* a table, not between tables.** The award table produced `1` and
  `05` in the same sample, so "the formalisation pads and the award table does not" — as an earlier
  draft of the feature had it — is wrong. Padding must be stripped wherever a lote is read.
- **A lote identifier is not always a number.** `OU0028`, `LU4001`, `LU4031` and `CO0642` were all
  observed in award-table lote cells. So a lote's identifier is **text**, and a model storing it as
  an integer would reject a real procedure.

**What believing the raw cell costs**, on the same 240 procedures — joining the award table's
`Part.` against the bidder rows counted for that lote:

| Join | Agree | Differ |
| --- | --- | --- |
| on the **raw** cell value | 63 | **95** |
| on the **normalised** key | **158** | 0 |

So the naive join fails on 60% of award rows and every failure is an artefact — `_` against `-`, or
`05` against `5`. Normalised, agreement is total. This matters more than a join usually would,
because a `Part.` mismatch is what sends a procedure to the outstanding ledger: unnormalised, the
cross-check would fail most procedures the source publishes perfectly well.

**The normalisation, therefore:** `_`, `-`, the empty string and a blank string all mean *the
procedure as a whole*; leading zeros are stripped; and what remains is compared as text.

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

## Where an awardee's fiscal identifier actually is

**The resolution table does not carry one.** Its columns are *Lote, Part., Resolución,
Adxudicatario, Importe, Data difusión, Prazo de execución, Recurso/Prazo*, and over **119 award
rows across six Órganos not one carried an identifier** — the awardee is named in text only.

**The formalisation table does carry one**, and this is the route that matters. Its columns are
*Data formalización, Lote, Contratista, Nacionalidade, Importe, Data difusión*, and the
**`Contratista` cell holds the name and the fiscal identifier together**:

```text
Data formalización | Lote | Contratista                          | Nacionalidade | Importe
28-06-2012         | 01   | EQUINSE, S.A. A41111220              | España        | 25.627,12 EUR
29-06-2012         | 02   | PLUS-MER, S.L. B36801942             | España        | 47.293,00 EUR
25-06-2012         | 05   | EQUIPAMIENTOS DEPORTIVOS, SA A30082945 | España      | 41.720,00 EUR
```

It is published **per lote**, exactly where R8 puts the award, and it carries a **UTE's own
identifier** too where the awardee is one — `UTE CARLOS GARCÍA SAORÍN-MIGUEL JIMÉNEZ MARTÍN
U86486669` on procedure 16938.

### How an award's identifier is reached, measured over 284 award rows

| Route | | Share |
| --- | --- | --- |
| **A** | the **formalisation** publishes it | **164 — 58%** |
| **B** | the **bidder list** for that lote publishes it | 19 — 7% |
| **C** | neither: only the published name is available | 101 — 36% |

**So 65% of awards publish their awardee's identifier somewhere on the record**, and no inference
is required for them. That is the correction that matters: an earlier reading of this source
recorded only the award row's silence and concluded the identifier was usually absent.

### It splits almost perfectly by state

**Award rows**, not procedures — the tables in this section and the next count different things and
are not summed against each other. A procedure with five lotes contributes five award rows here and
one procedure there.

| Estado | award rows | A: formalisation | B: bidder list | C: name only |
| --- | --- | --- | --- | --- |
| Formalizado | 172 | **164** | 1 | **7** |
| Adxudicado | 107 | 0 | 18 | **89** |
| Adxudicado provisional | 5 | 0 | 0 | 5 |

**A formalised procedure publishes its awardee's identifier for 95% of its awards through the
formalisation, and 96% counting the one its bidder list answers**, which follows from
what the states mean: *Formalizado* is the terminal state in which the contract has been signed
and the formalisation section filled in, and *Adxudicado* is the intermediate one where it has
not. Separately measured: of **112** formalised award-bearing pages inspected, **none** lacked a
recoverable identifier altogether.

### And the unresolved remainder is a historical tail

Counted in **procedures** this time, over a separate pass: of 73 *adxudicado* award-bearing
procedures, **60 had no identifier recoverable by any route** and 13 did. Their publication years:

| Year | 2008 | 2009 | 2011 | 2012 | 2026 |
| --- | --- | --- | --- | --- | --- |
| Count | 13 | 5 | 35 | 6 | **1** |

Against which the 13 that **do** resolve are published 2023 (1), 2025 (1) and 2026 (11). So the
population needing a name match is **weighted heavily toward pre-2013 records left in an
intermediate state** rather than the current flow: an initial import meets them and a routine run
largely does not. **It is a tail, not a closed set** — the single 2026 entry says a current
procedure can land in it, and will stay there until it formalises.

### The name match, and its false-merge risk

For the remainder, matching the published name against the operadores catalogue is the only route.
Measured against a name index built from bidder rows across 239 procedures: of 236 award rows, 46%
matched a bidder on their own procedure, 6% matched a unique operador elsewhere, and 48% matched
nothing — though that index held only **268 distinct names**, where production matches against the
whole catalogue.

Of those 268 names, **exactly one mapped to two identifiers** — `INDRA SOLUCIONES TECNOLOGIAS DE
LA INFORMACION S L U` against `B88016098` and `B88018098`, which differ by one digit and are
plainly the same firm mistyped at the source. Ambiguity is rare; a rule that guessed on it would
merge two suppliers, which SPEC-0006 R3 calls as damaging as splitting one.

## The awarded amount's VAT basis is unmarked, and only inferable

The budget and the estimated value label themselves in the published text (`con IVE` / `sen IVE`).
**The resolution table's `Importe` carries no such marker** — 0 of 119 rows. SPEC-0008 R18 requires
the amount supplied to SPEC-0006 to be VAT-inclusive so it is comparable with a contrato menor's, and
SPEC-0006 R9 states it as fact, so the basis matters and the source does not state it.

Inferred from ratios on **30 lotless awarded procedures** carrying all three figures:

| Ratio | Median |
| --- | --- |
| award ÷ base budget (VAT-inclusive) | **0.938** |
| award ÷ estimated value (VAT-exclusive) | 1.003 |
| awards exceeding the VAT-inclusive budget | **0 of 30** |

A VAT-exclusive award would have to sit near 0.83 × a competitive discount against the
VAT-inclusive budget — well below the 0.938 observed. **So the evidence leans VAT-inclusive**, which
is what R18 assumes. It is not conclusive: the estimated value often covers extensions and options,
so it is not simply the budget less VAT, which is why the second ratio does not corroborate as
cleanly as it should. Recorded as **consistent with R18's assumption and not a proof of it**.

## UTEs: identified by structure, and usually without a fiscal identifier

SPEC-0008 R17 requires a UTE to be stored "as an operador, identified by its **own published
fiscal identifier** under SPEC-0006 R3", and its *What the source publishes* section states that
"a bidder row carries one fiscal identifier and one name for the UTE" and that such an identifier
"begins with `U`".

**A UTE does have a fiscal identifier of its own — but the source usually does not publish it, and
the `U` prefix is not how a UTE can be recognised.** What identifies one is the **shape of the
bidder cell**: a consortium nests a second `<ul>` inside the first, listing each member's own
identifier and name.

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

### Measured over 613 bidder rows in 250 procedures across ten Órganos

| | nested `<ul>` — a consortium | flat cell — a single firm |
| --- | --- | --- |
| Rows | 35 | 578 |
| NIF cell is `-` or empty | 25 | **0** |
| NIF cell is a `TEMP-…` placeholder | 8 | **0** |
| NIF cell is a real `U…` identifier | 2 | 0 |
| NIF cell is an ordinary identifier | 0 | **578** |

Four conclusions, and the model rests on all four:

- **The structure is the test, and it is exact.** In 613 rows the nested `<ul>` never appeared on a
  single-firm bidder, and every consortium had one.
- **The name is not the test.** **7 of the 35** consortia are published under a name that does not
  begin with `UTE` — `MISTURAS-INGESAN`, `CONTACNOVA-CALLCENTER 012 2019`,
  `ARCO LABORA SERV.CONST. S.L.U. - HONDIGO S.L.`, `IAM RUMBO INSTALACIONES Y OBRAS, S.L. - APER
  SEGURIDAD…`. A prefix test would miss a fifth of them, and the `U`-prefix test SPEC-0008
  describes would miss 33 of 35.
- **A UTE usually publishes no usable identifier**: 33 of 35 carry `-` or a `TEMP-…` placeholder,
  and only 2 carry a genuine one (`U88779475` UTE INSIDE OVIGA RIBADEO, `U70551049` UTE EPTISA
  SERVICIOS DE INGENIERÍA — ACERARQ CORUÑA). So R17's mechanism is right in principle and
  unavailable in 94% of cases.
- **Members always publish real identifiers.** All **80** member entries carried an ordinary NIF —
  none was `-`, none was a placeholder. So a consortium whose own identity is unpublished is still
  composed of firms that are perfectly catalogueable.

### Why this makes the model simpler rather than harder

`-` and `TEMP-…` appear **only** inside consortium rows — zero of 578 single-firm rows carried
either. So a parser that takes the consortium branch **structurally, before resolving any
identifier**, never hands a placeholder to
[SPEC-0006](../../../specs/SPEC-0006-operadores-economicos.md) R3's identity rule at all.

That matters because R5 defines an identifier as unusable only when it is "absent, or empty once
surrounding whitespace is ignored", and `FiscalIdentifier.of` implements exactly that — so
`of("-")` returns a present value and `of("TEMP-00934")` likewise. Reached through the ordinary
bidder path, those would catalogue **one** operador holding the fiscal identifier `-`, carrying the
bids of dozens of unrelated consortia. Reached through the structural branch, they are never
offered to it.

So the SPEC-0006 R5 widening remains worth making as a guard against a row the sample did not
contain, but it is **not** what makes this family safe — the structural branch is, and it is this
feature's own to build. [FEAT-0015](../README.md) records the amendment as defensive rather than
blocking for exactly that reason.

**What a UTE without a published identifier needs instead is an identity that is not a fiscal
identifier**, and the source itself suggests the shape: SPEC-0008 R17 observes that "a UTE is
constituted for one procedure", which is what the measurements show — the consortium is a fact
about one bid, and its members are the durable entities. FEAT-0015 records it on the participation
accordingly.
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

- **The `TEMP-` reuse question is open, and is now moot.** The values look allocated per
  publication (`TEMP-00934` … `TEMP-00939` all on procedure 827145) and none was observed on two
  procedures, but the sample is small. Nothing should depend on their uniqueness in either
  direction — and nothing does, since the structural branch never treats one as an identity.
- **No single-firm bidder row carrying `-` or a placeholder was observed** — 578 of 578 carried an
  ordinary identifier. The structural branch's safety rests on that, and it is a measured negative
  over one sample rather than a rule the source states. The SPEC-0006 R5 widening is the guard for
  the row this sample did not contain, which is why FEAT-0015 still wants it.
- **The name-match recovery rate will change once the catalogue is populated**, and the 6% figure
  above should not be planned against. It was produced by a 268-name index; the production index is
  the operadores catalogue.
- **The awarded amount's VAT basis is inferred, not read.** If it turns out to be VAT-exclusive,
  every cross-family total in SPEC-0006 mixes bases silently — the exact defect that spec labels
  everything to avoid. One authoritative statement from the source, or one procedure whose
  formalisation restates the figure with a marker, would settle it.
- **Consortium detection was verified on 35 rows**, all from the same renderer. The nested `<ul>`
  is markup the source emits, not a documented contract, and a template change would break it
  silently — a parse that finds a flat cell where a consortium was published would record a bidder
  with a placeholder identifier. The `Part.` cross-check does not catch this, since the row count
  is unchanged; nothing here does.
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
