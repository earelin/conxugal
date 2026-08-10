---
status: active
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
bodies: every authenticated user browses its **taxonomy tree**, or **searches it by name**,
to pick an Órgano when querying contracts, while only administrators import, organise, and
list it.

Access follows that split — **managing** the catalogue and taxonomy, and viewing it as a
flat list, are `ADMIN`-only, while **browsing the taxonomy tree** and **searching by name**
are available to any authenticated user — consistent with the roles of
[SPEC-0002](SPEC-0002-user-authentication.md) and the administration area of
[SPEC-0003](SPEC-0003-administration-area.md). This spec describes the *what*; framework,
data model, source-retrieval mechanism, and scheduling technology are decided in ADRs and
features.

**The tree is a `USER`'s only view of the catalogue**, and what it shows them is not the
whole of it. Two rules shape it, and they pull in opposite directions on purpose:

- **It shows only Órganos the system holds contracts for.** A `USER` comes to the
  catalogue to reach contracts, and most organismos publish none the system imports, so an
  Órgano with nothing behind it is an entry that can only lead to an empty page.
  Administrators see the whole catalogue; a `USER` sees the part of it that answers
  something.
- **Within that set it is exhaustive.** An Órgano an administrator has not classified is
  **shown at the root of the tree**, alongside the root terms, rather than being absent
  from it — so classification organises what is reachable without ever gating it, and an
  administrator's filing backlog is never a `USER`'s missing data.

Together they make a `USER`'s visible set **exactly** the Órganos whose contracts the
system holds — which is the same set
[SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) R14 requires to be reachable,
stated from the other side.

## Requirements

### Access

- **R1** — **Managing** the catalogue and taxonomy — triggering imports (R10), creating,
  renaming, moving and deleting terms (R14–R16), and classifying Órganos (R17) — **and
  viewing the catalogue as a flat list (R8)** is reachable only by users with the `ADMIN`
  role; a `USER` or an unauthenticated visitor who attempts any of these is denied
  (consistent with SPEC-0003 R1).
- **R2** — **Browsing the taxonomy tree (R9)** and **searching for an Órgano by name
  (R19)** are available to any authenticated user, `USER` or `ADMIN`, because users need
  Órganos to query contracts. Neither grants any ability to modify the catalogue or the
  taxonomy. An unauthenticated visitor is denied.

  **Those two are the whole of a `USER`'s access to the catalogue.** There is no
  `USER`-facing list of it. The distinction the two share, and the reason a search does not
  reintroduce the list, is that **neither ever presents the catalogue undifferentiated**:
  the tree presents it through the structure an administrator gave it, and the search
  presents only what a user asked for by name. An administrator's flat view (R8) exists for
  the filing work R18 describes, not as an alternative way in.

  The tree carries the burden of **discovery** — finding an Órgano whose name you do not
  know — which is why R9 requires it to hold every Órgano a `USER` may see, classified or
  not. The search carries the burden of **speed** for a user who already knows the name,
  over a catalogue of several hundred entries where browsing to a known target is slower
  than typing it.

  **Both are scoped to the Órganos the system holds contracts for** (R9). A `USER`'s view
  of the catalogue is not the catalogue: it is the part of it that can answer the question
  they came with.

### Importing the catalogue

- **R3** — The system imports the list of Órganos de Contratación published by the
  official source and stores each entry as a record in its own catalogue, so the
  catalogue is available independently of the source thereafter.
- **R4** — Each stored Órgano carries the attributes the source provides — its name —
  together with a stable identity by which the same Órgano is recognised across
  successive imports, and an active/inactive state (per R6).
  > [SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) R4 adds one further
  > administrator-managed attribute — whether the Órgano's contracts are imported —
  > which R5 must preserve across re-imports exactly as it preserves taxonomy placement.

### Identity and reconciliation

- **R5** — A re-import reconciles against the stored catalogue rather than replacing it:
  an Órgano new to the source is added and starts **active**; an Órgano already stored
  is matched by its stable identity and its source-derived attributes are refreshed.
  Matching an existing Órgano never changes or discards its taxonomy placement.
- **R6** — An Órgano that was imported previously but is absent from the latest source
  list is retained and marked **inactive**; it keeps its taxonomy placement and is never
  deleted. If it reappears in a later import it is returned to **active**.
  > Becoming inactive has a consequence beyond this spec:
  > [SPEC-0005](SPEC-0005-import-browse-contratos-menores.md)'s rule on stopping retrieval
  > (R5) stops importing that Órgano's contracts and halts any retrieval already running,
  > while the contracts already stored are retained and stay browsable.
