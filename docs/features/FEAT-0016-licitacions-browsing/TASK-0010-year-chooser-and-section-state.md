---
feat: FEAT-0016
domain: frontend
adrs: [0004, 0015]
status: todo
depends_on: [TASK-0009]
---

# The year chooser, and what the section says about itself

R22's mandatory year scoping and R26's two statements, rendered. All three facts come from the outlet
context [TASK-0009](TASK-0009-licitacions-section-slice-and-route.md) narrows — **this task fetches
none of them**.

## Scope

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

**Out of scope:** the row ([TASK-0011](TASK-0011-the-licitacion-row.md)), the CPV and state filters
([TASK-0012](TASK-0012-cpv-and-state-filters.md)), and the URL handling of the selection
([TASK-0013](TASK-0013-sorting-and-paging-over-the-selection.md)) — though the chooser writes through
the same helper, so the *any change to the selection drops the page* rule is held in one place.

## Acceptance criteria

- The section opens on the **most recent** year the Órgano has visible licitacións in, and the years
  offered are **exactly** those — no year the Órgano has none in, and no non-year entry of any kind.
  ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #32)
- **No control anywhere in the section produces an all-years list**, and no URL a reader can reach
  from it omits the year. (SPEC-0008 #32)
- An Órgano whose initial licitacións import is **still running** presents the section stating that
  what is shown is **partial**, distinguishably from one whose import has completed. (SPEC-0008 #37)
- An Órgano that was imported and has since been **unmarked or become inactive** keeps its section and
  its years, and says it is **no longer being updated**. (SPEC-0008 #6)
- An Órgano that is **both** — unmarked mid-import — states **both**, as two statements rather than
  one collapsed status. (SPEC-0008 #6, #37)
- An Órgano with a **completed** import that is still marked and active states **neither**.
  (SPEC-0008 #37)
- Choosing a year re-reads the list for that year and returns the reader to the first page.
  (SPEC-0008 #34)
- The section makes **no request** for its years, its `partial` or its `updating` — asserted by a
  component test that provides the context and stubs no summary endpoint.
- All copy resolves from `strings.licitacions`; no user-facing literal appears in a component.
