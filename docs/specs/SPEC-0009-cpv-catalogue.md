---
status: draft
---

# SPEC-0009. The CPV catalogue

## Summary

The **Common Procurement Vocabulary** is the regulated European list that says what a public
contract is *for*. Every licitación this system imports cites at least one of its codes
([SPEC-0008](SPEC-0008-import-browse-licitacions.md) R8), and a reader can already narrow a year's
licitacións by one (SPEC-0008 R23) — but only by recognising a bare eight-digit number, because
what the system holds about a code is at most whatever wording the contract source happened to
print beside it.

This spec holds the vocabulary itself. The system acquires the regulated list from its publisher,
derives from the codes the hierarchy the list is built on, and carries a **Galician rendering of
every entry** — which does not exist anywhere and which this system therefore authors. The
vocabulary is reference data about a European standard, not information about Galician public
spending: it is acquired once per revision of the standard rather than on the daily cadence the
contract families import on, from a different publisher, and it says nothing about any contract
until a contract cites it.

**It improves the filter that already exists.** A reader narrowing by CPV today chooses between
bare numbers; with the catalogue in place they choose between named subjects, in Galician, and
nothing about how the narrowing is offered has to change for that to be worth having.
[SPEC-0010](SPEC-0010-hierarchical-cpv-selection.md) is what turns the derived hierarchy into a
tree a reader descends, and it is deliberately a separate spec because it rests on a measurement
this one does not need.

The catalogue is **not** authoritative over what the system will store: a procedure citing a code
it has never heard of is imported exactly as before. It supplies wording and a position in a
hierarchy. It does not grant a code permission to exist.

## Scope

- **In scope:** acquiring the regulated list; the hierarchy derived from the codes; matching a
  catalogue entry to a code a contract source published; the Galician rendering, its provenance
  and its relationship to the official wording and to the source's own; and what happens when the
  standard is revised.
- **Out of scope — offering the hierarchy to a reader.** Descending a tree, pruning it to a
  selection, and what choosing a node selects are [SPEC-0010](SPEC-0010-hierarchical-cpv-selection.md)'s.
  This spec derives the hierarchy and stops there.
- **Out of scope — which contracts a chosen code selects.** That belongs to each contract
  family's browsing spec; SPEC-0008 R23 owns it for licitacións. Nothing here changes it, and in
  particular the flat list of codes R23 offers today continues to satisfy R23.
- **Out of scope — the supplementary vocabulary.** The standard publishes a second, alphanumeric
  vocabulary of qualifiers (`AA12-4` and its kind) that further describe a contract's nature.
  Measured on the record's own classification table, the source publishes none of them, so
  acquiring them would be holding a vocabulary nothing cites. If a source is later measured to
  publish one, this exclusion is what has to change.
- **Out of scope — NUTS.** The system holds a second regulated European list of the same shape —
  a coded geographic hierarchy — and it is deliberately not folded in here. The two lists are
  built, revised and translated by different bodies on different cadences, and nothing about
  reading a subject-matter vocabulary tells us a reader wants a geographic one. If NUTS is later
  given the same treatment it gets its own spec, and this one is the precedent rather than the
  container.
- **Out of scope — translating anything else.** The Galician rendering covers CPV entries and
  nothing beyond them. Contract objects, state labels, operador names and every other value the
  sources publish are stored **as published** (SPEC-0008 R33), and no requirement here weakens
  that.
- **Out of scope — exporting the catalogue**, on the same deferral
  [SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) and
  [SPEC-0006](SPEC-0006-operadores-economicos.md) apply to their own data.
- **Out of scope — editing the catalogue in the application.** No surface creates, renames,
  re-parents or deletes a CPV entry. The list is a European standard, and the Galician wording
  this system owns is authored ahead of time (R8) and reaches the system as acquired data, not as
  something an administrator types.

### What this spec requires of its sibling specs

- **[SPEC-0003](SPEC-0003-administration-area.md) must be able to show that an acquisition
  happened, and that one failed.** R3 makes acquisition an administrator-triggered act rather than
  part of any Órgano's import run, and R1 requires a short acquisition to fail rather than to
  complete quietly — which is only useful if it fails *to somebody*. What this spec owes that one
  is therefore a fact it does not currently carry: which revision the catalogue holds, when it was
  acquired, and whether the last attempt succeeded.