- **R7** — Importing is idempotent: importing the same source list twice in succession
  leaves the set of stored Órganos, their identities, their active/inactive states, and
  their taxonomy placements unchanged, and creates no duplicates.

### Reading and selecting Órganos

- **R8** — An **administrator** can view the stored catalogue as a flat list of all
  Órganos showing, for each, its name, its active/inactive state, and its current taxonomy
  placement (or that it is unclassified). This is the administrator's working view of the
  catalogue — what they file from (R18), classify from (R17) and check an import against
  (R10) — and it is **not** a route a `USER` has.
- **R9** — Any authenticated user can browse the taxonomy as a navigable tree of category
  terms with the Órganos placed within each term, and select an Órgano from it — for
  example, to query contracts by that Órgano. For a `USER` this tree is read-only: it
  offers no controls that create, rename, move, delete, or reassign anything.

  **A `USER`'s tree holds exactly the Órganos the system holds contracts for.** An Órgano
  the system holds **no contract data of any family** for is **not shown to a `USER` at
  all** — not in the tree, not in the search (R19), nowhere. An administrator's surfaces
  (R8, and the management tree of R14–R18) are **not** filtered: they show the whole
  catalogue, because classifying and marking Órganos is work done precisely on the ones
  that have nothing yet.

  The reason is R2's own justification: a `USER` reaches Órganos **in order to query
  contracts**, so an Órgano with no contracts is an entry that can only ever lead to an
  empty page. Most of the catalogue is exactly that — the majority of organismos publish no
  contracts the system imports — so showing them all would bury the few that answer a
  question under the many that cannot.

  Three consequences follow, and each is a requirement rather than a rendering detail:

  - **A term with no visible Órgano anywhere beneath it is omitted from a `USER`'s tree.**
    An empty branch is noise for the same reason an empty entry is, and a taxonomy built
    for the whole catalogue would otherwise show mostly empty structure.
  - **An Órgano that sits in no term is shown at the root of the tree**, alongside the root
    terms, and is selectable there exactly as a classified one is within its term. Since
    R18 leaves **every newly imported Órgano unclassified**, a tree that showed only
    classified Órganos would leave an Órgano whose contracts the system does hold reachable
    from nowhere — and, through
    [SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) R14, would leave those
    contracts stored but unreachable. Placing them at the root makes classification a way of
    **organising** what is already reachable rather than a gate on reaching it, so an
    administrator's filing backlog never becomes a `USER`'s missing data.
  - **An Órgano that is inactive (R6) but still holds contracts stays visible** — in its
    term if it has one, at the root if it has not. It keeps its placement, it keeps its
    contracts, and they are still worth reading; *inactive* is a fact about the source
    list, not about whether there is anything there.

  > **This is the one place a requirement here depends on data another spec owns.**
  > SPEC-0004 is consumed by [SPEC-0005](SPEC-0005-import-browse-contratos-menores.md), not
  > the other way round, so the direction is worth naming rather than leaving to be noticed.
  > What is stated here is the **rule** — a `USER` sees the Órganos that have contracts —
  > because it is a rule about who sees what in *this* catalogue. What "has contracts" means
  > is whatever the contract specs store, across **every** family, so this requirement does
  > not have to change when a family is added. It resolves to *the system holds at least one
  > contract for this Órgano that a user could see*, which makes the visible set and
  > SPEC-0005 R14's *every Órgano whose contracts the system holds is reachable* the **same
  > set**, stated from two sides.
