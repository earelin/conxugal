---
feat: FEAT-0011
domain: frontend
adrs: [0004, 0015, 0022]
status: done
depends_on: []
---

# The paging control, in `shared/ui`

R17's control, built once for the whole system: first, previous, next, last and a jump to a chosen
page, with the entry count and the page total stated. It lives in `ui/src/shared/ui/` and **knows
nothing about contracts**, because
[SPEC-0006](../../specs/SPEC-0006-operadores-economicos.md) R11 and
[SPEC-0007](../../specs/SPEC-0007-monitor-import-runs.md) take it unchanged — and R17 says why: a
reader meets several of these lists in one session, and one of them paging differently is a defect
they experience as inconsistency rather than as design.

It has no dependency on any task in this feature and can land first. It is drawn in
[`design/paging-control.svg`](design/paging-control.svg), in its four states.

## Scope

- `Pagination` in `ui/src/shared/ui/`, taking `page`, `size`, `totalItems`, `totalPages` and an
  `onPageChange`, and emitting a page number.
- **It reads [ADR-0022](../../architecture/0022-paged-collection-contract-from-micronaut-data.md)'s
  envelope directly** — those four values, in the base they arrive in. There is **no conversion and
  no arithmetic** between the wire and the control: no `page + 1`, no `Math.ceil(total / size)`.
  That is the point of the envelope being ours, and it is what leaves SPEC-0006's and SPEC-0007's
  lists nowhere to diverge from this one.
- **The layout is one row under a hairline**: the record count on the left, the five controls on
  the right. Buttons are Mantine `variant="default"`; at the two ends they are **disabled, not
  hidden**, so the control never changes shape under a reader.
- **The two ends are controls of their own**, not reached by counting: *first* and *last* are the
  two pages a reader asks for by name — the newest and the oldest.
- **The jump takes a page number and refuses one outside `1…totalPages`** rather than emitting it.
- **Copy lives in a shared namespace** in `ui/src/shared/lib/strings.ts` — `Primeira`, `Anterior`,
  `Seguinte`, `Última`, `Páxina`, `de {n}` — because two other specs take this control. `records`
  travels as a `{ singular, plural }` `Word` and is rendered through `singularOrPlural`, the
  pattern `strings.ts` already uses for counted nouns, so no call site can take one form and
  forget the other.
- **A single page still renders the control**, with every button disabled and the count stated —
  the count is an answer R16 wants in its own right, not a by-product of there being more than one
  page.
- Component-tested with Vitest and Testing Library, driving accessible roles and the Galician copy
  from `strings.ts` rather than hardcoded literals.

## Acceptance criteria

- The control offers first, previous, next, last and a jump to a chosen page, and states both the
  entry count and the page total.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #23, control half)
- On page 1 *first* and *previous* are disabled and still present; on the last page *next* and
  *last* are disabled and still present; with one page all four are disabled. (SPEC-0005 #23)
- Each control emits the page it names — `last` emits `totalPages`, `next` emits `page + 1` — and
  the number emitted is the same 1-based number the control displays. (SPEC-0005 #23, #24)
- A jump to `0`, to `totalPages + 1` or to a non-number emits nothing.
- The component's props are the envelope's four fields; it takes no items, no row renderer and no
  knowledge of what is paged, and `ui/src/shared/ui/` imports nothing from `features/`.
- The record count reads `1 rexistro` for one entry and `N rexistros` otherwise, from `strings.ts`.
- Copy is Galician, lives in the shared strings namespace, and the control is usable at a 360 px
  viewport. ([SPEC-0001](../../specs/SPEC-0001-web-ui.md) AC6, AC7)
- `npm run lint`, `npm run build` and `npm test` pass from `ui/`.
