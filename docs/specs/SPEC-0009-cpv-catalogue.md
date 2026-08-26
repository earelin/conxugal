---
status: draft
---

# SPEC-0009. The CPV catalogue

## Summary

The **Common Procurement Vocabulary** is the regulated European list that says what a public
contract is *for*. Every licitación this system imports cites at least one of its codes
([SPEC-0008](SPEC-0008-import-browse-licitacions.md) R8), and a reader can already narrow a year's
licitacións by one (SPEC-0008 R23) — but only by recognising a bare eight-digit number, because the
system holds the codes and nothing else. The vocabulary itself — what each code *means*, how the
codes relate to one another, and what any of it is called in Galician — is not held anywhere.

This spec holds it. The system acquires the regulated list from its publisher, derives from the
codes the hierarchy the list is built on, carries a **Galician rendering of every entry** — which
does not exist anywhere and which this system therefore authors — and offers a reader a **tree** to
choose from instead of a code to remember. The vocabulary is reference data about a European
standard, not information about Galician public spending: it is acquired once per revision of the
standard rather than on the daily cadence the contract families import on, from a different
publisher, and it says nothing about any contract until a contract cites it.

It serves the same authenticated reader every browsing surface serves, and it is deliberately
**not** authoritative over what the system will store: a procedure citing a code this catalogue has
never heard of is imported exactly as before. The catalogue supplies wording and a position in a
tree. It does not grant a code permission to exist.

## Scope

- **In scope:** acquiring the regulated list; the hierarchy derived from the codes; matching a
  catalogue entry to a code a contract source published; the Galician rendering and its
  provenance; offering a reader a tree pruned to what a selection actually contains; and what
  happens when the standard is revised.
- **Out of scope — which contracts a chosen code selects.** That belongs to each contract family's
  browsing spec. SPEC-0008 R23 owns it for licitacións, and this spec's tree is a way of *choosing*
  a code, never a rule about which rows carry it.
- **Out of scope — the supplementary vocabulary.** The standard publishes a second, alphanumeric
  vocabulary of 903 qualifiers (`AA12-4` and its kind) that further describe a contract's nature.
  Measured on the record's own classification table, the source publishes none of them, so
  acquiring them would be holding a vocabulary nothing cites. If a source is later measured to
  publish one, this exclusion is what has to change.
- **Out of scope — NUTS.** The system holds a second regulated European list of the same shape —
  a coded geographic hierarchy — and it is deliberately not folded in here. The two lists are
  built, revised and translated by different bodies on different cadences, and nothing about
  reading a subject-matter tree tells us a reader wants a geographic one. If NUTS is later given
  the same treatment it gets its own spec, and this one is the precedent rather than the container.
- **Out of scope — translating anything else.** The Galician rendering here covers the CPV
  entries and nothing beyond them. Contract objects, state labels, operador names and every other
  value the sources publish are stored **as published**, and no requirement here weakens that.
- **Out of scope — exporting the catalogue**, on the same deferral SPEC-0005 and
  [SPEC-0006](SPEC-0006-operadores-economicos.md) apply to their own data.
- **Out of scope — editing the catalogue in the application.** No surface creates, renames,
  re-parents or deletes a CPV entry. The list is a European standard; the only Galician wording
  this system owns is produced ahead of time (R9) and reaches the system as acquired data, not as
  something an administrator types.

### What this spec inherits, and does not restate

The reader, the authentication rule, the paging control and the shape of a browsing section are
all settled elsewhere and are not re-decided here. What this spec adds to a browsing surface is a
**way of choosing a code**; every other property of that surface belongs to the family whose
surface it is.

## Requirements

### Acquiring the list

- **R1** — The catalogue holds the **main vocabulary of CPV 2008**, the revision established by
  Regulation (EC) No 213/2008 and the one in force. It is acquired from the standard's publisher
  in the machine-readable form the publisher offers, and it is neither authored nor edited here:
  an entry exists in this system because it exists in the standard, and its code and its official
  wording are whatever the publisher says they are.

  The list is **9,454 entries**. That number is stated because it is verifiable against the
  publisher's own file and is therefore the cheapest possible check that an acquisition completed
  rather than half-completed — a truncated acquisition is otherwise indistinguishable from a
  successful one, since every entry it did load looks correct.

- **R2** — Acquisition is a **whole-list act, and it is repeatable.** The catalogue is not
  assembled incrementally from codes seen on contracts, and running the acquisition twice leaves
  the system in the state one run leaves it in. Nothing about the catalogue depends on which
  contracts have been imported, or in what order.

