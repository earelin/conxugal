---
name: frontend-design
description: >-
  Apply the conxugal UI design language when building or changing frontend screens,
  pages, components, forms, tables, or dialogs in the ui/ module. Use whenever you add
  a new page/route or non-trivial component so it reads as part of the same Mantine
  product rather than a bolt-on. Covers layout chrome, design tokens, component
  patterns (cards, stat cards, tables, badges, buttons, forms/modals), status
  semantics, and Galician copy. Also covers the three server-rendered pages the backend
  serves outside the SPA (login, forbidden, server error). Grounded in the
  administration-area visual design.
---

# conxugal frontend design

Build every screen so it reads as one product. The design language is **Mantine v9
with the project theme** — do not invent new colours, spacings, radii, or bespoke CSS
when a theme token or Mantine prop already expresses it. The administration-area mockups
(`docs/design/administration-area/`) are the canonical reference for what "coherent" looks
like at full-screen scale; mirror their structure. This file is the authority on the rules —
where a mockup disagrees with it or with a shipped screen, the mockup is stale.

## Sources of truth — read these first

Never hardcode what these modules already own:

- **`ui/src/app/theme.ts`** — the Mantine theme (15 lines; read it). Use theme tokens
  (`c="dimmed"`, `radius="md"`, colour names like `green`, `red`), never literal hex. If a
  token is missing, add it to the theme rather than inlining a value.
- **`ui/src/shared/lib/strings.ts`** — all user-facing copy, in **Galician**. Add new
  copy here under a per-screen key; never write literal user-facing text in a
  component.
- **`ui/src/app/nav.ts` + `ui/src/app/layout/AppLayout.tsx`** — the persistent
  `AppShell` chrome (header + collapsible navbar). New sections extend this
  data/layout; they do not fork it.
- **`@tabler/icons-react`** — the icon set. Use it for every icon; never hand-roll SVG
  paths in components (the mockups draw raw SVG only because they are static artifacts).

## Design tokens

Mantine's stock palette supplies the values; what this table fixes is **which role gets which
token** — that mapping is the design language, and it is not derivable from the theme file.

| Role | Token |
| --- | --- |
| Primary — filled buttons, active nav, avatars | `indigo.6` |
| Soft accent / active nav background | `indigo.0` with `indigo.8`/`indigo.9` text (Mantine `light` variant) |
| Page background / surfaces | `gray.0` page, white surfaces |
| Card border / in-card divider | `gray.3` border, `gray.1` hairline |
| Body text / dimmed text | `gray.9` (headings weight 600) / `gray.6` |
| Healthy, enabled | `green` |
| Disabled, inert | `gray` — never red; inert is not an error |
| Required, destructive, a crossed alert threshold | `red` |
| Stale but not broken — reconnecting, or a warning band | `yellow` (Mantine stock, no theme change) |
| Chart line, and its stale state | `indigo.6` at `fillOpacity={0.4}`; `indigo.2` when stale |

Rule of thumb: **reach for a Mantine prop before a style**. `c="dimmed"`, `fw={600}`,
`gap="sm"`, `mt="md"`, `radius="md"`, `withBorder` — not inline `style={{...}}` or raw
hex. Spacing uses the Mantine scale (`xs/sm/md/lg/xl`), not arbitrary pixels.

## Brand & logo

The product mark is a **"G"** (Galicia) built from a **C** (conxugal) opening to the
right with an **arrow** driving out through it — the arrow reads as the *export/output*
of contract data, the last step of *extract → store → analyse → export*. The C is
`indigo` (`#4c6ef5`, the primary token) and the arrow is `red` (`#fa5252`, the same
token as required/destructive); on the tiled icon the C is white on an `indigo` tile.

Two ready-made assets are the source of truth in `ui/public/` — **use them, never
redraw or recolour the mark**. Reference copies also ship with this skill under
`assets/` so the mark travels with the design language:

| Asset | What it is | Use for |
| --- | --- | --- |
| `logo.svg` | The **icon**: white C + red arrow on a rounded `indigo` tile (radius ~23%) | Favicon and any app-badge/tiled context |
| `logo-glyph.svg` | The **glyph**: indigo C + red arrow, transparent background | The mark on a white/light surface with no tile |

Ship-time `ui/public/logo.svg` and `logo-glyph.svg` are the ones the app serves; the
`assets/` copies are for reference — if the mark ever changes, update both.

- The **favicon** is wired in `ui/index.html`
  (`<link rel="icon" type="image/svg+xml" href="/logo.svg" />`); the **header** mark is
  the tiled icon in `AppLayout.tsx`, rendered at 36px left of the product name.
- Reference the mark as an **`<img src="/logo.svg">` asset** — do not paste the SVG
  paths into a component (same rule as icons: components consume assets, not raw paths).
