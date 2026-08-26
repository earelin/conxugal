---
status: draft
---

# SPEC-0010. Hierarchical CPV selection

## Summary

[SPEC-0009](SPEC-0009-cpv-catalogue.md) holds the regulated procurement vocabulary and derives the
hierarchy the codes are built on. This spec offers that hierarchy to a reader: instead of choosing
a subject from a flat list of codes, they begin at the broadest subjects and **descend a tree** to
whatever depth they mean, and choosing a node narrows to that code **and every code beneath it**.

It is a separate spec from the catalogue for one reason, stated plainly rather than buried:
**the catalogue is worth building whatever the measurement in R1 says, and the tree is not.** Giving
a bare eight-digit code a Galician name improves the flat list a reader already has. Replacing that
list with a tree is only an improvement if the list is long enough to be hard to use — and how long
it actually is has not been measured. R1 is that measurement, and it is a precondition of this
spec rather than a note attached to it.

Until R1 is taken and comes out the way this spec assumes, **the flat list of codes each family's
browsing spec already offers continues to satisfy that spec**, now with catalogue wording against
each code. Nothing here is owed by any feature before then.

## Scope

- **In scope:** offering the hierarchy as a tree; what a chosen node selects; which nodes are
  offered against a given set of contracts; what each node states about that set; and how a chosen
  node is read back.
- **Out of scope — the vocabulary itself.** Acquiring the list, the wording in any language, the
  derivation of the hierarchy and the treatment of an entry first seen on a contract are all
  [SPEC-0009](SPEC-0009-cpv-catalogue.md)'s. This spec consumes a hierarchy; it does not build one.
- **Out of scope — everything else about a browsing surface.** The reader, authentication, the
  year scoping, the paging control, sorting and every other narrowing belong to the family's own
  browsing spec — [SPEC-0008](SPEC-0008-import-browse-licitacions.md) R22 and R23 for licitacións.
  This spec changes **how a subject is chosen** and nothing else about how a list behaves.
- **Out of scope — which rows carry which code.** R5's "classified at or beneath" composes over
  whatever rule the family states for a contract carrying a classification; it does not restate or
  override it. For licitacións that rule is SPEC-0008 R8 — a classification hanging off a lote or
  off the procedure as a whole.
- **Out of scope — free-text search over the vocabulary.** Typing part of a subject's name to find
  its node is a different capability with a different failure mode, and no requirement here implies
  it.

### What this spec requires of its sibling specs

- **[SPEC-0008](SPEC-0008-import-browse-licitacions.md) R23 and its criterion #33 must be amended
  to consume this spec — and not before R1 is taken.** R23 today offers a flat list of CPV codes,
  and FEAT-0016's tasks are authored against exactly that reading. What the amendment will have to
  say is R5 and R6 below: that a node is offered when a licitación of the selection is classified
  **at or beneath** it, and that choosing one selects everything beneath it.

  ❗ **The amendment must also settle a word R23 leaves ambiguous**, because the tree makes the
  ambiguity load-bearing. R23 offers "only codes and states the year's selection actually
  contains", and *the selection* is read two ways: **the year's rows**, or **the year's rows under
  whatever other narrowing is already in effect**. FEAT-0016 has settled it the first way in
  writing — its facets are a function of the Órgano and the year alone, and it accepts as a stated
  residual that choosing a CPV *and* a state can empty the list. R6 below is written to that same
  reading. If SPEC-0008 ever settles it the other way, R6 changes with it, and the two must not be
  allowed to drift apart silently.

## Requirements

### The precondition

- **R1** — ❗ **How many distinct CPV codes one Órgano's selection actually carries is measured
  before any feature under this spec is designed, and the measurement decides whether the tree is
  built at all.**

  The number is not currently known. It is a fact about **the contract source**, not about this
  system's database — the source publishes each procedure's classifications, and the adapters that
  read them are already built — so it does **not** wait on any family's import being finished, and
  the earlier claim that it did was wrong.

  What it decides: a tree over a couple of dozen codes is worse than a list of a couple of dozen
  codes, because it makes a reader take three decisions where one would do. Somewhere above that
  the flat list stops being scannable and the tree is plainly better. The measurement is recorded
  where a later reader can find it, together with the reading taken from it, so that this spec can
  be abandoned on evidence rather than argued about.

  **Until it is taken, no feature is owed anything by this spec**, and the family's existing flat
  list is not in deficit.

### Choosing a subject

- **R2** — **A reader narrowing by subject is offered a tree, not a list of codes.** They begin at
  the divisions and descend, choosing at whatever depth they mean. The code remains what is chosen
  and what is sent; recognising it is no longer how a reader gets there.

- **R3** — **Choosing a node selects that entry and everything beneath it.** Choosing the
  construction division selects a contract classified at a leaf several levels below it; choosing a
  leaf selects exactly that code. A reader who does not know how specific a classification will be
  can still find the contracts, which is the whole reason the standard is a hierarchy.

- **R4** — **A contract matching the chosen node through more than one of its classifications
  appears once and is counted once.** This is the ordinary case, not an edge: two lotes of one
  procedure classified under sibling codes both sit beneath the same division. It restates no
  family's dedup rule and adds none — it requires only that descending does not multiply rows a
  family already counts once.

### Which nodes are offered

