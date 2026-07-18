---
status: draft
---

# SPEC-0004. Import and manage Órganos de Contratación

## Summary

The system builds and maintains its own catalogue of the **Órganos de Contratación**
(contracting bodies) of the Xunta de Galicia by importing the list published by the
official source, [contratosdegalicia.gal](https://www.contratosdegalicia.gal/portada.jsp),
and storing it. The source list is flat — each entry is a name, sometimes with an
acronym — so the system also lets administrators impose structure on it: they organise
the Órganos into a **multilevel taxonomy** of categories they define (for example, by
administration level and sector) and view the result as a navigable tree.

The import keeps the catalogue current: an administrator can run it on demand, and it
also runs automatically on a recurring schedule. Re-importing reconciles against what is
already stored so that administrators' classification work is preserved across runs and
Órganos are never lost. This capability serves administrators of the system; it produces
the reference catalogue that other parts of the system (contract browsing, filtering,
reporting) build on.

Access to every function described here is `ADMIN`-only, consistent with the
administration area of [SPEC-0003](SPEC-0003-administration-area.md) and the roles of
[SPEC-0002](SPEC-0002-user-authentication.md). This spec describes the *what*; framework,
data model, source-retrieval mechanism, and scheduling technology are decided in ADRs and
features.

## Requirements

### Access

- **R1** — Every function in this spec — triggering imports, viewing the catalogue,
  managing the taxonomy, and classifying Órganos — is reachable only by users with the
  `ADMIN` role; a `USER` or an unauthenticated visitor who requests any of them is denied
  (consistent with SPEC-0003 R1).

### Importing the catalogue

- **R2** — The system imports the list of Órganos de Contratación published by the
  official source and stores each entry as a record in its own catalogue, so the
  catalogue is available independently of the source thereafter.
- **R3** — Each stored Órgano carries the attributes the source provides — its name and,
  when present, its acronym — together with a stable identity by which the same Órgano is
  recognised across successive imports, and an active/inactive state (per R6).
- **R4** — An administrator can view the stored catalogue: a list of all Órganos showing,
  for each, its name, its acronym (when present), its active/inactive state, and its
  current taxonomy placement (or that it is unclassified).

### Identity and reconciliation

- **R5** — A re-import reconciles against the stored catalogue rather than replacing it:
  an Órgano new to the source is added; an Órgano already stored is matched by its stable
  identity and its source-derived attributes are refreshed. Matching an existing Órgano
  never changes or discards its taxonomy placement.
- **R6** — An Órgano that was imported previously but is absent from the latest source
  list is retained and marked **inactive**; it keeps its taxonomy placement and is never
  deleted. If it reappears in a later import it is returned to **active**.
- **R7** — Importing is idempotent: importing the same source list twice in succession
  leaves the set of stored Órganos, their identities, their active/inactive states, and
  their taxonomy placements unchanged, and creates no duplicates.

### Triggering imports

- **R8** — An administrator can trigger an import on demand and is shown its outcome:
  whether it succeeded and a summary of what changed (for example, how many Órganos were
  added, refreshed, and marked inactive).
- **R9** — The system also runs the import automatically on a recurring schedule, without
  any human trigger.
- **R10** — At most one import runs at a time. A manual trigger issued while an import
  (manual or scheduled) is already in progress does not start a second concurrent run.
- **R11** — An import is resilient to source failure: if the source is unreachable or
  returns an unusable response, the import fails as a whole without corrupting or
  partially clearing the stored catalogue — the previously stored Órganos, their states,
  and their taxonomy remain intact — and the failure is reported to the administrator (for
  a manual run) or otherwise recorded.

### Managing the taxonomy

- **R12** — An administrator can build a **multilevel taxonomy** of category nodes they
  define: create a node with a name, place it at the root or nest it under a parent node,
  rename it, move it to a different parent, and delete it. The taxonomy may be nested to
  any depth.
- **R13** — The taxonomy is a tree: every node has at most one parent and there are no
  cycles — a node cannot be moved to sit under itself or under any of its own descendants.
- **R14** — The administration area presents a section that shows the taxonomy as a
  navigable tree of category nodes with the Órganos placed within each node.
- **R15** — Deleting a node with child nodes is not allowed until those children are
  removed or moved; deleting a node returns any Órganos assigned directly to it to the
  unclassified set. Deleting a taxonomy node never deletes an Órgano.

### Classifying Órganos

- **R16** — An administrator can assign an Órgano to a single taxonomy node, change that
  assignment to another node, or clear it. An Órgano is placed in **at most one** node at
  any time; it is never in two nodes simultaneously.
- **R17** — Órganos that have not been classified — including every newly imported one —
  are discoverable as an **unclassified** set, so an administrator can find and file them.

## Acceptance criteria

1. **(R1)** An authenticated `USER` or an unauthenticated visitor that requests any
   function of this spec is denied; an authenticated `ADMIN` is allowed.
2. **(R2, R4)** After an import from the source completes, an administrator viewing the
   catalogue sees every Órgano from the source list stored with its name and acronym.
3. **(R3, R5)** Re-importing after an Órgano's source attributes change updates that
   Órgano in place — its stable identity and its taxonomy placement are unchanged while
   the refreshed attributes are shown.
4. **(R5)** An Órgano that an administrator has placed in a taxonomy node retains that
   placement after a subsequent import.
5. **(R6)** When an Órgano present in an earlier import is absent from a later source
   list, it remains in the catalogue marked inactive and keeps its placement; a still
   later import that includes it again shows it active.
6. **(R7)** Running two imports of the same source list in succession yields the same
   catalogue with no duplicate Órganos and no change to states or placements.
7. **(R8)** After an administrator triggers an import manually, the system reports whether
   it succeeded and a summary of how many Órganos were added, refreshed, and marked
   inactive.
8. **(R9)** With no human trigger, the import runs on its recurring schedule and the
   catalogue reflects the source as of that automatic run.
9. **(R10)** A manual trigger issued while an import is already running does not start a
   second concurrent import.
10. **(R11)** When the source is unreachable or returns an unusable response, the import
    reports failure and the previously stored catalogue, states, and taxonomy are
    unchanged (no partial wipe).
11. **(R12)** An administrator can create a node, nest a node under a parent, rename a
    node, move a node to a different parent, and delete an empty node; a taxonomy nested
    several levels deep is supported.
12. **(R13)** An attempt to move a node under itself or under one of its own descendants
    is rejected and the taxonomy is left unchanged.
13. **(R14)** The administration area shows a tree view of the taxonomy with each category
    node and the Órganos assigned within it.
14. **(R15)** Deleting a node that has child nodes is rejected; deleting a node with
    directly assigned Órganos returns those Órganos to the unclassified set and deletes no
    Órgano.
15. **(R16)** Assigning an Órgano to a node, then to a different node, leaves it in only
    the second node; clearing its assignment leaves it in none.
16. **(R17)** A newly imported Órgano that has not been classified appears in the
    unclassified set until an administrator assigns it to a node.