- **[SPEC-0008](SPEC-0008-import-browse-licitacions.md) settles what a reader sees where the two
  wordings disagree.** R9 holds two Spanish strings for the same entry — the publisher's official
  wording and whatever the contract source printed beside the code — and SPEC-0008 R33's *store as
  published* is why the second cannot simply be discarded. Which one a licitación's own row shows
  is that spec's call, not this one's; what this spec guarantees is that both are available to make
  it with.

### Two decisions this spec reverses, named rather than left to be discovered

The shipped design records both, at length and with reasons. Neither reason has stopped being
true; what has changed is narrower than the sentences that state them.

- **"Nothing seeds this table."** `V19`'s preamble, `Cpv`'s javadoc and `CpvRepository`'s javadoc
  each state that nothing seeds the CPV list **and** nothing validates against it, as one
  conjunction. **R5 supersedes the first half and preserves the second.** Entries now arrive ahead
  of the contracts citing them; arriving late does not stop being allowed. The feature that
  acquires the list amends those three comments, on the precedent of FEAT-0015 correcting
  FEAT-0010's README when it widened `FiscalIdentifier`.

- **"Two published spellings stay two entries."** `Cpv`'s javadoc holds a code stripped of
  surrounding whitespace and *reduced no further* — no reformatting of the separator a code may
  carry — **so that the system never asserts an equivalence the list did not state.** R4 asserts
  exactly one such equivalence, between `45000000-7` and `45000000`, and it is the one the list
  *does* state: the ninth digit is computed from the other eight and distinguishes nothing. This
  narrows a uniqueness rule the system has already shipped, so it is a data migration and not only
  a wording change.

## Requirements

### What was measured, and when

Every count below was taken from the publisher's own machine-readable file for **CPV 2008**, on
**2026-08-26**. They are recorded here as one dated block rather than repeated through the
requirements, because a later revision changes all of them at once (R12) and the acceptance
criteria are phrased against the acquired file rather than against these literals.

| Measured | Value |
| --- | --- |
| Main vocabulary entries | 9,454 |
| Divisions / groups / classes / categories / deeper | 45 / 272 / 1,002 / 2,379 / 5,756 |
| Entries whose immediate parent is absent from the list | 35 |
| Entries unreachable from a division | 0 |
| Languages published | 23 — Galician not among them |
| Irish (`GA`) strings byte-identical to English | 9,454 of 9,454 |
| Supplementary vocabulary entries (not acquired) | 903 |

### Acquiring the list

- **R1** — The catalogue holds the **main vocabulary of CPV 2008**, the revision established by
  Regulation (EC) No 213/2008 and the revision in force as at the date above. It is acquired from
  the standard's publisher in the machine-readable form the publisher offers, and it is neither
  authored nor edited here: an **acquired entry** exists because it exists in the standard, and its
  code and official wording are whatever the publisher says they are.

  **An acquisition that yields fewer entries than the publisher's file contains fails**, rather
  than completing with a partial list. This is the cheapest possible check that an acquisition
  finished, because a truncated one is otherwise indistinguishable from a whole one: every entry it
  did load looks correct.

- **R2** — Acquisition is a **whole-list act, and it is repeatable.** The catalogue is not
  assembled incrementally from codes seen on contracts, and running the acquisition twice leaves
  the system in the state one run leaves it in. Nothing about it depends on which contracts have
  been imported, or in what order.

- **R3** — **Acquisition is an administrator's act, and it is not part of any import run.** The
  catalogue is acquired once per revision of the standard: it does not go stale between contract
  imports, it must not be attempted on the cadence the families import on, and it must not be able
  to block or be blocked by an Órgano's import. Its outcome is visible to the administrator who
  triggered it.

### Identity, and the code a contract publishes

- **R4** — **A code's identity is its eight significant digits.** Every entry in the standard is
  published as eight digits and a ninth **check digit** — `45000000-7`. The check digit is computed
  from the other eight and distinguishes nothing: no two entries share eight digits and differ in
  the ninth. It is therefore not part of the identity, and it is not held — it can be recomputed
  from the eight whenever the official spelling is wanted.

  This matters because the two things being matched are spelled differently. The standard publishes
  `45000000-7`; **the contract source publishes `45000000`** — measured on procedure 822054, whose
  classification cell reads the eight digits, a non-breaking space, then Spanish wording. A
  catalogue keyed on the published nine-character form would match **none** of the codes the system
  already holds, and would do so silently: every entry would load, every code would still classify
  its procedures, and every one of them would simply have no wording and no place in the hierarchy.