- **R5** — ❗ **A node is offered when at least one contract of the selection is classified at or
  beneath it.** This is the sentence the whole capability turns on, and it is deliberately not the
  same as the flat list's rule. A year whose only construction contract is classified `45231100`
  **offers the division `45000000`**, which no contract of that year carries — the division is
  offered for what lies beneath it, not for itself.

  A family's existing "only codes the selection actually contains are offered" is therefore
  **narrowed to leaves and widened to ancestors** by this spec, and a family spec that adopts the
  tree must say so rather than leaving two rules that no implementation can both satisfy.

- **R6** — **The tree is pruned: a branch the selection reaches nothing under is not offered at
  all.** A division no contract of the selection falls under does not appear, and a division that
  appears offers only the groups the selection reaches. So a reader can descend any offered branch
  to any depth and choose there **without emptying the list** — which is the promise the family
  specs already make for their flat lists, kept at every level.

  **The selection this is computed against is the family's, and it excludes the subject itself.**
  Concretely: the tree is computed against the rows the reader would see with every *other*
  narrowing in effect and no subject chosen — so choosing a subject cannot empty the list, and
  changing another narrowing recomputes the tree. This spec does not require the converse; whether
  choosing a subject **and** another narrowing together can empty a list is the family's residual to
  state, and for licitacións SPEC-0008 has stated it.

- **R7** — **An entry the catalogue never acquired is offered, and rolls up like any other.** A
  contract classified under a code the standard has since retired (SPEC-0009 R5) appears in the
  tree at the position its digits give it, under its own code, with whatever wording it has — and
  **choosing any of its ancestors returns it.**

  ❗ This is the case the obvious implementation drops. A tree assembled by walking the acquired
  catalogue downward never reaches an entry that is not in it, so those contracts become
  unreachable by subject with nothing reporting it — the same silent-loss failure SPEC-0009 R7
  exists to prevent, arrived at from the other direction.

- **R8** — **Each offered node states how many contracts of the selection it covers**, counting
  everything at or beneath it, and counting each contract once (R4) on whatever unit the family
  counts in. A reader choosing between branches is choosing between populations, and the count is
  what makes that choice informed. It also makes R6 self-evident: a node offered with a count of
  zero is a visible contradiction rather than a silent one.

  Where a family already states what its counts count — SPEC-0008 R24 counts procedures — a node's
  count counts the same thing, so that a division's count and the list's count are commensurable.

- **R9** — **What a reader chose survives being read back.** Having narrowed, the reader can see
  which node is in effect, see it as a named subject rather than as a bare code, and clear it; and
  the choice is expressed in a way that can be shared and returned to. Nothing here re-decides how
  a family carries a selection — only that a subject chosen from a tree is as recoverable as one
  chosen from a list.

### Non-functional expectations

- **R10** — **The tree is computed per selection and read on every visit, and that is the cost
  worth watching.** Unlike the catalogue it is derived from (SPEC-0009 R18), it cannot be prepared
  once and reused: R6 makes it a statement about the rows in front of the reader, and two
  selections get two trees. Where a family already carries a read-latency obligation and a
  reference environment — SPEC-0008 R32 — the tree read is measured under it rather than under a
  harness of its own.

- **R11** — **A pruned tree requires the same authentication the browsing surface requires.** The
  vocabulary is public (SPEC-0009 R19); a pruned tree is not, because it states which subjects an
  Órgano's contracts fall under and how many of each, which is information about Galician public
  spending rather than about a European standard.

## Acceptance criteria

Criteria 2–11 are claimable only once R1's measurement has been taken and read in favour of the
tree; before then this spec obliges nothing, and a family's flat list is conformant.

1. **(R1)** The count of distinct CPV codes carried by one Órgano's selection is measured from the
   contract source and recorded, together with the reading taken from it, before any feature citing
   this spec is designed. No feature cites this spec while the number is unknown.
2. **(R2, R3)** Choosing a **division** returns exactly the contracts of the selection classified
   under any code at or beneath it — including one classified at a leaf several levels below — and
   choosing a **leaf** returns exactly those carrying that code.
3. **(R3)** The value sent when a node is chosen is that node's code, unaltered; no request carries
   a wording, a level, or a position in a list.
4. **(R4)** A contract carrying two classifications that both lie beneath the chosen node appears
   once in the result and contributes one to every count.
5. **(R5)** A selection whose only contract under a division is classified at a leaf several levels
   down **offers that division**, and offers each intermediate node on the path to that leaf, even
   though no contract carries any of their codes.
6. **(R6)** Every node offered at every level covers at least one contract of the selection: with
   no subject chosen, descending the offered tree to any depth and choosing there never produces an
   empty list.
7. **(R6)** A division no contract of the selection falls under is not offered; a division that is
   offered exposes only those groups the selection reaches.
8. **(R6)** Changing another narrowing recomputes the tree, and two selections differing in their
   contracts are offered different trees. Choosing a subject does not itself change the tree that
   is offered.
9. **(R7)** A contract classified under a code absent from the acquired catalogue is offered in the
   tree under its own code, **and is returned when any of its ancestor nodes is chosen** — including
   when no ancestor on its path is an acquired entry.
10. **(R8)** Every offered node states a count; that count equals the number of contracts of the
    selection at or beneath it, counted on the same unit the family's own list count uses; and no
    offered node states zero.
11. **(R9)** A subject chosen from the tree can be seen as a named subject with its code, cleared,
    and reached again from a shared reference to the same selection.
12. **(R11)** An unauthenticated visitor requesting a pruned tree is denied.