- The mark is **decorative** wherever the adjacent "conxugal" wordmark already names the
  product (`alt=""`); give it a real `alt`/`aria-label` only when it stands alone.
- It stays legible down to 16px — reach for `logo.svg` (tile) at small sizes and
  `logo-glyph.svg` only where a tile would be wrong.

## Layout & chrome

- Every authenticated screen **in `ui/`** lives inside the existing `AppShell` from
  `AppLayout.tsx`.
  Header carries product name + tagline (+ signed-in user where relevant); the navbar
  collapses behind the burger below `sm`. Do not add a second shell or a page-level
  sidebar.
- **Grouped nav sections** (e.g. `ADMINISTRACIÓN`) use an uppercase, letter-spaced,
  dimmed section label above the links. The active item uses the `light`/filled active
  state (indigo). Gate section visibility by role in the navbar for affordance only —
  the **server is the real gate**; never treat a hidden link as security.
- **Three pages are deliberately outside the shell**: login, forbidden and server error
  are plain server-rendered HTML from the backend (`server/.../resources/views/`), with
  no `AppShell`, no navbar and no application JavaScript — a denial or a crash must not
  depend on the SPA booting. They share only the **tokens** (colour, radius, type,
  Galician copy) and the brand mark, so the seam between "before the app" and "inside the
  app" is invisible. Do not give them app chrome, and do not move them into `ui/`.
  Two rules above are **knowingly broken** there, because those templates cannot reach
  `ui/`: their stylesheet (`server/.../static-pages/static-pages.css`) hand-copies the
  palette out of `theme.ts` rather than sharing it, and `views/fragments/brand.html`
  inlines the mark's SVG paths rather than referencing `/logo.svg`. Both must be updated
  by hand when the theme or the mark changes.
- All chrome and copy is **Galician**, sourced from `strings.ts`.

## Page & component patterns

Match these shapes exactly — they are what the mockups render.

### Page scaffold
Every page opens with a title block: `Title order={2}` (or a page `Title`) plus a
`Text c="dimmed"` one-line subtitle describing the screen. Wrap page content in a
`Stack gap="md"|"lg"`.

```tsx
<Stack gap="lg">
  <Stack gap={4}>
    <Title order={2}>{strings.users.title}</Title>
    <Text c="dimmed">{strings.users.subtitle}</Text>
  </Stack>
  {/* content */}
</Stack>
```

### Cards & surfaces
White `Card withBorder radius="md"` with a subtle shadow on `gray.0` background. Use a
section title (`Text fw={600}`) + a hairline `Divider` (`gray.1`) to separate a card
header from its body. Group cards in a responsive `SimpleGrid`, not fixed pixel columns.

### Stat / status cards
An uppercase dimmed **label** (letter-spaced, ~11px, `fw={600}`), a large value with an
optional leading **status dot** (`green`/`gray`), and a `Badge` on the right. A hairline
divider then a dimmed caption for context. See `dashboard.svg`.

### Sparklines & segmented bars
A **sparkline** under a stat card's value shows where that figure has been. One series only,
so it carries **no legend and no axes** — the card's label already names the data. Draw it with
`strokeWidth={2}`, `color="indigo.6"` and `fillOpacity={0.4}` (the area is that same colour at
low opacity, not a separate fill token), over a `gray.1` baseline hairline; swap the colour to
`indigo.2` while the data is stale. Pad a partly-filled buffer to its full width rather than
stretching a few points across the box: a short line *is* how "still filling up" reads.

A sparkline is **decorative** — the number above it is the accessible source of truth — so mark
it `inert aria-hidden`, or recharts' `accessibilityLayer` leaves an unlabelled focusable
`role="application"` tab stop. Lazy-load anything importing `@mantine/charts`; it drags in
recharts.

A **segmented bar** (a pool, a quota) leaves a 2 px surface-coloured gap between segments so
adjacent fills stay distinguishable, and names each segment in a legend — never colour alone.
Keep each legend swatch the exact token of the segment it stands for.

### Tables
Mantine `Table` on a bordered card. Column headers are **uppercase, dimmed,
letter-spaced** labels on a `gray.0` header row. Rows separated by `gray.1` hairlines.
Right-align an `ACCIÓNS` column. **Never remove rows to represent a disabled/inactive
state** — dim them (muted avatar + dimmed text) and keep them present. A dimmed count
caption sits under the table (e.g. "6 contas · 4 activadas · 2 desactivadas").

### Badges
`Badge variant="light"` in the semantic colour: `green` = enabled/healthy, `gray` =
disabled/inert, `indigo` = a privileged role (ADMINISTRADOR). Text is uppercase and
letter-spaced. A disabled account's badge is **grey, never red** — it is inert, not an
error.