- **R19** — Any authenticated user can **find an Órgano by name**: they type part of a
  name and the Órganos whose names match are offered **as they type**, in a list they
  choose from, without submitting a search or leaving the surface they are on. Choosing one
  selects that Órgano exactly as choosing it in the tree does (R9), so the two affordances
  lead to the same place.

  > **Numbered R19 although it belongs beside R9.** Requirement identifiers are cited from
  > other specs and features, so inserting one here would silently repoint every reference
  > to R10–R18. The number follows the last requirement; its place in the document follows
  > its meaning.

  - For a `USER` it searches **exactly what R9's tree shows** — classified or not, active or
    not, but only Órganos the system holds contracts for. The search and the tree must agree
    about what exists, in both directions: an affordance that found **fewer** would be a
    second, quieter answer to *what does this system hold*, and one that found **more** would
    be a way around R9's filter, reachable by anyone who guessed a name. An administrator
    searching the same surfaces searches the whole catalogue, as R8 shows it.
  - Matching is on the **name**, and is **forgiving of case and of accents**: a user typing
    `avila` finds `Ávila`. Galician names are full of accents, and a search that requires
    them to be typed exactly fails precisely the users who know the name best.
  - Each offered entry carries enough to **tell two similar names apart** — the name, and
    whether the Órgano is inactive. Several Órganos differing only by a trailing qualifier
    is the ordinary case in this catalogue, not the exception.
  - When nothing matches, the user is **told so**, rather than shown an empty control that
    is indistinguishable from one that has not searched yet.
  - **An empty or blank input matches nothing and offers nothing.** This is the boundary
    that keeps R19 from becoming the `USER`-facing list R2 removes: the search answers a
    question a user asked, and *"show me everything"* is not one of them. A user who has
    typed nothing has asked nothing.
  - It is a way to **find** an Órgano, not to browse the catalogue: it offers no paging, no
    sorting and no filters, and it never presents itself as a complete view of anything.
    How many matches are offered at once is a design decision, not a requirement.

### Triggering imports

- **R10** — An administrator can trigger an import on demand and is shown its outcome:
  whether it succeeded and a summary of what changed (for example, how many Órganos were
  added, refreshed, and marked inactive).
- **R11** — The system also runs the import automatically on a recurring schedule, without
  any human trigger.
- **R12** — At most one import runs at a time. A manual trigger issued while an import
  (manual or scheduled) is already in progress does not start a second concurrent run.
  The guard is **system-wide, not per importer**: it is the same single-import guard the
  contratos menores spec states
  ([SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) R22), held once across both, so a
  catalogue import and a contract import never run together. Both draw on the same public
  source, and being throttled or blocked by it would cost both alike.
- **R13** — An import is resilient to source failure: if the source is unreachable or
  returns an unusable response, the import fails as a whole without corrupting or
  partially clearing the stored catalogue — the previously stored Órganos, their states,
  and their taxonomy remain intact — and the failure is reported to the administrator (for
  a manual run) or otherwise recorded.
  > *"Otherwise recorded"* is made concrete by
  > [SPEC-0007](SPEC-0007-monitor-import-runs.md), whose requirement that **every run is
  > recorded** (R2) covers the scheduled runs of R11 — the ones with no requester to report
  > to — and whose **diagnostics** requirement (R9) makes the failure debuggable without
  > server logs. This spec states the obligation; that one owns the record and the surface.

### Managing the taxonomy

- **R14** — An administrator can build a **multilevel taxonomy** of category terms they
  define: create a term with a name, place it at the root or nest it under a parent term,
  rename it, move it to a different parent, and delete it. The taxonomy may be nested to
  any depth.
- **R15** — The taxonomy is a tree: every term has at most one parent and there are no
  cycles — a term cannot be moved to sit under itself or under any of its own descendants.
- **R16** — Deleting a term with child terms is not allowed until those children are
  removed or moved; deleting a term returns any Órganos assigned directly to it to the
  unclassified set. Deleting a term never deletes an Órgano.

### Classifying Órganos

- **R17** — An administrator can assign an Órgano to a single term, change that
  assignment to another term, or clear it. An Órgano is placed in **at most one** term at
  any time; it is never in two terms simultaneously.
- **R18** — Órganos that have not been classified — including every newly imported one —
  are discoverable as an **unclassified** set, so an administrator can find and file them.

## Acceptance criteria

1. **(R1)** A `USER` or an unauthenticated visitor that attempts any management function
   — triggering an import, creating/renaming/moving/deleting a term, assigning/clearing an
   Órgano's term, **or viewing the catalogue as a flat list** — is denied; an authenticated
   `ADMIN` is allowed.
2. **(R2)** An authenticated `USER` can browse the taxonomy tree and search for an Órgano
   by name; an unauthenticated visitor that requests either is denied. **No surface offers
   a `USER` the catalogue as a list**, and no route to an Órgano exists for them beyond
   those two.
   > **Partly deferred, with criterion 9 below.** The access-control half — a `USER` may
   > reach both, an anonymous caller is denied — is satisfied by
   > [FEAT-0007](../features/FEAT-0007-organos-taxonomia-classification/README.md)'s two
   > authenticated reads. The *"browse the taxonomy tree"* half is the same unrendered
   > `USER` surface #9 defers, and travels with it to
   > [SPEC-0005](SPEC-0005-import-browse-contratos-menores.md)'s requirement that a user
   > reaches an Órgano's contracts by browsing the tree (R14). A task claiming this
   > criterion should say which half it proves.
