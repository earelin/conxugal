---
feat: FEAT-0012
domain: frontend
adrs: [0004, 0015, 0018]
status: done
depends_on: [TASK-0003]
---

# The picker's text filter

[SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) R19's name search, added
to the control [TASK-0003](TASK-0003-side-panel-organo-picker-tree.md) built: a text box in the
dropdown that filters **the same list the tree is built from**, as the user types, with no request
per keystroke and no query endpoint.

**Search and tree are two states of one control, not two components.** #26 requires the search to
offer exactly what the tree shows, in both directions; one control over one in-memory list makes
that true by construction rather than by two implementations staying in step. The filter is
therefore never a second surface: with it empty the body is the tree, with text in it the body is
the matches.

It is drawn in [`design/picker-search.svg`](design/picker-search.svg), whose no-matches state is
the dashed inset.

## Scope

- **`shared/lib/organoSearch.ts`** — a pure, unit-tested pair: a normaliser that lowercases and
  strips diacritics (`String.prototype.normalize('NFD')` with combining marks removed) and a
  `matches(name, query)` built on it, `includes`-style so an **interior** fragment matches.
  - A naïve `toLowerCase().includes()` fails `avila` → `Ávila`, and this catalogue is full of
    accents. Both sides are normalised, so the match holds whichever side carries the accent.
  - It lives beside the tree builder in `shared/lib` and is tested without rendering.
- **`shared/ui/OrganoPicker.tsx`** — a Mantine `TextInput` at the top of the dropdown body, with
  the design's `searchPlaceholder`, filtering `view.catalogue` — the flat, name-ordered list the
  narrowed read delivered and the tree was assembled from. **Not the pruned tree, and not a second
  fetch**: the same list is what makes #26 hold.
  - **Empty or whitespace-only input shows the tree** (SPEC-0004 #9, and R19's *a blank query
    offers nothing*, which is a rule about the matches — they are empty until something is typed).
  - **Matches render as a flat list**, each stating the Órgano's name and, when it is inactive, an
    inert grey `Inactivo` marker. Choosing one navigates to `/organo/{id}` and closes the dropdown,
    exactly as choosing it in the tree does — the same handler, not a second one.
  - **No matches states so**, quoting the query back (`noMatches`) with `noMatchesHelp` beneath,
    and is visibly a different thing from a filter not yet typed in. Neither state ever falls back
    to listing the catalogue flat.
  - **No paging, no sorting, no filters, no result count** — R19 is a way to *find* an Órgano, not
    a view of the catalogue.
  - **Two details the mockup renders are deliberately not built**, as the design README records:
    the highlighted matched substring, and the term-path caption under each match. R19 asks for
    neither; the inactive marker is the one thing #23 does ask for.
- **`ui/src/shared/lib/strings.ts`** — `searchPlaceholder`, `noMatches`, `noMatchesHelp` and
  `inactive` join the picker's namespace, in Galician, as the design's copy table records them.
- **Tests:** unit tests for the matcher from both directions (`avila` finds `Ávila`, `Ávila` finds
  `avila`, an interior fragment finds its name, a non-match answers false); component tests for
  typing, the empty and whitespace-only cases, the no-matches copy, the inactive marker and
  selection; and an acceptance spec
  ([ADR-0018](../../architecture/0018-frontend-acceptance-tests-against-a-stubbed-api.md)) driving
  the picker against the narrowed WireMock stub — that an Órgano the administration area lists is
  findable by **neither** the tree nor the search when it is outside the visible set.

## Acceptance criteria

- Typing part of a name offers the matching Órganos **as the user types**, with nothing to submit
  and without leaving the page; choosing a match opens the same Órgano choosing it in the tree
  would. (SPEC-0004 #23)
- A query differing from the stored name only in **case or accents** still finds it — `avila`
  offers `Ávila` — and a query matching an **interior** fragment finds its name too.
  (SPEC-0004 #25)
- Each offered entry states whether that Órgano is **inactive**. (SPEC-0004 #23)
- A **blank or whitespace-only** query offers no matches and shows the **tree**; a query matching
  nothing **says so**, quoting what was typed. The two render differently, and neither returns the
  whole catalogue as a flat list. (SPEC-0004 #24)
- The search offers **exactly** the Órganos the tree shows: an unclassified one and an inactive one
  in the visible set are both findable, and one outside the visible set is findable by neither — no
  name can be typed to reach what the tree withholds. (SPEC-0004 #26 browse half;
  [SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) #20)
  - **#26's second sentence is not claimed here.** *In the administration area both cover the whole
    catalogue* needs an admin search, which does not exist and which this feature does not add.
- Typing issues **no** HTTP request: the filter runs over the list already held. (SPEC-0004 R20)
- The matcher is a pure function tested without rendering, and the picker holds **one** definition
  of what matches — no second component and no second list. (SPEC-0004 #26)
- All copy is Galician and lives in `strings.ts` under the picker's namespace; the dropdown is
  usable at a 360 px viewport. ([SPEC-0001](../../specs/SPEC-0001-web-ui.md) AC6, AC7)
- `npm run lint`, `npm run build`, `npm test` and the acceptance suite pass from `ui/`.
