---
status: active
---

# SPEC-0001. Web UI for browsing, searching and exporting contracts

## Capability
Users need a web interface to make the imported public-contract information of the
Xunta de Galicia (sourced from contratosdegalicia.gal) **accessible and analysable**,
as stated in the project [README](../../README.md). The UI lets a user reach the system in a browser,
find contracts, inspect their detail, and export results — all over a single origin.

This spec describes the *what*. It is independent of framework, component library and
build tooling (those are recorded in ADRs and detailed in features).

## Requirements

### R1 — Reachable application shell
The system presents a web application a user can open in a browser. It has a persistent
shell (identifiable application name/branding and primary navigation) that frames every
screen.

### R2 — Navigation between sections
A user can move between the application's main sections using in-app navigation, and the
browser address reflects the current section (shareable, bookmarkable, back/forward work).

### R3 — Unknown locations are handled
Opening an address that does not correspond to any section shows a clear "not found"
state from within the application shell, not a server or browser error.

### R4 — Consistent, accessible presentation
The UI applies one consistent visual theme across all screens. Interactive elements are
keyboard-operable and carry accessible names and semantics.

### R5 — Readable on common viewports
The shell and its content remain usable on a typical desktop browser width and degrade
gracefully on a narrow (mobile) width without horizontal overflow of primary content.

### R6 — Galician as the interface language
User-facing text of the interface chrome (navigation, shell, not-found state) is in
Galician, consistent with the project and its data source.

> Browsing/searching/filtering the contract dataset, contract detail views, and export
> are capabilities of this same web UI and will be specced/featured incrementally on top
> of this shell. This spec establishes the reachable, navigable, themed shell they live in.

## Acceptance criteria

- **AC1 (R1):** Loading the application's root address renders an application shell
  showing the product name and a primary navigation region; no blank page or raw error.
- **AC2 (R2):** Activating a primary navigation item changes the displayed section and
  updates the browser address bar to a distinct path; reloading that path returns to the
  same section.
- **AC3 (R2):** Browser Back, after navigating between two sections, returns to the
  previously shown section.
- **AC4 (R3):** Navigating to an address with no matching section displays an in-shell
  "page not found" message in Galician and a way back to a known section.
- **AC5 (R4):** Every screen renders with the same theme (colors/typography) applied;
  the primary navigation is reachable and operable using only the keyboard (Tab/Enter).
- **AC6 (R5):** At a 360 px-wide viewport (≈ the smallest common mobile viewport width),
  the shell's primary content has no horizontal scrollbar and navigation remains reachable.
- **AC7 (R6):** The navigation labels and the not-found message are presented in Galician.

## Implemented by

- **FEAT-0001** — UI application scaffolding (retired 2026-08-23, commit `3f17cc0`)
  - Decisions: [ADR-0003](../architecture/0003-react-router-ui-served-by-backend.md)
    (React Router served by the backend),
    [ADR-0004](../architecture/0004-ui-stack-vite-mantine.md) (Vite build, library-mode
    SPA, Mantine, npm)
  - System: [`ui/README.md`](../../ui/README.md) and [`ui/CLAUDE.md`](../../ui/CLAUDE.md)
    — the `ui/` module's structure, routing, theme, i18n seam and scripts as built
  - Behaviour: `ui/src/App.test.tsx` covers AC1 (shell with product name and primary
    navigation) and AC4 (in-shell Galician not-found state)
  - Production deep-linking (AC2/AC4 outside the dev server) is closed by FEAT-0003
    below
- **FEAT-0003** — Backend serves the UI application (retired 2026-08-23, commit `73cf32f`)
  - Decisions: [ADR-0003](../architecture/0003-react-router-ui-served-by-backend.md)
    (the backend is the single origin for both the API and the built UI),
    [ADR-0006](../architecture/0006-reserved-api-url-prefix.md) (the reserved `/api/`
    prefix that lets the fallback tell a missing endpoint from a client-side route),
    [ADR-0007](../architecture/0007-acceptance-testing-module.md) (Playwright, scoped to
    the served UI)
  - System: [`server/CLAUDE.md`](../../server/CLAUDE.md) — the UI build inside the server
    build, and the routing/fallback matrix as built;
    [`server/README.md`](../../server/README.md) — what the server build needs and what a
    request without a session gets
  - Behaviour: `application`'s `SpaHistoryFallbackTest` (7 scenarios) and `acceptance`'s
    browser-driven `AuthenticatedSpaRoutingTest` together close AC2 and AC4 against a
    production build — the latter's `root_serves_the_spa_shell_with_its_built_assets`
    fetches every asset the served page references. `ApiUrlPrefixArchTest` enforces
    ADR-0006 at build time.

<!-- distilled-from: FEAT-0001 @ 3f17cc0 -->
<!-- distilled-from: FEAT-0003 @ 73cf32f -->