- **R5** — **The catalogue holds two populations, and it does not decide which codes may exist.**
  An **acquired entry** comes from the publisher's file (R1). An **entry first seen on a contract**
  is created because a contract cited a code the catalogue did not contain — and that contract is
  imported, stored and browsed exactly as one citing an acquired entry.

  **This is not a concession, it is the point.** CPV is versioned — the 2008 revision retired codes
  the 2003 revision issued — and this system imports procedures published across both. A catalogue
  allowed to reject an unknown code would turn a code that was perfectly valid when it was
  published into a rejected procedure, which is precisely the harm that storing values as published
  exists to prevent. The catalogue is a source of *wording*, never of *permission*.

  Every requirement below that speaks of the publisher's file, official wording or a revision
  speaks of **acquired entries**. An entry first seen on a contract has a code, a position in the
  hierarchy (R6), and whatever wording its contract supplied — and it may later become an acquired
  entry, if a revision the system acquires contains it.

### The hierarchy

- **R6** — **The hierarchy is derived from the code itself, and from nothing else.** The standard
  is built so that a code's digits state its position: the first two digits name a **division**,
  the third a **group**, the fourth a **class**, the fifth a **category**, and the sixth to eighth
  refine it further, with trailing zeros as padding. A code's parent is the same code with its last
  significant digit returned to zero.

  Nothing separate is acquired to state parentage, because the standard publishes nothing separate
  and the digits already carry it. **Every entry has a position, including one first seen on a
  contract** — its digits are as good as any other entry's, which is what keeps a contract under a
  retired code reachable.

- **R7** — ❗ **An entry whose immediate parent is absent attaches to its nearest present
  ancestor.** The standard is not a complete tree, and assuming it is loses entries: measured,
  **35** entries have no immediate parent in the published list at all — `30192121` through
  `30192127` are published, `30192120` is not.

  Every one of those does have an ancestor further up, and walking toward the division until a
  present entry is found leaves **no entry unreachable**. So the rule is *nearest present
  ancestor*, not *immediate parent*: built the naive way, the hierarchy silently drops those 35 as
  a handful of contracts no reader can find by subject, with no error anywhere.

- **R8** — **An entry with no present ancestor at all attaches to the division its first two
  digits name, and that division exists for it.** R5's population makes this reachable in a way
  the publisher's file alone does not: a retired code may sit under intermediate codes the current
  revision also dropped. No entry is left outside the hierarchy, and no entry becomes a root of its
  own — a division is always the top, whether or not the publisher's file contains that division.

### The wording

- **R9** — **Three wordings, and they are three distinct facts.** For each entry the system may
  hold: the publisher's **official wording**, which the standard assigns; the **source's own
  wording**, which a contract printed beside the code and which SPEC-0008 R33 stores as published;
  and the **Galician rendering** this system authors (R10). They are not interchangeable and none
  replaces another.

  At minimum the official **Spanish** wording is held for every acquired entry, that being the
  language the contract source itself publishes and the one a reader is most likely to recognise a
  term from. It is also what makes the Galician rendering checkable later: this system's rendering
  is the only Galician one in existence, so there is nothing to check it against unless what it was
  rendered from is kept.

  The two Spanish strings may disagree, and neither is wrong — the source is describing this
  contract's subject, the publisher is naming a category. Which one a contract's own row shows is
  that family's call (see *What this spec requires of its sibling specs*); what is settled here is
  that acquiring the catalogue never overwrites or discards what a contract published.

- **R10** — ❗ **The system authors the Galician wording, because nobody has published one.** The
  publisher issues the vocabulary in 23 languages, and Galician is not among them: Galician is not
  an official language of the Union. The neighbouring places a reader would expect to find one do
  not have one either — the Spanish administration serves the list in Spanish behind a
  Galician-language interface, and the contract source publishes Spanish in its own classification
  cells.

  **The publisher's Irish wording is a trap and this requirement exists partly to name it.** The
  language is labelled `GA`, which reads as an abbreviation of *Galician* and is an abbreviation of
  *Gaeilge*; measured, all **9,454** of its strings are byte-identical to the English. Taking it
  would produce a complete, well-formed, fully populated English catalogue that passes every check
  anyone would think to write.

