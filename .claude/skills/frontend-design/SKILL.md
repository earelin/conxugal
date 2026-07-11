---
name: frontend-design
description: >-
  Apply the conxugal UI design language when building or changing frontend screens,
  pages, components, forms, tables, or dialogs in the ui/ module. Use whenever you add
  a new page/route or non-trivial component so it reads as part of the same Mantine
  product rather than a bolt-on. Covers layout chrome, design tokens, component
  patterns (cards, stat cards, tables, badges, buttons, forms/modals), status
  semantics, and Galician copy. Grounded in the FEAT-0004 visual design.
---

# conxugal frontend design

Build every screen so it reads as one product. The design language is **Mantine v9
with the project theme** — do not invent new colours, spacings, radii, or bespoke CSS
when a theme token or Mantine prop already expresses it. The FEAT-0004 mockups
(`docs/features/FEAT-0004-administration-area/design/`) are the canonical reference for
what "coherent" looks like here; mirror their structure.

## Sources of truth — read these first

Never hardcode what these modules already own:

- **`ui/src/theme.ts`** — the Mantine theme: `primaryColor: 'indigo'`,
  `defaultRadius: 'md'`, the system font stack, headings at weight 600. Use theme
  tokens (`c="dimmed"`, `radius="md"`, colour names like `green`, `red`), not literal
  hex. If a token is missing, add it to the theme rather than inlining a value.
- **`ui/src/strings.ts`** — all user-facing copy, in **Galician**. Add new copy here
  under a per-screen key; never write literal user-facing text in a component.
- **`ui/src/nav.ts` + `ui/src/layout/AppLayout.tsx`** — the persistent `AppShell`
  chrome (header + collapsible navbar). New sections extend this data/layout; they do
  not fork it.
- **`@tabler/icons-react`** — the icon set. Use it for every icon; never hand-roll SVG
  paths in components (the mockups draw raw SVG only because they are static artifacts).

## Design tokens

| Role | Token | Notes |
| --- | --- | --- |
| Primary | `indigo` (`indigo.6` `#4c6ef5`) | filled primary buttons, active nav, avatars |
| Active nav bg / soft accent | `indigo.0` `#edf2ff` with `indigo.8`/`indigo.9` text | Mantine `light` variant |
| Radius | `md` (8px) | cards, inputs, buttons, modals — from `defaultRadius` |
| Page background | `gray.0` `#f8f9fa` | surfaces are white |
| Border | `gray.3` `#dee2e6`; hairlines `gray.1` `#f1f3f5` | card borders vs. in-card dividers |
| Body text | `gray.9` `#212529` | headings weight 600 |
| Dimmed text | `gray.6` `#868e96` | subtitles, captions, table headers, meta |
| Healthy / enabled | `green` (`#40c057` dot, `#ebfbee`/`#2b8a3e` badge) | |
| Disabled / inert | `gray` badge | disabled ≠ error; render neutral, never red |
| Required / destructive | `red` `#fa5252` | required-field `*`, destructive emphasis only |

Rule of thumb: **reach for a Mantine prop before a style**. `c="dimmed"`, `fw={600}`,
`gap="sm"`, `mt="md"`, `radius="md"`, `withBorder` — not inline `style={{...}}` or raw
hex. Spacing uses the Mantine scale (`xs/sm/md/lg/xl`), not arbitrary pixels.

## Layout & chrome

- Every authenticated screen lives inside the existing `AppShell` from `AppLayout.tsx`.
  Header carries product name + tagline (+ signed-in user where relevant); the navbar
  collapses behind the burger below `sm`. Do not add a second shell or a page-level
  sidebar.
- **Grouped nav sections** (e.g. `ADMINISTRACIÓN`) use an uppercase, letter-spaced,
  dimmed section label above the links. The active item uses the `light`/filled active
  state (indigo). Gate section visibility by role in the navbar for affordance only —
  the **server is the real gate**; never treat a hidden link as security.
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
(`variant="default"`) + a filled primary. Required fields carry a **red `*`**. Sensitive
fields (passwords) get a dimmed helper note stating the handling
("Gárdase cifrado; nunca se mostra…"). Prefer `@mantine/form` for validation; surface
server-side errors (e.g. uniqueness) as field errors at submit time.

### Icons & captions
Contextual helper text uses a small dimmed Tabler icon (`IconInfoCircle`, `IconLock`)
followed by dimmed `Text size="sm"`. Keep captions to one line where possible.

## Status & semantics

- **Healthy/enabled → green; disabled/inert → grey; destructive/required → red.** Do
  not use red for merely-inactive state.
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
- [ ] Icons from `@tabler/icons-react`; screen sits inside the existing `AppShell`.
- [ ] Status colours follow the semantics (green/grey/red) above.
- [ ] Inactive records are dimmed, not removed; blocked actions disabled with a reason, not hidden.
- [ ] Icon-only controls have `aria-label`; layout stays within the viewport at `sm`.
- [ ] From `ui/`: `npm run lint`, `npm run build`, `npm run test` pass.
