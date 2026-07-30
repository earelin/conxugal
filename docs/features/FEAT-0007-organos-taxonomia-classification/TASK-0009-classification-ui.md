---
feat: FEAT-0007
domain: frontend
adrs: [0003, 0004, 0015]
status: todo
depends_on: [TASK-0006, TASK-0007]
---

# Classification UI

Filing Órganos into the taxonomy: the assign picker, the clear action, and the unclassified
worklist as a working queue. Built on
[TASK-0007](TASK-0007-organos-section-and-tree-view.md)'s section and builder, against the
two classification endpoints of
[TASK-0006](TASK-0006-taxonomia-admin-endpoints.md). Independent of
[TASK-0008](TASK-0008-taxonomia-management-ui.md) — the two touch different controls and can
be picked up in either order, because everything they share (the `ProblemError` reader, the
refetch hook, the section chrome, the worklist itself) is TASK-0007's.

Visual target: [`design/assign-organo.svg`](design/assign-organo.svg) and the worklist in
[`design/unclassified-worklist.svg`](design/unclassified-worklist.svg).

## Scope
- `Asignar a un termo` — a searchable tree picker, reachable from a worklist row and from a
  term's `Asignar órgano` action. The dialog always **replaces** a placement, never adds
  one: an already-classified Órgano shows its current term, and confirming moves it.
- `Quitar do termo` on an Órgano row in a term's table — clears the placement and returns the
  Órgano to the worklist.
- The **actions on** the **Sen clasificar** worklist. TASK-0007 already renders it — the
  null-`termoId` slice of the catalogue it fetched, never a separate call or an
  admin-only endpoint. What this task adds is the per-row assign action and the guarantee
  that the queue stays in step: an Órgano leaves it the moment it is filed, and rejoins it
  when cleared.
- After an assign or a clear, refetch the catalogue and re-run the builder. The taxonomy read
  is untouched by either, so it is not refetched.
- Refusals by problem `type`: `organo-not-found` and `termo-not-found` (the target
  term was deleted by another admin) each get their own message and a refresh path.
- Inactive Órganos are dimmed but assignable — going inactive does not unfile an Órgano, and
  the existing table convention already dims rather than hides them.
- All copy in Galician, in the slice's strings.

## Acceptance criteria
- An `ADMIN` can assign an Órgano from the worklist to a term; it leaves the worklist and
  appears under that term in the same refresh.
  ([SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md) #17, #18)
- Reassigning an already-classified Órgano moves it: it appears under the new term and
  under **no** other — the picker offers no way to place it in two. (SPEC-0004 #17)
- Clearing a placement returns the Órgano to the worklist, and it is not deleted.
  (SPEC-0004 #18)
- Assigning to a term another admin has just deleted shows its own message and recovers on
  refresh. (Feature *Failure contract*; no SPEC-0004 criterion covers concurrent admin
  edits.)
- An inactive Órgano is visibly dimmed — its state is part of the catalogue view
  (SPEC-0004 #8) — and can **still** be assigned and cleared: going inactive does not unfile
  an Órgano, which is this feature's decision rather than a spec criterion.
- All added copy is in Galician. ([SPEC-0001](../../specs/SPEC-0001-web-ui.md) AC7)
- Component-tested for assign, reassign, clear, the worklist contents and each refusal, with
  HTTP mocked with `nock` as the existing admin page tests do.
