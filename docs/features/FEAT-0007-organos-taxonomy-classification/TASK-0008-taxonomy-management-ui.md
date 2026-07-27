---
feat: FEAT-0007
domain: frontend
adrs: [0003, 0004, 0015]
status: todo
depends_on: [TASK-0006, TASK-0007]
---

# Taxonomy management UI

The four write actions on the tree — create, rename, move, delete — added to the read-only
section built by [TASK-0007](TASK-0007-organos-section-and-tree-view.md), against the
`ADMIN` endpoints of [TASK-0006](TASK-0006-taxonomy-admin-endpoints.md). Governed by
[ADR-0004](../../architecture/0004-ui-stack-vite-mantine.md),
[ADR-0003](../../architecture/0003-react-router-ui-served-by-backend.md) and
[ADR-0015](../../architecture/0015-frontend-feature-based-shared-core-modularization.md).

Visual target: [`design/create-node.svg`](design/create-node.svg),
[`design/move-node.svg`](design/move-node.svg),
[`design/delete-node.svg`](design/delete-node.svg), plus the selected-node action row in
[`design/taxonomy-admin.svg`](design/taxonomy-admin.svg).

## Scope
- `Novo nodo` in the tree header, and inline **rename / move / delete** actions on the
  selected tree row — each a Mantine `Modal`, as the mockups show. Both fill action areas
  [TASK-0007](TASK-0007-organos-section-and-tree-view.md) already rendered empty; this task
  adds no chrome of its own.
- Refusal messages are keyed on the problem `type` via TASK-0007's `ProblemError`, and
  refetching after a mutation goes through TASK-0007's hook. Neither is built here.
- Move offers the whole tree as a target plus an explicit "at the root" option — a move to
  the root is a legitimate destination, not the absence of one. It is **its own component**,
  not shared with [TASK-0009](TASK-0009-classification-ui.md)'s assign picker: that one is
  searchable, offers no root and always replaces. Two controls by design.
- **Selecting an invalid target explains, it does not hide.** Picking the node itself or one
  of its descendants is *selectable*, and doing so renders the cycle alert naming both nodes
  with the primary action disabled — the behaviour `design/move-node.svg` shows. Filtering
  those targets out of the list instead would make the refusal unreachable through the UI,
  and the admin would be left guessing why an obvious destination is missing.
- After a mutation, refetch the taxonomy read and re-run TASK-0007's builder; there is no
  server-assembled shape to re-request. A delete also refetches the catalogue, since it
  returns Órganos to unclassified. A create selects and expands the new node using the id
  its 201 returns, without a second round trip.
- **Surface each refusal as its own message**, keyed on the problem `type` from TASK-0006,
  never on the status: a cycle and a blocked-by-children delete are both 409, and telling an
  admin "conflict" for either is the failure this task exists to avoid.
  - `taxonomy-cycle` → a node cannot be moved under itself or its own descendant.
  - `taxonomy-node-has-children` → move or delete the child nodes first.
  - `duplicate-sibling-name` → a sibling already has that name.
  - `taxonomy-node-not-found` → the node was deleted by someone else; refresh.
- Client-side, mirror only the validation that needs no tree knowledge: a blank or
  over-length name is rejected in the form before submitting. The server remains the
  authority for every rule.
- All copy in Galician, in the slice's strings.

## Acceptance criteria
- An `ADMIN` can create a node at the root and under a parent, rename it, move it to
  another parent and back to the root, and delete an empty one — the tree reflects each
  change without a manual reload.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #14)
- A move that would create a cycle and a delete of a node with children each render their
  **own** explanatory message — visibly different from each other, not a shared generic
  error — and the tree is unchanged. (SPEC-0004 #15, #16)
- A duplicate sibling name is refused with its own message, and the dialog stays open with
  the entered name intact so the admin can correct it rather than retype it.
- Selecting the node itself, or a descendant, as a move target is possible and produces the
  cycle explanation with the confirm disabled — the refusal is reachable through the UI
  rather than filtered out of existence. (SPEC-0004 #15)
- Acting on a node another admin has just deleted shows the stale-reference message and
  recovers on refresh, rather than leaving the section broken. (Feature *Failure contract*;
  no SPEC-0004 criterion covers concurrent admin edits.)
- Deleting a node that holds Órganos returns them to the unclassified worklist in the same
  refresh, and none disappears. (SPEC-0004 #16)
- All added copy is in Galician. ([SPEC-0001](../../specs/SPEC-0001-web-ui.md) AC7)
- Component-tested for each flow and each refusal, with HTTP mocked at the network boundary
  with `nock` as the existing admin page tests do. Each refusal test asserts the **message**,
  not the status code — a test that only checks "an error appeared" would pass against the
  generic-error bug this task rules out.