### Buttons & actions
Primary action = filled indigo `Button` (with a Tabler leading icon where it aids
scanning, e.g. `IconPlus` on "Novo usuario"). Secondary = `variant="default"` (outline).
A blocked action (e.g. disabling the last active admin) is a **disabled** button with a
lock affordance and a tooltip explaining why — never hidden.

### Forms & dialogs
Create/edit flows are a Mantine `Modal` (`radius="md"`) over the list, with a titled
header, a hairline divider, stacked fields, and a footer of `Cancelar`
(`variant="default"`) + a filled primary. Required fields carry a **red `*`**. Prefer
`@mantine/form` for validation; surface server-side errors (e.g. uniqueness) as field
errors at submit time.

**Never ask for a secret the server should mint.** The create-user dialog takes email and
role only. A value the reader gets exactly once is revealed *after* the write, in the same
modal, as a read-only `TextInput` with a `CopyButton` and a yellow `Alert` warning it will
not be shown again — not as a field they fill in, and not on a page they might navigate
away from. Reopening the dialog offers a fresh form, never the value again. See
`CreateUserModal.tsx`, whose two states are what `create-user.svg` and the shipped reveal
render.

### Icons & captions
Contextual helper text uses a small dimmed Tabler icon (`IconInfoCircle`, `IconLock`)
followed by dimmed `Text size="sm"`. Keep captions to one line where possible.

## Status & semantics

- **Healthy/enabled → green; disabled/inert → grey; destructive/required → red.** Do
  not use red for merely-inactive state. Red also marks a **measured value that has crossed
  an alert threshold** — a fault the reader should act on, which is not the same as an inert
  one.
- **Live data that has gone stale is `yellow`, not red or grey.** A stream that dropped is
  reconnecting on its own, so the panel is **never** hidden or replaced by an error state:
  keep the last known values on screen, dim them, and caption when they arrived
  ("Última mostra ás 12:45:07 · hai 18 s"). Grey would read as inert and red as broken; the
  data is neither. Carry the meaning in the badge's **text** as well as its colour.
- **Band a rate whose health isn't obvious from the number** (SPEC-0003 R22) — never badge a
  computed rate unconditionally green, or a 100 % error rate reads as "NORMAL". The spec asks
  for normal-vs-concerning; **the cut-offs are this file's call, not the spec's.** The HTTP
  error rate is the one in service: `normal` under 1 % (green), `elevated` 1–5 % (yellow),
  `high` at or above 5 % (red), in `metricsFormat.ts#errorRateSeverity`, pinned by
  `metricsFormat.test.ts`. Move them here, not in a spec amendment.
- **Error *pages* are not red either.** The 403 and 5xx pages render their glyph, code
  and actions in calm **indigo** — a denied route or a transient fault is a system state,
  not something the reader broke. The one exception is the login form's generic failure
  alert (`red.0`/`red.3` tint, `red.9` text), which is a validation error the reader must
  act on.
- Convey liveness for status data (a "Comprobado o …" timestamp + a refresh affordance)
  so a reader can tell fresh from stale.
- Never surface secrets: no credentials, connection strings, tokens, or raw passwords in
  any screen — state so explicitly where a reader might expect them (see `dashboard.svg`).

## Accessibility & i18n

- Every interactive control has an accessible name (`aria-label` on icon-only controls,
  as `AppLayout` does for the burger). Nav landmarks use `<nav aria-label>`.
- Content must not overflow horizontally on narrow viewports — rely on Mantine
  responsive props (`SimpleGrid cols`, `visibleFrom`/`hiddenFrom`, `Table` scroll
  container), never fixed widths that force a horizontal scroll on the page body.
- All copy comes from `strings.ts` (Galician). Tests assert against `strings`, not
  literals — keep new copy there so tests stay in sync.

## Before you finish

- [ ] New user-facing text added to `strings.ts` (Galician), not inlined.
- [ ] Colours/spacing/radius via theme tokens & Mantine props — no stray hex or ad-hoc CSS.
- [ ] Icons from `@tabler/icons-react`; a `ui/` screen sits inside the existing `AppShell`.
- [ ] Status colours follow the semantics (green/grey/yellow/red) above; a computed
      rate is banded, never badged unconditionally green.
- [ ] A decorative chart is `inert aria-hidden`, with its value readable above it.
- [ ] Inactive records are dimmed, not removed; blocked actions disabled with a reason, not hidden.
- [ ] Icon-only controls have `aria-label`; layout stays within the viewport at `sm`.
- [ ] From `ui/`: `npm run lint`, `npm run build`, `npm run test` pass.

<!-- distilled-from: FEAT-0002 @ 6d8a9f4 -->
<!-- distilled-from: FEAT-0004 @ 7402d8a -->
<!-- distilled-from: FEAT-0005 @ 525bdaa -->
