---
feat: FEAT-0007
domain: frontend
adrs: [0003, 0004]
status: todo
depends_on: [TASK-0005]
---

# Taxonomy admin UI

Governed by [ADR-0004](../../architecture/0004-ui-stack-vite-mantine.md) (Vite + Mantine
SPA) and [ADR-0003](../../architecture/0003-react-router-ui-served-by-backend.md). Adds an
`ADMIN`-only **Órganos** section to the existing admin area, consuming
[TASK-0005](TASK-0005-taxonomy-and-classification-rest-endpoints.md)'s endpoints. This is
the only UI in FEAT-0007 — no `USER`-facing browser is built; the tree a user navigates
arrives later as the Órgano filter of the contratos list.

## Scope
- A new route and nav entry inside the admin area, alongside the existing pages, gated the
  same way `AdminRoute` already gates them.
- The taxonomy tree with its management controls: create a node at the root or under a
  parent, rename, move, delete.
- The catalogue with each Órgano's name, active state and placement, an assign-to-node
  action and a clear action, plus the **unclassified** worklist as the filing queue.
- An **import** button calling FEAT-0006's `POST /api/admin/organos/import`, showing the
  returned outcome — added / refreshed / deactivated counts, or "already running" — and
  refreshing the catalogue afterwards.
- Surface the refusals from TASK-0005 as real messages: a move that would create a cycle, a
  delete blocked by child nodes, a stale reference to a node another admin just deleted.
- Galician chrome and copy in `strings.ts`, consistent with the existing admin pages.

## Acceptance criteria
- An `ADMIN` can create a node at the root and under a parent, rename it, move it and
  delete it from the UI, and the tree reflects each change.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #14)
- A move that would create a cycle, and a delete of a node with children, are shown as
  explanatory messages rather than silent no-ops or a generic error.
  (SPEC-0004 #15, #16)
- An `ADMIN` can assign an Órgano to a node, reassign it to another and clear it; the
  Órgano then appears under the expected node, and in the unclassified worklist once
  cleared. (SPEC-0004 #17, #18)
- A newly imported Órgano is visible in the unclassified worklist. (SPEC-0004 #18)
- Triggering an import shows its outcome, including the "already running" case.
  (SPEC-0004 #10)
- The section is not reachable by a `USER`; this gating is cosmetic — `/api/admin/**`
  remains the real gate. (SPEC-0004 #1)
- All added chrome and messages are in Galician. ([SPEC-0001](../../specs/SPEC-0001-web-ui.md) #6)
- Component-tested with a stubbed API for the tree operations, the assign/clear flow, the
  unclassified worklist and the import outcome, including the refusal cases.