- **R3** — The catalogue is acquired **once per revision of the standard**, not on the cadence the
  contract families import on. It is reference data about a European standard: it does not go
  stale between contract imports and it is not part of any Órgano's import run.

### Identity, and the code a contract publishes

- **R4** — **A code's identity is its eight significant digits.** Every entry in the standard is
  published as eight digits and a ninth **check digit** — `45000000-7`. The check digit is a
  property of the code, computed from the other eight; it distinguishes nothing, because no two
  entries share eight digits and differ in the ninth.

  This matters because the two things being matched are spelled differently. The standard
  publishes `45000000-7`; **the contract source publishes `45000000`** — measured on procedure
  822054, whose classification cell reads the eight digits, a non-breaking space, then Spanish
  wording. A catalogue keyed on the published nine-character form would match **none** of the
  codes the system already holds, and would do so silently: every entry would load, every code
  would still classify its procedures, and every single one of them would simply have no wording
  and no place in the tree. So the eight digits are the identity, and the check digit is carried
  as a fact about the entry rather than as part of what names it.

- **R5** — **The catalogue does not decide which codes may exist.** A contract citing a code this
  catalogue does not contain is imported, stored and browsed exactly as one citing a code it does
  contain. The code keeps its meaning as the identifier the source published; what it lacks is
  wording and a position in the tree.

  **This is not a concession, it is the point.** CPV is versioned — the 2008 revision retired codes
  the 2003 revision issued — and this system imports procedures published across both. A catalogue
  allowed to reject an unknown code would turn a code that was perfectly valid when it was
  published into a rejected procedure, which is precisely the harm that storing values as published
  exists to prevent. The catalogue is a source of *wording*, never of *permission*.

  ❗ **This requirement is load-bearing and easy to lose.** The system's existing design records, in
  more than one place and at length, that nothing seeds the CPV list and nothing validates against
  it, on exactly this reasoning. Introducing a catalogue is what makes that reasoning look
  obsolete, and it is not: what changes is that entries now arrive ahead of the contracts citing
  them, not that arriving late stops being allowed.

- **R6** — **A code the catalogue does not contain is still offered and still selectable.** It
  appears in the tree at the position its digits give it (R7), under its own code, without wording.
  It is not hidden, not grouped into an "other" bucket, and not silently dropped — a reader
  narrowing by subject must be able to reach every contract, including one classified under a code
  the standard has since retired.

### The hierarchy

- **R7** — **The tree is derived from the code itself, and from nothing else.** The standard is
  built so that a code's digits state its position: the first two digits name a **division**, the
  third a **group**, the fourth a **class**, the fifth a **category**, and the sixth to eighth
  refine it further, with trailing zeros as padding. A code's parent is the same code with its
  last significant digit returned to zero.

  No separate statement of parentage is acquired, stored or maintained, because the standard does
  not publish one and the digits already carry it. Measured over the published list, the tree this
  yields is **45 divisions, 272 groups, 1,002 classes, 2,379 categories and 5,756 deeper entries**.

- **R8** — ❗ **An entry whose immediate parent is absent attaches to its nearest present
  ancestor.** The standard is not a complete tree, and assuming it is loses entries. Measured over
  all 9,454: the immediate parent is present for 9,374, and **35 entries have no immediate parent
  in the list at all** — `30192121` through `30192127` are published, `30192120` is not.

  Every one of those 35 does have an ancestor further up: walking toward the division until a
  present entry is found leaves **no entry unreachable**, and no entry more than two levels from
  the ancestor it attaches to. So the rule is *nearest present ancestor*, not *immediate parent*,
  and a tree built the naive way silently drops 35 entries — a defect that would surface only as
  a handful of contracts a reader can never find by subject, with no error anywhere.

### The Galician rendering

- **R9** — ❗ **The system authors the Galician wording, because nobody has published one.** The
  publisher issues the vocabulary in **23 languages**, and Galician is not among them: Galician is
  not an official language of the Union. The neighbouring places a reader would expect to find one
  do not have one either — the Spanish administration serves the list in Spanish behind a
  Galician-language interface, and the contract source publishes Spanish wording in its own
  classification cells.

  **The publisher's Irish wording is a trap and this requirement exists partly to name it.** The
  language is labelled `GA`, which reads as an abbreviation of *Galician* and is an abbreviation of
  *Gaeilge*; measured over the published file, all **9,454** of its strings are byte-identical to
  the English. Taking it would produce a complete, well-formed, fully populated English catalogue
  that passes every check anyone would think to write.

- **R10** — **The Galician rendering is produced ahead of time and acquired as data.** It is
  prepared once, reviewed as a whole, and reaches the system already written. Rendering it while
  contracts are being imported, or while a reader is waiting, is excluded: the wording must be the
  same on every installation and on every run, reproducible without reaching an external service,
  and inspectable as a body of text before it is ever shown to anyone.

