# QA review — UI (2026-08-05)

Exploratory QA pass over every feature currently implemented in `ui/`, driven
through Chrome against a **real backend** (not the WireMock stub), as an `ADMIN`
and as a `USER`.

## 1. Scope and environment

| Item | Value |
| --- | --- |
| Branch under test | `worktree-bug-remove-auto-select` (2 commits ahead of `trunk`) |
| UI | `ui/` — Vite dev server *and* a production `vite preview` build |
| Backend | `server` running from the main checkout (`./gradlew run`, `MICRONAUT_ENVIRONMENTS=local`) |
| Datastore | PostgreSQL 18 (`server/docker-compose.yml`), Flyway-seeded incl. `db/migration-local` |
| contratosdegalicia.gal | WireMock stub (`server/docker-compose.yml`), 430 órganos |
| Browser | Chrome, viewport **1768 × 982** CSS px, dark colour scheme (system `auto`) |
| Accounts | `root@local` (ADMIN), `demo@local` (USER) — repo test credentials |

Features covered: FEAT-0001 (scaffolding), FEAT-0002 (auth), FEAT-0004
(administration area), FEAT-0005 (realtime metrics), FEAT-0006 (órganos import),
FEAT-0007 (taxonomía / classification), FEAT-0008 (session menu).

Automated baselines re-run as part of this review — all green:

- `npm run lint` — clean
- `npm test` — **299 tests, 26 files, all passing**
- `npm run build` — succeeds (with one bundle-size warning, see [L-8](#l-8--main-entry-chunk-is-754-kb))

## 2. Verdict

The application is in good shape functionally. Every implemented journey works
end to end against a real backend, the refusal/error copy is unusually
well-considered (cycles, child terms, duplicate siblings and last-admin are all
explained in terms the administrator can act on), and role gating is enforced on
both sides. Nothing here is a release blocker for an internal admin tool.

The defects cluster in three places:

1. **Dark mode was not verified against real tokens.** Several hardcoded light
   greys never flip, one of which makes the account menu illegible.
2. **Localisation of dates is silently broken in Chrome** — a Galician-only UI
   renders `5 Aug 2026`.
3. **The classification worklist does not scale.** 430 rows today, unpaginated,
   with the entry point and the total count both parked at the bottom of a
   22,430 px page.

| Severity | Count |
| --- | --- |
| High | 4 |
| Medium | 8 |
| Low | 11 |

## 3. High

### H-1 · All dates render in English, not Galician

**Where:** `ui/src/shared/lib/date.ts:2`, `ui/src/features/administration/monitoring/DashboardPage.tsx:33`,
`ui/src/features/administration/monitoring/metrics/metricsFormat.ts:23,38`

The code correctly asks for `gl-ES`, but Chrome's ICU build ships no Galician
date data and silently resolves it to `en-GB`. Measured in the browser under test:

```js
new Intl.DateTimeFormat('gl-ES').resolvedOptions().locale        // → "en-GB"
d.toLocaleDateString('gl-ES', {day:'numeric',month:'short',year:'numeric'}) // → "5 Aug 2026"
d.toLocaleDateString('es-ES', {day:'numeric',month:'short',year:'numeric'}) // → "5 ago 2026"
```

**Observed:** the *Creación* and *Último acceso* columns of *Xestión de usuarios*
read `5 Aug 2026` in an interface that is otherwise entirely Galician. Firefox
does ship `gl` data, so the bug is browser-dependent and easy to miss.

**Suggested fix:** don't trust the runtime for `gl`. Either format month names
from `strings.ts`, or pass an explicit fallback chain (`['gl-ES', 'es-ES']`) so
the degradation lands on Spanish rather than English. Note this also explains
why `ui/CLAUDE.md` tells acceptance specs never to assert on locale-formatted
dates — the underlying problem is worth fixing rather than working around.

### H-2 · Account menu is unreadable while open (dark mode)

**Where:** `ui/src/app/layout/UserMenu.module.css:9`

```css
.trigger:hover,
.trigger[data-expanded] {
  background-color: var(--mantine-color-gray-1);   /* #f1f3f5 — static, never flips */
}
```

`--mantine-color-gray-1` is a fixed palette value, so in dark mode the trigger
gets a near-white background while the text inside stays light. Measured
computed values with the menu open:

| Element | Foreground | Background | Contrast |
| --- | --- | --- | --- |
| `root@local` | `#c9c9c9` | `#f1f3f5` | **1.55 : 1** |
| `Administradora` | `#828282` | `#f1f3f5` | **3.4 : 1** |

The email is effectively invisible on hover and while the menu is open. Same
class of bug on plain hover, which every user hits before clicking.

**Suggested fix:** `background-color: light-dark(var(--mantine-color-gray-1), var(--mantine-color-dark-5));`

### H-3 · Every modal's close button has no accessible name

**Where:** all six `<Modal>` call sites — `CreateUserModal.tsx:156`,
`CreateTermoModal.tsx:85`, `RenameTermoModal.tsx:59`, `MoveTermoModal.tsx:83`,
`DeleteTermoModal.tsx:59`, `AssignOrganoModal.tsx:149`

None passes `closeButtonProps`, so Mantine renders a bare icon button:

```text
buttons in "Conta creada" dialog → ["(unnamed)", "Copiar", "Feito"]
aria-label: null · title: null · text content: ""
```

A screen-reader user hears only "button". It is also the element that receives
focus when the dialog opens, so it is the *first* thing announced.

**Suggested fix:** add `closeButtonProps={{ 'aria-label': strings.close }}` (a new
shared string) to every modal — or wrap `Modal` once in a local component that
supplies it, so the next dialog cannot forget.

Note the contrast with the rest of the section, which is done well: the import
alert's close button *is* labelled (`ImportOrganosControl.tsx:61`), and the tree
row actions carry per-term labels like `Renomear: Atención hospitalaria (proba)`.

### H-4 · Dimmed text fails WCAG AA in both colour schemes

**Where:** every `c="dimmed"` — page subtitles, notes, `Nunca`, breadcrumbs,
metric labels, home/about body copy.

| Context | Foreground | Background | Contrast | AA (4.5:1) |
| --- | --- | --- | --- | --- |
| Dark scheme | `#828282` | `#242424` | 4.04 : 1 | fail |
| Light scheme | `#868e96` | `#ffffff` | 3.32 : 1 | fail |
| `Nunca` inside a disabled user row (0.6 opacity) | — | — | **2.32 : 1** | fail |
| Normal text inside a disabled user row | — | — | 4.32 : 1 | fail |

The disabled-row case (`UsersTable.tsx:38`, `opacity: 0.6`) compounds with the
dimmed colour and lands well under even the 3:1 large-text threshold.

**Suggested fix:** override `--mantine-color-dimmed` in `theme.ts` to a value
that clears 4.5:1 in both schemes, and express the disabled-row state with a
colour token rather than a blanket opacity so it doesn't multiply.

## 4. Medium

### M-1 · `document.title` never changes between routes

Measured across every route — `/`, `/acerca`, `/administracion`,
`/administracion/usuarios`, `/administracion/organos` — the title is always
`conxugal`. Browser history, tab strips, bookmarks and the screen-reader page
announcement therefore carry no information about where the user is. The
server-rendered login page does this correctly (`Iniciar sesión · conxugal`).

**Suggested fix:** set the title per route (a small `useDocumentTitle` in
`AppLayout`, or `title` on each `RouteObject.handle`), with strings from
`strings.ts`.

### M-2 · Failed reads on the panel and users pages are a dead end

Reproduced by forcing `/api/admin/**` to 500.

- **Panel:** the error alert replaces `DashboardContent` — and the *Actualizar*
  refresh button lives inside it, so it disappears with the card. `DashboardError`
  (`DashboardPage.tsx:48-63`) renders a plain `Alert` with no retry.
  Verified: `main` contains **zero** buttons in this state.
- **Usuarios:** `UsersError` (`UsersPage.tsx:12-18`) also uses `ErrorAlert`
  without `onRetry`; *Novo usuario* is correctly disabled. Also no way back.

In both cases the only recovery is a full page reload. `ErrorAlert` already
supports `onRetry`/`retrying`, and the Órganos section uses it
(`OrganosPage.tsx:122`) — this is an inconsistency, not a missing capability.

### M-3 · The unclassified worklist does not scale

Measured against the 430-órgano stub catalogue, **production build**:

| Metric | Value |
| --- | --- |
| Rows rendered | 430 (no pagination, no virtualisation) |
| DOM nodes on the page | ~6,900 |
| Page height | **22,430 px** |
| Time to paint after selecting *Sen clasificar* | **216 ms** (1,027 ms in the dev build) |

The real contratosdegalicia catalogue is larger than the stub, and this scales
linearly. There is also no search or filter over the worklist, so finding a
specific órgano to classify means scrolling.

### M-4 · The órgano count is at the bottom of a 22,430 px page

`TermoContentCard` renders `430 órganos sen clasificar` *after* the table. To
learn how much work is left, an administrator scrolls past all 430 rows.

**Suggested fix:** move the count next to the pane title (where the subtitle
already is), or show it in both places.

### M-5 · *Sen clasificar* is pinned below the entire taxonomy

The worklist entry — the default selection and the main entry point of the
classification workflow — is the last row of the tree card, below every term.
With 35 test terms it already sits at y≈908; with a real taxonomy it moves
further down every time a term is added.

**Suggested fix:** pin it above the tree, or make the tree pane scroll
independently so the entry stays reachable.

### M-6 · Sparklines are near-empty for the first ~21 minutes

`MetricSparkline.tsx:29-31` pads the series to `METRICS_HISTORY_LIMIT` (250)
with `null`s, so the x-axis is always sized for a full history. At the 5 s
sample cadence that is **~21 minutes** before the chart fills.

Combined with the documented behaviour that history lives only in the browser
and resets on reload (`historyNoteSuffix`), the state an administrator almost
always sees is a flat line with a 2 px sliver at the far left, labelled
`1/250 mostras`. It reads as broken rather than as "not enough data yet".

**Suggested fix:** scale the domain to the samples actually held (or to a much
shorter rolling window) and keep the 250-sample buffer purely as a cap.

### M-7 · Static grey tokens don't flip in dark mode

Same root cause as [H-2](#h-2--account-menu-is-unreadable-while-open-dark-mode); there is **no** use of `light-dark()`
or a dark mixin anywhere in `ui/src`.

| Where | Token | Effect in dark mode |
| --- | --- | --- |
| `MetricSparkline.tsx:39` | `gray-1` baseline | Confirmed: measured `rgb(241,243,245)` on 4 charts — the **brightest element on the card**, ~13:1 against the card background, so the empty axis dominates the real data |
| `DatastorePoolCard.tsx:46` | `gray-2` empty-pool track | A near-white full-width bar when the pool reports no capacity |
| `DatastorePoolCard.tsx:53,115` | `gray-2` *Libres* segment | Free connections render as a bright white block |
| `DatastorePoolCard.tsx:75` | `gray-3` segment border | Light hairline on a dark card |
| Several files | `gray-6` icons | Acceptable in both schemes — no change needed |

`MemoryGcCard` uses Mantine's `<Progress>`, whose track *is* theme-aware
(measured `rgb(66,66,66)`) — that one is fine.

### M-8 · Breadcrumbs look interactive but are not

`TermoContentCard.tsx:104-110` renders every crumb as `<Text>`. Verified: all
five nodes of `Taxonomía › Sanidade (proba) › Atención hospitalaria (proba)` are
`<p>` elements, none focusable, none inside a `<nav>`. Users will try to click an
ancestor to move up the tree; nothing happens.

**Suggested fix:** make ancestor crumbs select their term (Mantine `Anchor` +
`onClick`), wrap in `<nav aria-label>`, and mark the last with `aria-current="page"`.

## 5. Low

### L-1 · The parent-term picker is not searchable

`TermoParentSelect.tsx:162` renders a plain `Select`. It already lists **37**
path-labelled options and grows with the taxonomy. The órgano picker in the same
dialog family *is* searchable (`Buscar órgano…`), so the two halves of the same
task behave differently. Adding `searchable` is a one-word change.

### L-2 · Nested `<nav>` landmarks, one unlabelled

`AppLayout.tsx:59-60` puts `<nav aria-label="Navegación principal">` inside
Mantine's `AppShell.Navbar`, which is itself a `<nav>`. Verified in the
accessibility tree: two navigation landmarks, the outer one anonymous.

### L-3 · No skip-to-content link

Verified absent. Keyboard users tab through the burger, brand, account menu and
all five nav links before reaching `main` on every page.

### L-4 · Tree parents expose no `aria-expanded`

Mantine `Tree` items carry `role="treeitem"`, `aria-selected` and roving
`tabindex` (all correct), but nodes with children report `aria-expanded: null`
despite rendering a chevron. Screen-reader users get no collapse/expand state.
This is a Mantine-level gap, so it may need `renderNode` to add the attribute.

### L-5 · Stat cards say the same word twice

`DashboardPage.tsx:74-82` renders `value` both as the large text and inside the
badge, producing "SERVIZO / **Operativo** / `OPERATIVO`". Screen readers announce
it twice. The badge could carry the state colour without repeating the label.

### L-6 · A failed login discards the typed email

Verified: after `/login?error=true` the email field is empty (`previous: ""`).
The generic, indistinct error message is correct per SPEC-0002 #3 — this is only
about not making the user retype a value that wasn't necessarily wrong.

### L-7 · Client-side email validation is stricter than the server

`CreateUserModal.tsx:65-72` uses zod's `z.email()`, which rejects a domain with
no dot. Verified: entering `demo@local` produces *"Introduce un correo electrónico
válido."* — yet `demo@local` and `root@local` are exactly the accounts the system
seeds itself, and the API contract only says `format: email` (Hibernate
Validator accepts them). The UI cannot create an account of the shape the system
already uses.

### L-8 · Main entry chunk is 754 kB

```text
dist/assets/index-*.js   754.29 kB │ gzip: 229.65 kB   ← over Vite's 500 kB warning
dist/assets/metrics-*.js 301.90 kB │ gzip:  91.27 kB   ← correctly lazy-loaded
dist/assets/index-*.css  231.08 kB │ gzip:  33.79 kB
```

The metrics/charts split is already done well. The remaining eager chunk carries
Mantine core, react-hook-form, zod, TanStack Query and React Router for a
five-screen app. Worth a look before more features land, not urgent now.

### L-9 · Most mutations give no confirmation

Assign, *Quitar do termo*, rename, move, delete and enable/disable all succeed
silently — the list simply changes. Create-user (password panel) and import
(outcome alert) are the exceptions. *Quitar do termo* in particular is a
single click with no confirmation and no undo affordance; it is reversible, but
nothing on screen says so at the moment of clicking.

### L-10 · Delete dialog doesn't say how many órganos will be unclassified

`DeleteTermoModal` shows the rule ("Os órganos deste termo pasan a «Sen
clasificar»") but not the number, even though the term's órgano list is on screen
behind the dialog. Naming the count would match the quality of the
child-terms refusal, which does list them.

### L-11 · Dev-only: `/login` renders unstyled under `npm run dev`

`ui/vite.config.ts` proxies `/api`, `/login` and `/logout` but not
`/assets/static-pages/**`, so the login page's stylesheet request is answered
with the SPA's `index.html` (verified: HTTP 200, `content-type: text/html`). The
inline brand SVG then expands to full width and the page reads as blank. It is
correct when the backend serves both (`http://localhost:8080/login`), so this
only affects local development — but it makes the dev login flow look broken.

**Suggested fix:** add `/assets/static-pages` to the proxy path list.

## 6. Verified working

Recorded so a future pass knows what was actually exercised, not just eyeballed.

### Authentication (FEAT-0002)

- Valid login establishes a session and lands on `/`.
- Wrong credentials → `/login?error=true` with a single generic message; unknown
  email and wrong password are indistinguishable (SPEC-0002 #3).
- Unauthenticated access to any route redirects to `/login`; `/login` while
  authenticated redirects to `/`.
- Logout from the account menu clears the session and returns to `/login`.
- `GET /logout` correctly refuses with 405 — logout is POST-only.
- The login page's own markup is sound: `lang="gl"`, `<label for>` correctly
  bound to both inputs, `autocomplete="username"`/`current-password`, and a
  password-reveal toggle that swaps `aria-pressed` and `aria-label`.

### Role gating (FEAT-0004)

- As `USER`, the *Administración* nav section is hidden.
- As `USER`, navigating directly to `/administracion/usuarios` renders the
  in-shell 404 rather than a "forbidden" page — deliberate, and it holds.
- The server independently refuses: `GET /api/admin/users` and
  `/api/admin/organos` both return **403** for a `USER` session.

### Panel + metrics (FEAT-0004 / FEAT-0005)

- System status, datastore reachability and system info all render; the privacy
  note is present and no credentials or connection strings appear anywhere.
- SSE metrics stream live (`EN DIRECTO`, sample every 5 s) and keep streaming
  even when the system-status read fails — good graceful degradation.
- Sparklines are correctly `inert` + `aria-hidden` with the tile value as the
  accessible source of truth.
- The refresh button is icon-only but labelled `Actualizar`.

### Users (FEAT-0004)

- List, create, enable and disable all work against the real backend.
- Validation: empty → required message; malformed → invalid message. Both set
  `aria-invalid`, wire `aria-describedby` to the message, and move focus to the
  field.
- Duplicate email (409) is reported on the email field, not as a banner.
- Created account reveals a one-time password with a copy button and a warning;
  the list refreshes immediately.
- Last-enabled-admin guard: the button uses `aria-disabled` (not `disabled`), so
  it stays focusable, and the explanatory tooltip is reachable and wired through
  `aria-describedby`. This is the right way to do it.

### Órganos (FEAT-0006 / FEAT-0007)

- Catalogue import runs and reports `0 engadidos · 0 actualizados · 0
  desactivados` with a dismissible `role="status"` alert and a labelled close
  button.
- Create term: blank, 256-character and duplicate-sibling names are all refused
  with distinct, actionable messages; the typed name survives a refusal.
- Move: the cycle guard is evaluated client-side, disables the primary and
  explains itself by name — *"«Atención hospitalaria (proba)» é un termo fillo de
  «Sanidade (proba)»…"*.
- Delete: a term with children is blocked on open, naming all children; a leaf
  term deletes and disappears from the tree.
- Assign: the órgano picker searches and shows each candidate's current
  placement; assign and *Quitar do termo* both round-trip correctly.
- Dialogs are bound to the term they were opened on — confirmed by opening a
  dialog after changing the selection (the fix in `e2c e7a`/`c303ebb` holds).
- Tree accessibility: `role="tree"` with `aria-label="Taxonomía"`,
  `aria-selected`, roving `tabindex`; ArrowDown/Enter navigate and select.
  Per-row action buttons render only for the selected row and carry labels that
  include the term name.
- Generic mutation failure keeps the dialog open with the typed value and shows
  *"Non se puido completar a acción…"*.

### Layout, responsive and shell

- `lang="gl"`, viewport meta and meta description are all present; the logo is
  correctly `alt=""` next to its text.
- At 390 px: burger nav, collapsed navbar, hidden tagline and account text,
  single-column stacking, **no horizontal overflow** (`scrollWidth === clientWidth`).
- Row actions collapse to icon-only on narrow screens *and keep a per-row
  accessible name* — e.g. `Asignar a termo: Academia Galega de Seguridade
  Pública (AGASP)`. Nicely done.
- Active nav link carries `aria-current="page"`; the pinned worklist row carries
  `aria-current`.
- No console errors or warnings on any route.
- Production load: `DOMContentLoaded` 193 ms, `load` 251 ms.

## 7. Not verified

- **One browser only.** Chrome on Linux. No Firefox, Safari or a real mobile
  device; mobile layout was checked by rendering the app in a 390 px iframe, and
  no touch-target or gesture testing was done. [H-1](#h-1--all-dates-render-in-english-not-galician) in particular behaves
  differently in Firefox.
- **No screen-reader run.** Accessibility findings come from the accessibility
  tree, computed styles and measured contrast ratios — not from NVDA/VoiceOver.
- **States that could not be provoked:** the import `ALREADY_RUNNING` response,
  a source-failure import (`urn:conxugal:problem-type:organo-import-failed`), the
  degraded/`DOWN` dashboard state, and the metrics `RECONECTANDO` state.
- **Concurrency refusals** (`notFound` / `termoNotFound` — another administrator
  deleting a record mid-dialog) were reviewed in code but not reproduced live.
- **Session expiry** (30 min inactivity → 401 → redirect) was not waited out.

## 8. Test data left behind

The local dev database was mutated. Net effect:

- **`qa.review@conxugal.gal`** — created to exercise the create/disable flow, and
  left **disabled**. Accounts are never deleted by design, so it cannot be
  removed through the UI; drop it directly if the database is not disposable.
- **`QA termo raíz`** — created, used, then deleted. No trace remains.
- One órgano (*Academia Galega de Seguridade Pública (AGASP)*) was assigned to
  that term and then unassigned — back to *Sen clasificar*.
- One catalogue import was run (`0 · 0 · 0` — no rows changed).