3. **(R3, R5, R8)** After an import from the source completes, a user viewing the
   catalogue sees every Órgano from the source list stored with its name; an Órgano new
   to that import is stored **active**.
4. **(R4, R5)** Re-importing after an Órgano's source attributes change updates that
   Órgano in place — its stable identity and its taxonomy placement are unchanged while
   the refreshed attributes are shown.
5. **(R5)** An Órgano that an administrator has placed in a term retains that
   placement after a subsequent import.
6. **(R6)** When an Órgano present in an earlier import is absent from a later source
   list, it remains in the catalogue marked inactive and keeps its placement; a still
   later import that includes it again shows it active.
7. **(R7)** Running two imports of the same source list in succession yields the same
   catalogue with no duplicate Órganos and no change to states or placements.
8. **(R8)** The **administrator's** catalogue view shows, for every Órgano, its name, its
   active/inactive state, and its taxonomy placement (or that it is unclassified).
9. **(R9)** A user can browse the taxonomy tree and select an Órgano from it; the tree
   presented to a `USER` offers no control to create, rename, move, delete, or reassign
   anything.
   > **Deferred, and knowingly outside this spec's features.** The *data* this tree is
   > built from ships with
   > [FEAT-0007](../features/FEAT-0007-organos-taxonomia-classification/README.md) as two
   > authenticated reads, but no `USER`-facing tree is rendered by any feature of this
   > spec. It is rendered by
   > [SPEC-0005](SPEC-0005-import-browse-contratos-menores.md)'s **requirement that a user
   > reaches an Órgano's contracts by browsing the tree** (R14), where browsing the tree and
   > selecting an Órgano is how a user reaches that Órgano's contracts — built against those
   > two contracts, and claimed by that spec's criterion for it (#19). This criterion is therefore
   > satisfied *outside* SPEC-0004, and the spec should not be read as unfulfilled while it
   > waits. Recorded here so the gap is visible from the spec that owns the requirement,
   > not only from the feature that declined it.
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
14. **(R14)** An administrator can create a term, nest a term under a parent, rename a
    term, move a term to a different parent, and delete an empty term; a taxonomy nested
    several levels deep is supported.
15. **(R15)** An attempt to move a term under itself or under one of its own descendants
    is rejected and the taxonomy is left unchanged.
16. **(R16)** Deleting a term that has child terms is rejected; deleting a term with
    directly assigned Órganos returns those Órganos to the unclassified set and deletes no
    Órgano.
17. **(R17)** Assigning an Órgano to a term, then to a different term, leaves it in only
    the second term; clearing its assignment leaves it in none.
18. **(R18)** A newly imported Órgano that has not been classified appears in the
    unclassified set until an administrator assigns it to a term.
19. **(R9)** An Órgano the system holds contracts for that is in **no term** is shown **at
    the root of the taxonomy tree** and is selectable there; after an administrator assigns
    it to a term it is shown within that term and no longer at the root. Every Órgano a
    `USER` may see is therefore reachable from the tree, whether or not it is classified and
    whether or not it is active.
20. **(R9)** An Órgano the system holds **no contract data** for appears **nowhere** in a
    `USER`'s tree or search, while an administrator still sees it in the catalogue list (R8)
    and in the management tree — so the same Órgano is present for one role and absent for
    the other. Once the system stores its first contract, it appears for a `USER` too,
    without an administrator doing anything.
21. **(R9)** A term whose subtree contains no Órgano visible to a `USER` is **omitted from
    their tree**, while a term with at least one is shown, carrying only the visible ones.
    An administrator's management tree still shows both.
22. **(R19)** Typing part of an Órgano's name offers the matching Órganos as the user
    types, without submitting anything; choosing one selects the same Órgano that choosing
    it in the tree would. A query matching nothing says so, and an **empty or blank query
    offers nothing at all** — no surface returns the whole catalogue to a `USER`.
23. **(R19)** A query that differs from the stored name only in **letter case or accents**
    still finds it — typing `avila` offers `Ávila` — and each offered entry states whether
    that Órgano is inactive.
24. **(R9, R19)** For a `USER` the search offers **exactly** the Órganos their tree shows:
    an **unclassified** one and an **inactive** one that hold contracts are both findable,
    and one holding no contracts is findable by neither — so no name can be typed to reach
    what the tree withholds.