- **R11** — **The official wording is retained beside the Galician one, not replaced by it.**
  Because this system's rendering is the only Galician one in existence, there is nothing to check
  it against later unless what it was rendered from is kept. At minimum the official **Spanish**
  wording is held, that being the language the contract source itself publishes and the one a
  reader is most likely to recognise a term from.

- **R12** — **The rendering is unofficial, is never the identity, and never stands alone.** The
  code identifies the entry — that rule is older than this spec and is not weakened by it. Wherever
  a Galician wording is shown, **the code is shown with it**, so a reader who does not recognise a
  term, or who suspects it, always has the value the standard actually assigns. A mistranslated
  entry can therefore mislead a reader about what a category is called; it cannot cause the wrong
  contracts to be selected.

- **R13** — **The catalogue states what its Galician wording is.** A reader who reaches the
  vocabulary can find out, without asking anyone, that the Galician rendering is produced by this
  system rather than by the standard's publisher, and what it was rendered from. This is one
  statement in one place, not a disclaimer repeated beside every entry.

### Choosing a subject

- **R14** — **A reader narrowing by subject is offered a tree, not a list of codes.** They begin at
  the divisions and descend, choosing at whatever depth they mean. This is what "narrow by CPV
  code" (SPEC-0008 R23) becomes: the code remains what is chosen and what is sent, but recognising
  it is no longer how a reader gets there.

- **R15** — **Choosing a node selects that entry and everything beneath it.** Choosing a division
  selects every contract classified anywhere within it; choosing a leaf selects exactly that code.
  A reader who does not know how specific a classification will be can therefore still find the
  contracts, which is the whole reason the standard is a hierarchy.

- **R16** — ❗ **The tree offered is pruned to the selection it will narrow.** Only branches the
  selection actually contains are offered, at every level — so a division appears only if some
  contract in the selection is classified within it, and a division that appears offers only the
  groups the selection reaches.

  **This is R23's promise, kept at every depth.** SPEC-0008 R22 and R23 already guarantee that
  choosing an offered narrowing can never be the reason a list is empty, and that guarantee is
  what a full 45-division tree would break: a reader would descend a branch with nothing in it and
  arrive at an empty list having done nothing wrong. Pruning makes the tree a statement about the
  selection in front of the reader rather than about the standard, which is the cost this
  requirement knowingly accepts — the tree cannot be prepared once and reused, because it is
  different for every selection.

- **R17** — **Each offered node states how many contracts of the selection it covers**, counting
  everything beneath it. A reader choosing between branches is choosing between populations, and
  the count is what makes that choice informed rather than exploratory. It also makes R16
  self-evident: a node offered with a count of zero is a visible contradiction rather than a silent
  one.

- **R18** — **What a reader chose survives being read back.** Having narrowed, the reader can see
  which node is in effect and clear it, and the choice is expressed in a way that can be shared and
  returned to. Nothing here re-decides how a selection is carried; it requires only that a subject
  chosen from a tree is as recoverable as one chosen from a list.

### Revision of the standard

- **R19** — **A later revision of CPV is a fresh acquisition, and it does not retire what the
  system holds.** Entries the new revision drops are **kept**, because contracts already imported
  cite them and a reader must still be able to find those contracts by subject (R6). Wording and
  position may change for entries the revision keeps; nothing about a superseded entry is deleted.

- **R20** — **The system states which revision it holds.** A catalogue that cannot say which
  revision it came from cannot be reasoned about when the standard moves, and the question is
  asked exactly once — at the point someone notices a code the system does not know.

### Non-functional expectations

- **R21** — **The catalogue is small, fixed and read constantly.** It is 9,454 entries and does not
  grow with use — it grows only when the standard is revised, or by one entry when a contract cites
  a code it lacks. The pruned tree (R16), by contrast, is computed per selection and is read every
  time a reader opens a browsing section, so it is the part whose cost is worth measuring and the
  catalogue itself is not.

  ❗ **The size of a pruned tree has not been measured and is the number this capability turns on.**
  How many distinct CPV codes one Órgano's year of contracts actually carries decides whether a
  tree is a genuine improvement over the flat list of codes SPEC-0008 R23 already provides. It
  cannot be measured yet, because no licitación rows have been stored. **It is measured before any
  feature under this spec is designed**, and recorded where a later reader can find it — a tree
  over twenty codes is worse than a list of twenty codes, and this spec would be wrong to have been
  built.

