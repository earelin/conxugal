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
