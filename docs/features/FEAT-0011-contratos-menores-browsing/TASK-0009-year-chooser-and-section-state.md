---
feat: FEAT-0011
domain: frontend
adrs: [0003, 0004, 0015]
status: todo
depends_on: [TASK-0006]
---

# The section: its slice, its year chooser, and what it says about itself

The `features/contratos-menores` slice comes into existence here, with the year chooser and R18's
two statements. It **fetches none of it**: the years, `partial` and `updating` all arrive as
outlet context from [FEAT-0013](../FEAT-0013-organo-contracts-page/README.md)'s page, which reads
them from the summary [TASK-0006](TASK-0006-section-summary-port-and-schema.md) publishes.

**Also depends on [FEAT-0013](../FEAT-0013-organo-contracts-page/README.md)'s task 2**, which
builds the page, its tab bar and the outlet that carries the context. That dependency is outside
this feature and so is not in `depends_on:`; the component is testable against a supplied context
before it lands.

## Scope

- **A new slice**, `ui/src/features/contratos-menores/`, exposing only an `index.ts` barrel —
  named for the **family** it renders rather than for contracts in general, because the
  licitacións section will be its sibling, not its successor.
  `eslint-plugin-boundaries` forbids it importing FEAT-0013's page slice or being imported by it
  ([ADR-0015](../../architecture/0015-frontend-feature-based-shared-core-modularization.md)); the
  router composes them, and **declaring the child route is FEAT-0013's task 3**, not this one's.
- **The section component**, reading its summary through `useOutletContext()` and **narrowing the
  opaque `families['contratos-menores']` entry to the schema this feature owns**. The opaque entry
  type lives in `shared/entities`, where FEAT-0013 declares it; `shared/` deliberately does not
  know this family's shape and no other slice may narrow it.
  - **The context is data, not an import.** Nothing here imports FEAT-0013, and nothing there
    imports this.
- **The year chooser**: a Mantine `Select` offering **only years the Órgano has visible contracts
  in**, in the newest-first order they arrive, defaulting to the **first** entry. It offers
  **nothing but years** — no *all years* entry, no *undated* entry, no *(todos)* placeholder,
  because neither exists in the domain.
  - The chosen year is written to the URL query string as `?year=YYYY`, spelled exactly as the API
    takes it. That is where the selection lives, so a control and a rendered list cannot disagree
    about it, and a contract list is shareable and deep-linkable.
  - Arriving with no `year`, or with one the Órgano has no contracts in, lands on the default year
    rather than on an error.
- **R18's two statements**, as two Mantine `Alert`s in this product's existing semantics:
  *importación en curso* is informational and takes `indigo` light; *este órgano xa non se
  actualiza* is **inert and takes grey** — the reading a disabled account gets. Neither is red;
  neither is an error. They are **independent**, and both render at once for an Órgano unmarked
  halfway through its initial import.
- **No empty state, and none is built.** The chooser offers only years that have contracts, so no
  choice a reader can make produces an empty list; and a section with no summary never mounts.
- **Copy** in `ui/src/shared/lib/strings.ts` under this section's own namespace, in Galician, with
  the keys the [design README](design/README.md) lists.
- Component-tested with Vitest and Testing Library: the chooser's contents and default, the URL it
  writes, and each combination of the two flags.

## Acceptance criteria

- The chooser lists exactly the years the summary offers, newest first, and opens on the most
  recent; it offers no *all years*, *undated* or placeholder entry of any kind.
  ([SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #43)
- Choosing a year writes `?year=YYYY` to the URL, and loading a URL that already carries one opens
  on that year. (SPEC-0005 #27)
- A section whose summary says `partial` states that what is shown is incomplete; one that does
  not, does not. (SPEC-0005 #26)
- A section whose summary says the Órgano is no longer updated states so, in grey rather than as
  an error, and distinguishably from *partial*. (SPEC-0005 #7 third clause, #26)
- Both statements render together when both flags are set, neither replacing nor contradicting the
  other. (SPEC-0005 #26)
- The slice issues **no request** for the summary, the years or the Órgano's name: they arrive as
  outlet context, and the component renders from a supplied context in its tests.
- `ui/src/features/contratos-menores/` imports nothing from `features/organo`, and nothing imports
  it except `app/router.tsx` — `npm run lint` proves it through `eslint-plugin-boundaries`.
- Copy is Galician, lives in `strings.ts`, and the section is usable at a 360 px viewport.
  ([SPEC-0001](../../specs/SPEC-0001-web-ui.md) AC6, AC7)
- `npm run lint`, `npm run build` and `npm test` pass from `ui/`.
