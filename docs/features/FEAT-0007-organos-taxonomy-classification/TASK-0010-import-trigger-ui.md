---
feat: FEAT-0007
domain: frontend
adrs: [0003, 0004, 0015]
status: todo
depends_on: [TASK-0007]
---

# Import trigger UI

The one control in this feature that drives a **FEAT-0006** endpoint: an import button in
the Órganos section toolbar, and the outcome it reports. Built on
[TASK-0007](TASK-0007-organos-section-and-tree-view.md)'s section; independent of
[TASK-0008](TASK-0008-taxonomy-management-ui.md) and
[TASK-0009](TASK-0009-classification-ui.md). Governed by
[ADR-0004](../../architecture/0004-ui-stack-vite-mantine.md),
[ADR-0003](../../architecture/0003-react-router-ui-served-by-backend.md) and
[ADR-0015](../../architecture/0015-frontend-feature-based-shared-core-modularization.md).

Visual target: the toolbar button and success banner in
[`design/unclassified-worklist.svg`](design/unclassified-worklist.svg), including its
annotated "already running" state.

## Scope
- An **Importar** button in the section toolbar calling
  [FEAT-0006](../FEAT-0006-organos-catalogue-import/README.md)'s
  `POST /api/admin/organos/import`.
- Show the returned outcome: the added / refreshed / deactivated counts on success, and the
  distinct **"already running"** case — which is a normal answer, not an error, and must not
  be rendered as one.
- Disable the button and show progress while a request is in flight, so the obvious
  double-click does not queue a second import.
- Refresh the catalogue read afterwards and re-run the builder, so newly imported Órganos
  land in the unclassified worklist without a manual reload. The taxonomy is untouched by an
  import, so it is not refetched.
- A failed import shows the shared `ErrorAlert` with a retry, distinct from both the success
  banner and the already-running notice.
- **No persistent "última importación" caption.** The mockups show one; it cannot be built
  against any contract this feature or FEAT-0006 delivers, since no endpoint reports the
  last import's timestamp or counts, and SPEC-0004 does not require it — R10 asks that the
  outcome be reported *after an administrator triggers an import*, which the banner does. The
  caption is a design-only element, recorded as such in the mockup README; adding the read
  behind it would be a FEAT-0006 change with its own requirement.
- All copy in Galician, in the slice's strings.

## Acceptance criteria
- An `ADMIN` triggering an import sees the outcome — added, refreshed and deactivated counts
  — reported in the section.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #10)
- The **"already running"** response is shown as its own informational message, visibly
  distinct from both a success and a failure. (SPEC-0004 #10)
- A failed import shows an error with a retry, and the section's existing data stays on
  screen rather than being replaced by an error state.
- The button is disabled while the request is in flight.
- After a successful import, newly added Órganos appear in the unclassified worklist without
  a manual reload. (SPEC-0004 #18)
- All added copy is in Galician. ([SPEC-0001](../../specs/SPEC-0001-web-ui.md) #6)
- Component-tested for the success, already-running, failure and in-flight states, with HTTP
  mocked with `nock` as the existing admin page tests do.
