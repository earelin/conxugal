# The contratos menores source contract

What contratosdegalicia.gal actually offers for an Órgano's contratos menores, measured against
the live site on **2026-08-02** using **Órgano 242 — Axencia Turismo de Galicia**
(`consultaOrganismo.jsp?OR=242&N=242&lang=gl`). This is the answer
[FEAT-0009](../README.md) needs before its adapter can be written, and it is recorded rather
than left in a task because several of the feature's decisions rest on it.

## How the page works

The *Perfil do contratante* page renders **no contract data**. It ships one empty `<table>` per
contract family and a jQuery DataTables script per family, each in **`serverSide` mode**, which
fetches rows from a REST API. The tables and their scripts:

| Family | Table id | Script | API path |
| --- | --- | --- | --- |
| Contratos menores | `tabResultadosCM` | `js/js-spc/consultaOrgCM.min.js` | `api/v1/organismos/{id}/contratosmenores/table` |
| Licitacións | `tabResultados` | `js/js-spc/consultaOrgLicitacion.min.js` | `api/v1/organismos/{id}/licitaciones/table` |
| Encargos | — | `js/js-spc/consultaOrgEncargo.min.js` | `api/v1/organismos/{id}/encargos/table` |

**So the import needs no browser, no JavaScript engine and no HTML parsing.** It calls the same
API the page calls. The `{id}` is read off the page as `data-organismo="242"`.

## The contratos menores endpoint

```
GET https://www.contratosdegalicia.gal/api/v1/organismos/{organismo}/contratosmenores/table
    ?datestart=YYYY-MM-DD
    &dateend=YYYY-MM-DD
    &start=0
    &length=100
    &draw=1
```

Plain `GET`. **No cookies, no session, no CSRF token and no referer check** — a bare request
answers. The response is `application/json` in **UTF-8**, unlike the site's HTML pages, which
are ISO-8859-1.

```json
{
  "draw": 1,
  "recordsTotal": 14822,
  "recordsFiltered": 480,
  "data": [
    {
      "id": 2001090,
      "publicado": "05-05-2026",
      "objeto": "SERVIZOS TÉCNICOS DE ELECTRICIDADE PARA A CELEBRACIÓN DA FES",
      "importe": 3630.0,
      "nif": "33545498K           ",
      "adjudicatario": "ANGEL CABARCOS ABADIN                             ",
      "duracion": "1 mes"
    }
  ]
}
```

### Fields

| Field | Type | Notes |
| --- | --- | --- |
| `id` | integer | The publication identifier. Stable, and **totally ordered** — which is what [SPEC-0006](../../../specs/SPEC-0006-operadores-economicos.md) R4 needs for its tie-break. Older publications carry lower ids (2018 → ~289 000, 2026 → ~2 001 000). |
| `publicado` | string | Publication date, **`DD-MM-YYYY`**. Text, so it needs interpreting for R19's year scoping. |
| `objeto` | string | **Capped at 60 characters in the source itself** — see below. |
| `importe` | number | JSON number, already numeric. **VAT-inclusive** (the detail page renders it "3.630,00 con IVE"), which is what SPEC-0005 R7 requires to be labelled. |
| `nif` | string | Awardee fiscal identifier, **space-padded to fixed width** (`"33545498K           "`). This is the padding [SPEC-0006](../../../specs/SPEC-0006-operadores-economicos.md) R3's match rule exists for, observed rather than assumed. |
| `adjudicatario` | string | Awardee name, space-padded the same way. |
| `duracion` | string | Free text (`"1 mes"`). |

`recordsFiltered` is the count **within the requested window**; `recordsTotal` is described below.

### Measured limits

| Request | Result |
| --- | --- |
| Window 92 days (`2026-05-02` → `2026-08-02`) | `200` |
| Window 97 days (`2026-04-27` → `2026-08-02`) | `200` |
| Window 104 days (`2026-04-20` → `2026-08-02`) | **`500`** |
| Window 4 months, 6 months, 12 months, 24 months | **`500`** |
| `length=100` | `200`, returns 100 rows |
| `length=200`, `length=500` | **`500`** |
| `start=400` inside a 480-row window | `200` |
| Window entirely before the published history (`2013`) | `200`, `recordsFiltered: 0` |

So: **the window is bounded at 3 months** — the boundary sits between 97 and 104 days, and the
site's own UI enforces exactly three months (`moment(start).add(3, "months")`), which is the
value to build against. **A page is at most 100 rows**, offset paging works at depth, and an
out-of-range window is an ordinary empty answer rather than an error.

An over-wide window fails with a bare `500` and no machine-readable body, so it is
indistinguishable from a server fault. The adapter must not rely on the error to discover the
limit; it stays inside 3 months by construction.

### `recordsTotal` is the Órgano's whole history

`recordsTotal` is **the Órgano's total contratos menores count, independent of the window** —
14 822 for Órgano 242, identical across every window queried, including one that matched nothing.

Three consequences, all of which FEAT-0009 uses:

- **Completeness is provable.** An initial import is finished when the Órgano's stored count
  equals `recordsTotal`, rather than when a walk reaches a date someone guessed history starts at.
- **Progress is a real fraction**, not an elapsed-window count — which is what SPEC-0007 R5 wants
  to render.
- **An import can be costed before it is started**, with a single `length=1` request per Órgano.

It is a live figure, so it moves while a multi-day import runs. It is a completeness test, not a
constant.

## The per-publication address

The page reaches one publication through a hidden form (`consultaCM(codigo, idioma)` posts
`N=CM{id}&S=CM` to `licitacion`), but the same page **publishes the canonical URL itself**:

```
https://www.contratosdegalicia.gal/licitacion?N={id}
```

It answers to `GET`. So [SPEC-0005](../../../specs/SPEC-0005-import-browse-contratos-menores.md)
R16's per-row route to the original is **derivable from the stored `id`** — there is nothing
extra to capture at import time.

## What the source does not give

- **The full object.** `objeto` comes back at exactly 60 characters, cut mid-word. The detail
  page shows **the same 60 characters** — verified on two contracts (`2001090`, `2001110`) — so
  the truncation is in the source's own data, not in the API. There is nothing longer to fetch.
  This is why the import needs **no per-contract request**: one call per window-page carries
  every attribute the system will ever hold.
- **Contract type, tramitación, and the publication timestamp** exist on the detail page
  (`Tipo de contrato: Servizos`, `Data de difusión: 05-05-2026 06:01`) but **only there** — one
  further request per contract, which for the largest Órganos means millions. This confirms
  SPEC-0005's reason for excluding contract type rather than contradicting it.
- **CPV**, as SPEC-0005 already records.

## Caveats

- Measured on **one Órgano**. The limits are almost certainly global (they are enforced by the
  shared endpoint and mirrored in the shared UI script), but the field shapes of a much larger
  publisher have not been checked.
- **Ordering parameters were not made to work.** DataTables' `order[0][column]` / `order[0][dir]`
  produced `500` or `204` in every combination tried, so the adapter should rely on the default
  ordering (`publicado` ascending) and not on sorting the source. This costs nothing: the walk's
  newest-first property is a property of **which window** is requested, and within a window every
  row is paged through regardless of order.
- The 60-character cap was verified on two contracts of one Órgano; it is presented as the
  source's behaviour, not as a documented guarantee.
- `id` was observed to increase with publication date. The adapter must not depend on that — only
  on `id` being stable and totally ordered, which is all SPEC-0006 R4 asks.
