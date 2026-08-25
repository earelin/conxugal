---
feat: FEAT-0016
domain: frontend
adrs: [0003, 0004, 0015, 0018]
status: todo
depends_on: [TASK-0007, TASK-0009]
---

# The selection module, the year chooser, and what the section says about itself

R22's mandatory year scoping and R26's two statements, rendered — and, underneath them, **the module
every later control writes the selection through**.

The section's three facts come from the outlet context
[TASK-0009](TASK-0009-licitacions-section-slice-and-route.md) narrows; **this task fetches none of
them**. What it does fetch is nothing at all: the list read lands with
[TASK-0011](TASK-0011-the-licitacion-row.md).

## Scope

### The selection module

`selection.ts` and `selectionUrl.ts` for this family, the counterparts of
`features/contratos-menores`'s — the pure half (no browser) and the browser half:

- **parsers that never throw**, for `year`, `cpv`, `state`, `sort` and `page`, each answering a
  fallback rather than raising, so no hand-typed or stale URL can break the section;
- **the single write rule** — any write to `year`, `cpv`, `state` or `sort` **deletes `page`** — held
  here and nowhere else, so R23's *changing the selection re-pages it from its first page* is one rule
  rather than a thing four controls each remember;
- **the respelling correction**, which rewrites a URL that states the selection differently from how
  it is shown (`?year=02025`) with `replace` rather than `push`, while distinguishing a *respelling*
  from a *move* (`?year=2019`);
- `locationWith` / `locationFor` / `choose`, preserving the hash.

**It lands here, with the first control that writes through it**, which is FEAT-0011's own ordering —
that feature landed its whole selection with the year chooser, before the row and before paging. An
earlier draft of this feature deferred it to
[TASK-0013](TASK-0013-sorting-and-paging-over-the-selection.md) while tasks 10 and 12 both wrote
through it, which left the module owned by nobody and #34's re-page half claimed by three tasks and
observable at none of their landing points.

❗ **A stale or malformed `year` in the URL is this module's to answer**, and nothing downstream will:
the endpoint **400s** on one. An unparseable or absent year falls back to the section's default —
the most recent year the summary offers — by `replace`, so a shared link that has gone stale lands the
reader somewhere real rather than on an error.

**It is not a copy of the contratos menores module.** That one carries a year, a sort and a page; this
one carries two filters as well, and its sort names different keys. The parsers are the same *shape*
and the same traps — the `DIGITS` guard that stops `0x7e8` parsing as a year, the bound checks, the
never-throw rule — which is why they are written by reading that module rather than by rediscovering
them.

### The chooser and the statements

- **The year chooser** — a `Select` offering **only** years the Órgano has visible licitacións in,
  newest first, defaulting to the **first** of them, which is the most recent.

  It offers **nothing but years**: no *all years*, no *undated*, and no equivalent. R22 forbids an
  all-years list and there is no way to obtain one — the selection type has no absence and the
  endpoint refuses a request without a year, so the control is the third place the same rule holds
  rather than the only one.
- **R26's two statements**, as two independent alerts, because the two facts are orthogonal and an
  Órgano unmarked halfway through its initial import is **both**:
  - **partial** — the initial licitacións import has not finished, so what is shown is incomplete and
    a reader must not read a growing list as a complete one;
  - **no longer being updated** — the Órgano has been unmarked or has become inactive, and its
    retained licitacións are no longer refreshed.
- **The empty section never renders**, because it never mounts: the tab does not exist for an Órgano
  with no visible licitación, and once the section is present R22 guarantees it is never empty, since
  the chooser offers only years that have rows.

**One consequence worth rendering carefully.** FEAT-0015's initial walk is ordered by **`id`
ascending**, not newest-first, so a partially imported Órgano's years fill in from the **oldest** end
and it opens on the most recent year *it has so far* — which may be years behind the source. That is
the opposite of contratos menores, whose newest-first walk made the default year meaningful from the
first batch. The *partial* statement is what tells a reader the list is still filling; the copy should
not imply the newest year is present.

**Out of scope:** the row and the list read ([TASK-0011](TASK-0011-the-licitacion-row.md)), the CPV and
state filter **controls** ([TASK-0012](TASK-0012-cpv-and-state-filters.md)), and the sorts and the
paging control ([TASK-0013](TASK-0013-sorting-and-paging-over-the-selection.md)) — all three of which
write through the module built here rather than adding to it.

## Acceptance criteria

- The section opens on the **most recent** year the Órgano has visible licitacións in, and the years
  offered are **exactly** those — no year the Órgano has none in, and no non-year entry of any kind.
  ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #32)
- **No control anywhere in the section produces an all-years list**, and no URL a reader can reach
  from it omits the year. (SPEC-0008 #32)
- An Órgano whose initial licitacións import is **still running** presents the section stating that
  what is shown is **partial**, distinguishably from one whose import has completed. (SPEC-0008 #37)
- An Órgano that was imported and has since been **unmarked or become inactive** keeps its section and
  its years, and says it is **no longer being updated**. (SPEC-0008 #6 display half)
- An Órgano that is **both** — unmarked mid-import — states **both**, as two statements rather than
  one collapsed status. (SPEC-0008 #6 display half, #37)
- An Órgano with a **completed** import that is still marked and active states **neither**.
  (SPEC-0008 #37)
- **Choosing a year rewrites the URL to that year and drops `page`** — asserted on the module and on
  the chooser, without a list to read, since the rule is the module's and not the list's.
  (SPEC-0008 #34 re-page half)
- **Every parser answers a fallback rather than throwing**, over a table of malformed inputs: a
  non-numeric year, `0x7e8`, a five-digit year, a `page` of `0`, a negative `page`, an unknown `sort`
  spelling, and an empty value for each. A **stale but well-formed** year the summary does not offer
  falls back to the default by `replace`, so the reader lands on a real selection rather than on the
  400 the endpoint would answer. (SPEC-0008 #32)
- A URL stating the selection differently from how it is shown (`?year=02025`) is **respelled with
  `replace`**, while a genuine move (`?year=2019`) **pushes** — the distinction that keeps the back
  button usable. (SPEC-0008 #34)
- The section makes **no request** for its years, its `partial` or its `updating` — asserted by a
  component test that provides the context and stubs no summary endpoint.
- All copy resolves from `strings.licitacions`; no user-facing literal appears in a component.