- **R11** — **The rendering is deterministic, self-contained and inspectable before it is shown.**
  The same acquisition yields the same wording on every installation and on every run; producing it
  reaches no external service, either while contracts are being imported or while a reader is
  waiting; and it can be read as a body of text, in full, before anyone is shown any of it.

  *How* that is achieved is the acquiring feature's to decide, but the requirement rules out the
  obvious shortcut: a rendering computed when it is needed satisfies none of the three.

- **R12** — **The rendering is unofficial, is never the identity, and never stands alone.** The
  code identifies the entry — that rule is older than this spec and is not weakened by it. Wherever
  a Galician wording is shown, **the code is shown with it**, so a reader who does not recognise a
  term, or who suspects it, always has the value the standard actually assigns. A mistranslated
  entry can therefore mislead a reader about what a category is called; it cannot cause the wrong
  contracts to be selected.

- **R13** — **The catalogue states what its Galician wording is.** A reader who reaches the
  vocabulary can find out, in one place and without asking anyone, that the Galician rendering is
  produced by this system rather than by the standard's publisher, and what it was rendered from.
  One statement, not a disclaimer beside every entry.

### Revision of the standard

- **R14** — **A later revision is a fresh acquisition, and it does not retire what the system
  holds.** Entries the new revision drops are **kept**, because contracts already imported cite
  them and must stay reachable by subject (R5, R6). Nothing about a superseded entry is deleted.

- **R15** — **What a revision can change, and what it cannot.** An entry's **position cannot
  change**, because position is a function of its digits (R6) and its digits are its identity (R4).
  Two things can: its **official wording**, and the **ancestor it attaches to** — a revision that
  adds or drops an intermediate code makes R7's walk resolve differently, which is the one way a
  revision can silently re-home an entry.

  ❗ **A revision may also keep a code and change what it means.** That happened between the 2003
  and 2008 revisions, and it is the case where *keeping* an entry is actively wrong: contracts
  classified under the old meaning are now described by the new one, and nothing in the data says
  so. The system must be able to state that an entry's meaning changed at a revision, so that this
  is visible rather than silently absorbed.

- **R16** — **Revision provenance is per entry, not per catalogue.** After a second acquisition the
  catalogue holds entries the new revision dropped alongside the new revision's own, so *the
  revision the catalogue holds* does not denote. Each entry states which revision it came from, or
  that it was first seen on a contract; the catalogue as a whole states the most recent revision
  acquired and when.

- **R17** — **A revision that changes an entry's official wording invalidates its Galician
  rendering.** The rendering is authored from the official wording (R9, R10), so wording that
  changes leaves a rendering that describes the old category. Such an entry is re-rendered, and
  until it is, it is not presented as though nothing happened.

  This is the same class of silent defect R4 and R7 exist to prevent: the obvious implementation
  acquires the new wording, leaves the old rendering attached, and reports nothing.

### Non-functional expectations

- **R18** — **The catalogue is small and fixed.** It is on the order of ten thousand entries and
  does not grow with use — it grows when the standard is revised, or by one entry when a contract
  cites a code it lacks. Nothing about its size needs measuring; where a cost is worth measuring is
  in the surfaces that read it, and SPEC-0008 R32 already owns that obligation for the family that
  has one.

- **R19** — **Reading the catalogue requires the same authentication the browsing surfaces
  require.** Not because the vocabulary is confidential — it is a public European standard, and
  anyone can fetch it from its publisher — but because every read surface this system offers is
  behind a session, and a catalogue reachable without one would be the first exception. Uniformity
  is the reason; there is no secret here.

- **R20** — **The Galician rendering is correctable without disturbing anything else.** Because it
  is authored here and produced ahead of time, an error in it is fixed by re-rendering that entry.
  A correction changes wording only: never a code, never a position, and never any contract's
  classification.

## Acceptance criteria

Criteria 1–4 and 20–24 concern **acquired entries** (R5); a catalogue that has also created entries
from contracts still passes them.