- **R22** — **Reading the catalogue and any tree derived from it requires the same authentication
  the browsing surfaces require.** The vocabulary is a public European standard and nothing about
  it is confidential; a **pruned** tree is not, because it states which subjects an Órgano's
  contracts fall under and how many of each, which is information about Galician public spending.

- **R23** — **The Galician rendering is inspectable as a whole before it is relied upon.** Because
  it is authored here (R9) and produced ahead of time (R10), it can be read end to end, diffed
  between revisions, and corrected in place. A correction changes wording and never a code.

## Acceptance criteria

1. **(R1)** The acquired catalogue holds exactly **9,454** main-vocabulary entries; an acquisition
   that yields any other count fails rather than completing with a partial list.
2. **(R1)** Every held entry's code and official wording match the publisher's file for that entry;
   no entry exists that the publisher's file does not contain.
3. **(R2)** Running the acquisition twice yields the same catalogue as running it once — same
   entries, same wording, same tree — and neither run depends on which contracts have been
   imported.
4. **(R4)** A catalogue entry published as `45000000-7` and a contract classification published as
   `45000000` resolve to the **same** entry, and that contract shows that entry's wording and sits
   at that entry's position in the tree.
5. **(R4)** No two held entries share the same eight significant digits.
6. **(R5)** A contract citing a code absent from the catalogue is imported, stored and browsed
   without error, and no import fails, is rejected or is deferred because of an unknown code.
7. **(R6)** That contract's code is offered in the tree, at the position its digits give it, under
   its own code and with no wording — it is neither hidden, nor bucketed under an "other" node, nor
   dropped.
8. **(R7)** The tree derived from the published list has **45** divisions, **272** groups,
   **1,002** classes, **2,379** categories and **5,756** deeper entries.
9. **(R8)** All **9,454** entries are reachable from a division by descending the tree; **no entry
   is orphaned**. In particular each of `30192121`…`30192127` is present in the tree despite
   `30192120` being absent from the standard, and each attaches to `30192100`.
10. **(R8)** Exactly **35** entries attach to an ancestor other than their immediate parent, and
    none attaches more than two levels above itself.
11. **(R9)** The held Galician wording of an entry is **not** equal to that entry's official
    English wording, except where the official Spanish wording is also equal to it. This is the
    check that fails if the publisher's `GA` (Irish) column — byte-identical to English for all
    9,454 entries — is ever mistaken for Galician.
12. **(R10)** The Galician wording is present for all 9,454 entries before any reader can query the
    catalogue, and producing it requires no call to any external service at import time or at read
    time.
13. **(R11)** Every entry carries the official Spanish wording alongside the Galician one, and no
    operation replaces or discards it.
14. **(R12)** Every surface that shows a CPV entry's wording shows its code as well; no surface
    identifies an entry by wording alone.
15. **(R12)** Choosing an entry sends its code, unaltered — never its wording, in any language, and
    never a position in a list.
16. **(R13)** A reader can find, from within the application, a statement that the Galician wording
    is produced by this system rather than by the standard's publisher, and what it was rendered
    from.
17. **(R14, R15)** Choosing a **division** returns exactly the contracts of the selection
    classified under any code within it — including one classified at a leaf several levels below —
    and choosing a **leaf** returns exactly those carrying that code.
18. **(R15)** A contract classified under two codes in the same branch appears **once** in the
    result and is counted once.
19. **(R16)** Every node offered at every level covers at least one contract of the selection:
    descending the offered tree to any depth and choosing there never produces an empty list.
20. **(R16)** A division the selection contains offers only the groups the selection reaches;
    a division the selection does not contain is not offered at all.
21. **(R16)** Two selections that contain different subjects are offered different trees; the tree
    is not fixed across selections.
22. **(R17)** Every offered node states a count, that count equals the number of contracts of the
    selection at or beneath it, and no offered node states zero.
23. **(R18)** A subject chosen from the tree can be read back, seen, cleared, and reached again
    from a shared reference to the same selection.
24. **(R19)** After acquiring a revision that drops an entry, that entry is still held, still shows
    its wording, and the contracts citing it are still reachable by subject.
25. **(R20)** The system reports which CPV revision the catalogue was acquired from.
26. **(R21)** The measurement of how many distinct CPV codes one Órgano's year of contracts carries
    is recorded before any feature under this spec is designed, and no feature is designed against
    an unmeasured assumption that a tree is needed.
27. **(R22)** An unauthenticated visitor requesting the catalogue, or any tree derived from it, is
    denied.
28. **(R23)** The Galician rendering can be read in full and diffed between two revisions of it,
    and a correction to an entry's wording leaves its code, its position and every contract's
    classification unchanged.
