---
status: draft
---

# SPEC-0004. Import and manage Órganos de Contratación

## Summary

The system builds and maintains its own catalogue of the **Órganos de Contratación**
(contracting bodies) of the Xunta de Galicia by importing the list published by the
official source, [contratosdegalicia.gal](https://www.contratosdegalicia.gal/portada.jsp),
and storing it. The source list is flat — each entry is a name — so the system also
lets administrators impose structure on it: they organise
the Órganos into a **multilevel taxonomy** of categories they define (for example, by
administration level and sector) and view the result as a navigable tree.

The import keeps the catalogue current: an administrator can run it on demand, and it
also runs automatically on a recurring schedule. Re-importing reconciles against what is
already stored so that administrators' classification work is preserved across runs and
Órganos are never lost. The catalogue is the system's reference set of contracting
bodies: every authenticated user reads it and browses its taxonomy tree to pick an
Órgano when querying contracts, while only administrators import and organise it.

Access follows that split — **managing** the catalogue and taxonomy is `ADMIN`-only,
while **reading** the catalogue and browsing its tree is available to any authenticated
user — consistent with the roles of
[SPEC-0002](SPEC-0002-user-authentication.md) and the administration area of
[SPEC-0003](SPEC-0003-administration-area.md). This spec describes the *what*; framework,
data model, source-retrieval mechanism, and scheduling technology are decided in ADRs and
features.

## Requirements

### Access

- **R1** — **Managing** the catalogue and taxonomy — triggering imports (R10), creating,
  renaming, moving and deleting taxonomy nodes (R14–R16), and classifying Órganos (R17) —
  is reachable only by users with the `ADMIN` role; a `USER` or an unauthenticated visitor
  who attempts any of these is denied (consistent with SPEC-0003 R1).
- **R2** — **Reading** the catalogue of Órganos (R8) and browsing the taxonomy tree (R9)
  is available to any authenticated user, `USER` or `ADMIN`, because users need Órganos to
  query contracts. These reads grant no ability to modify the catalogue or the taxonomy.
  An unauthenticated visitor is denied.

### Importing the catalogue

- **R3** — The system imports the list of Órganos de Contratación published by the
  official source and stores each entry as a record in its own catalogue, so the
  catalogue is available independently of the source thereafter.
- **R4** — Each stored Órgano carries the attributes the source provides — its name —
  together with a stable identity by which the same Órgano is recognised across
  successive imports, and an active/inactive state (per R6).

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

### Reading and selecting Órganos

- **R8** — Any authenticated user can view the stored catalogue: a list of all Órganos
  showing, for each, its name, its active/inactive state, and its current taxonomy
  placement (or that it is unclassified).
- **R9** — Any authenticated user can browse the taxonomy as a navigable tree of category
  nodes with the Órganos placed within each node, and select an Órgano from it — for
  example, to query contracts by that Órgano. For a `USER` this tree is read-only: it
  offers no controls that create, rename, move, delete, or reassign anything.

### Triggering imports

- **R10** — An administrator can trigger an import on demand and is shown its outcome:
  whether it succeeded and a summary of what changed (for example, how many Órganos were
  added, refreshed, and marked inactive).
- **R11** — The system also runs the import automatically on a recurring schedule, without
  any human trigger.
- **R12** — At most one import runs at a time. A manual trigger issued while an import
  (manual or scheduled) is already in progress does not start a second concurrent run.
- **R13** — An import is resilient to source failure: if the source is unreachable or
  returns an unusable response, the import fails as a whole without corrupting or
  partially clearing the stored catalogue — the previously stored Órganos, their states,
  and their taxonomy remain intact — and the failure is reported to the administrator (for
  a manual run) or otherwise recorded.

### Managing the taxonomy

- **R14** — An administrator can build a **multilevel taxonomy** of category nodes they
  define: create a node with a name, place it at the root or nest it under a parent node,
  rename it, move it to a different parent, and delete it. The taxonomy may be nested to
  any depth.
- **R15** — The taxonomy is a tree: every node has at most one parent and there are no
  cycles — a node cannot be moved to sit under itself or under any of its own descendants.
- **R16** — Deleting a node with child nodes is not allowed until those children are
  removed or moved; deleting a node returns any Órganos assigned directly to it to the
  unclassified set. Deleting a taxonomy node never deletes an Órgano.

### Classifying Órganos

- **R17** — An administrator can assign an Órgano to a single taxonomy node, change that
  assignment to another node, or clear it. An Órgano is placed in **at most one** node at
  any time; it is never in two nodes simultaneously.
- **R18** — Órganos that have not been classified — including every newly imported one —
  are discoverable as an **unclassified** set, so an administrator can find and file them.

## Acceptance criteria

1. **(R1)** A `USER` or an unauthenticated visitor that attempts any management function
   — triggering an import, creating/renaming/moving/deleting a taxonomy node, or
   assigning/clearing an Órgano's node — is denied; an authenticated `ADMIN` is allowed.
2. **(R2)** An authenticated `USER` can read the catalogue and browse the taxonomy tree;
   an unauthenticated visitor that requests either is denied.
3. **(R3, R8)** After an import from the source completes, a user viewing the catalogue
   sees every Órgano from the source list stored with its name.
4. **(R4, R5)** Re-importing after an Órgano's source attributes change updates that
   Órgano in place — its stable identity and its taxonomy placement are unchanged while
   the refreshed attributes are shown.
5. **(R5)** An Órgano that an administrator has placed in a taxonomy node retains that
   placement after a subsequent import.
6. **(R6)** When an Órgano present in an earlier import is absent from a later source
   list, it remains in the catalogue marked inactive and keeps its placement; a still
   later import that includes it again shows it active.
7. **(R7)** Running two imports of the same source list in succession yields the same
   catalogue with no duplicate Órganos and no change to states or placements.
8. **(R8)** The catalogue view shows, for every Órgano, its name, its active/inactive
   state, and its taxonomy placement (or that it is unclassified).
9. **(R9)** A user can browse the taxonomy tree and select an Órgano from it; the tree
   presented to a `USER` offers no control to create, rename, move, delete, or reassign
   anything.
10. **(R10)** After an administrator triggers an import manually, the system reports
    whether it succeeded and a summary of how many Órganos were added, refreshed, and
    marked inactive.
11. **(R11)** With no human trigger, the import runs on its recurring schedule and the
    catalogue reflects the source as of that automatic run.
12. **(R12)** A manual trigger issued while an import is already running does not start a
    second concurrent import.
13. **(R13)** When the source is unreachable or returns an unusable response, the import
    reports failure and the previously stored catalogue, states, and taxonomy are
    unchanged (no partial wipe).
14. **(R14)** An administrator can create a node, nest a node under a parent, rename a
    node, move a node to a different parent, and delete an empty node; a taxonomy nested
    several levels deep is supported.
15. **(R15)** An attempt to move a node under itself or under one of its own descendants
    is rejected and the taxonomy is left unchanged.
16. **(R16)** Deleting a node that has child nodes is rejected; deleting a node with
    directly assigned Órganos returns those Órganos to the unclassified set and deletes no
    Órgano.
17. **(R17)** Assigning an Órgano to a node, then to a different node, leaves it in only
    the second node; clearing its assignment leaves it in none.
18. **(R18)** A newly imported Órgano that has not been classified appears in the
    unclassified set until an administrator assigns it to a node.