1. **(R1)** After an acquisition, the number of acquired entries equals the number of entries in
   the publisher's file, and an acquisition yielding fewer **fails** rather than reporting success.
   Measured against CPV 2008 as at the date above, that number is **9,454**.
2. **(R1)** Every acquired entry's code and official wording match the publisher's file for that
   entry, and no acquired entry exists that the publisher's file does not contain.
3. **(R2)** Running the acquisition twice yields the same catalogue as running it once — same
   entries, same wording, same hierarchy — and neither run depends on which contracts have been
   imported.
4. **(R3)** An acquisition can be triggered by an administrator and by nothing else; no Órgano
   import run triggers, blocks or is blocked by one; and its outcome, success or failure, is
   reported to the administrator who triggered it.
5. **(R4)** A catalogue entry published as `45000000-7` and a contract classification published as
   `45000000` resolve to the **same** entry, and that contract shows that entry's wording and sits
   at that entry's position in the hierarchy.
6. **(R4)** No two held entries share the same eight significant digits, and importing a
   classification whose code is the nine-character spelling of a code already held creates no
   second entry.
7. **(R5)** A contract citing a code absent from the catalogue is imported, stored and browsed
   without error; no import fails, is rejected or is deferred because of an unknown code; and an
   entry is created for that code.
8. **(R5, R9)** That entry carries whatever wording its contract supplied and no official wording;
   acquiring a revision that contains its code later fills in the official wording without creating
   a second entry.
9. **(R6, R7)** Every held entry is reachable from a division by descending the hierarchy; **no
   entry is orphaned**. In particular each of `30192121`…`30192127` is present despite `30192120`
   being absent from the standard, and each attaches to `30192100`.
10. **(R7)** No entry attaches to an ancestor more than two levels above itself. Measured against
    CPV 2008 as at the date above, exactly **35** entries attach to other than their immediate
    parent.
11. **(R8)** An entry none of whose ancestors is held attaches to the division its first two digits
    name, that division is present in the hierarchy, and the entry is not a root of its own.
12. **(R9)** An entry's official wording, its source-supplied wording and its Galician rendering
    are separately readable where the system holds more than one, and acquiring the catalogue
    overwrites or discards neither of the other two.
13. **(R10)** The held Galician wording of an acquired entry is not, **across the catalogue as a
    whole**, the publisher's English wording: the number of entries whose Galician equals the
    official English is of the same order as the number whose official **Spanish** also equals the
    official English. This fails outright on the whole-column substitution it exists to catch — the
    publisher's Irish (`GA`) column, byte-identical to English for all 9,454 entries — while
    admitting the individual entries where a correct Galician wording legitimately coincides
    (`Software`, `Kits`, `Diesel`).
14. **(R11)** The same acquisition run twice produces byte-identical Galician wording; producing it
    issues no request to any external service at import time or at read time; and the whole
    rendering can be read as one body of text before any reader has been shown an entry.
15. **(R12)** Every surface that shows a CPV entry's wording shows its code as well; no surface
    identifies an entry by wording alone.
16. **(R12)** Choosing an entry sends its code, unaltered — never its wording, in any language, and
    never a position in a list.
17. **(R13)** The statement that the Galician wording is produced by this system rather than by the
    standard's publisher, and what it was rendered from, is reachable from the surface that offers
    the vocabulary, and appears once rather than per entry.
18. **(R14)** After acquiring a revision that drops an entry, that entry is still held, still shows
    its wording, and the contracts citing it are still reachable by subject.
19. **(R15)** Acquiring a revision leaves every kept entry at the same position. Where a revision
    adds or drops an intermediate code, an entry whose nearest present ancestor changes is
    identifiable as having been re-homed.
20. **(R15)** An entry the system records as having changed meaning at a revision can be told from
    one whose wording merely changed, and the change is visible rather than absorbed.
21. **(R16)** Each entry states the revision it was acquired from, or that it was first seen on a
    contract; the catalogue states the most recent revision acquired and when it was acquired; and
    after two acquisitions no single revision is claimed of the whole catalogue.
22. **(R17)** After acquiring a revision that changes an entry's official wording, that entry is
    not presented with the Galician rendering authored from the superseded wording.
23. **(R19)** An unauthenticated visitor requesting the catalogue is denied.
24. **(R20)** Correcting an entry's Galician wording leaves its code, its position and every
    contract's classification unchanged.
