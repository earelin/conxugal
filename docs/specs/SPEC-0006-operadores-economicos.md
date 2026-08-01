---
status: draft
---

# SPEC-0006. Operadores económicos and their contract history

## Summary

Every public contract the system holds names the party that was awarded it, together with a
fiscal identifier. This spec turns those awardees into a first-class catalogue of
**operadores económicos**, and gives each one a place in the application where its
**contract history across every Órgano de Contratación** is visible, split by contract
family exactly as an Órgano's own contracts are.

That cross-Órgano view is the whole point. The official source publishes each Órgano's
awards in isolation and offers no way to ask what a given supplier has been awarded overall
— so the accumulation of contracts to one operador is invisible until someone assembles it.
Assembling it is what makes the data answer the question people actually have.

The catalogue is **derived, never imported**: it has no source of its own and no import of
its own. It is a projection of the contracts the system already holds, and it exists only
so long as those contracts do. It is also **family-neutral by construction**: contratos
menores are the first family to feed it (via
[SPEC-0005](SPEC-0005-import-browse-contratos-menores.md)), licitacións will be the second,
and any later family feeds the same catalogue and the same history rather than defining its
own. This is why operadores are specified here and not inside the spec of any one family.

Two properties of the real published data drive most of the requirements below, and neither
can be assumed away at this volume:

- **Awardees are not all companies.** Roughly one in seven is a **natural person**,
  published with a personal fiscal identifier. The catalogue therefore models an *operador
  económico* — a person or an entity — rather than an *empresa*, and it means the catalogue
  handles personal data and produces new derived information about identifiable people. R12
  states that plainly rather than denying it.
- **Identifiers and names are published inconsistently.** The same identifier appears with
  different padding and casing, and under varying names, so matching and display each need a
  rule of their own (R3, R4, R13). Matched naively the aggregation fails silently, and a quiet
  undercount is worse than an error.

Reading operadores is available to any authenticated user, consistent with
[SPEC-0002](SPEC-0002-user-authentication.md). There is no administrative surface here: the
catalogue is derived, so it is managed by managing the contracts it comes from.

## Scope

- **In scope:** the derived catalogue, operador identity and matching, the display name
  rule, awardees whose identifier is unusable, operador lifecycle, how a user finds an
  operador, and the contract history — its split by contract family, its totals, filtering,
  sorting and pagination.
- **Out of scope — importing contracts.** Every family's import is owned by that family's
  spec; contratos menores by [SPEC-0005](SPEC-0005-import-browse-contratos-menores.md).
- **Out of scope — anything about an operador the contracts do not say.** No enrichment from
  company registries, no sector or size classification, no linking of related entities, and
  no inference of whether an identifier belongs to a person or an entity beyond what the
  source states. Each would be new information about identifiable people from outside the
  official publication.
- **Out of scope — exporting an operador's history.** Left to the future export spec
  SPEC-0005 also defers to.
- **Out of scope — erasing an operador.** No function removes an operador's data; R7 governs
  only when an operador stops being *reachable*, and R12 records why that is judged
  acceptable.

### What a contract family must supply

The catalogue is family-neutral, so it depends on every feeding family supplying the same
facts about each contract. Stated here rather than left implicit, because a family that
cannot supply them cannot feed this catalogue, and that is worth discovering while writing
that family's spec rather than while building it:

- an **awardee name** and an **awardee fiscal identifier**, as published (R3, R4);
- a single **publication date** per contract, comparable across families — R4's most-recent
  rule, R10's year filter and R10's date sort all order contracts of different families
  against one another;
- an **amount**, on the VAT basis R9 fixes;
- the **awarding Órgano**;
- a **stable contract identity**, comparable across families, since R4 breaks ties on it;
- an **explicit removal rule** — a family must say what it means for one of its contracts to
  be withdrawn, because R7's lifecycle hooks onto it;
- a **family name**, since R9 presents the history one section per family and titles each
  section with it.

Contratos menores supply all seven
([SPEC-0005](SPEC-0005-import-browse-contratos-menores.md)).
Licitacións publish more and differently, and the licitacións spec is where any mismatch has
to be resolved.

### Decisions taken, and what is left open

**How reads are paged** is settled, and not by an ADR.
[SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) closes it: paging stays positional,
with an exact count and a jump to a chosen page, and its cost is measured before it is
optimised. R11 and R14 here follow that, as this spec said they must. The reasoning does
**not** transfer intact, though: SPEC-0005 bounds every selection to one Órgano in one
publication year and this spec's operadores list has no such scope, which is why R14 states
its own dataset and reads the deferral as the larger bet of the two.

One decision remains open:

- **Whether the catalogue is stored state or computed on read.** R2 says the catalogue is
  derived; it does not say whether it is maintained as its own stored projection or assembled
  from the contracts at query time. That choice decides whether R7's lifecycle happens
  automatically or has to be driven, and whether R14's reads are viable at all over hundreds
  of thousands of operadores. It is architecturally significant and should be an ADR before a
  feature settles it.

## Requirements

### Access

- **R1** — Reading the operadores catalogue (R8) and any operador's contract history (R9) is
  available to any authenticated user, `USER` or `ADMIN`, and grants no ability to modify
  anything. An unauthenticated visitor is denied — a mitigation R12 depends on. There is no
  management surface: the catalogue is derived (R2), so nothing about an operador is
  editable.

### The derived catalogue

- **R2** — The system maintains a catalogue of **operadores económicos** derived from the
  contracts it holds, of **every** contract family, with no source and no import of its own.
  An operador appears because a contract names it and for no other reason.
- **R3** — An operador is identified by the **fiscal identifier** published with the award.
  Because the source publishes identifiers with inconsistent padding and casing, two awards
  name the same operador when their identifiers are equal **ignoring surrounding whitespace
  and letter case**. This equivalence governs **matching only**: what is displayed is always
  the value exactly as published (R13). Without it the same operador splits in two and the
  cross-Órgano aggregation this spec exists for fails silently.
- **R4** — The same identifier is published under **varying names**, and with varying padding
  and casing. Since R13 forbids normalising either, an operador is shown under the **name and
  the identifier spelling** taken from its **most recently published** contract — ties broken
  by taking the **higher** contract identifier, so the choice is deterministic and not merely
  "some tie-break". The rule covers the identifier as well as the name because R3 deliberately
  matches `b12345678`, ` B12345678 ` and `B12345678` as one operador, and that operador must
  still be shown under exactly one of those published spellings rather than an invented
  canonical form. Neither name nor spelling variation ever produces a second operador.
- **R5** — An identifier is **unusable** when it is absent, or empty once surrounding
  whitespace is ignored. Such a contract yields **no** operador — never an invented or
  placeholder one — while remaining stored, browsable and displaying its awardee's name as its
  own family requires ([SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) R7). Nothing
  beyond the emptiness test is validated: the source publishes irregular but genuine
  identifiers, and rejecting them would discard real awards.
- **R6** — Awardees that are **natural persons** and those that are **legal entities** are
  catalogued and reachable identically, and the system does not classify which is which. The
  reason is the no-inference rule of this spec's Scope, not an absence in the source: the kind
  of identifier published does in practice distinguish the two, which is how the roughly
  one-in-seven figure in the Summary is known at all. Deriving and storing that classification
  would be new information about identifiable people, so the system declines to, even though it
  could.
- **R7** — An operador is **reachable exactly as long as it has at least one visible
  contract**. When its last contract is withdrawn under its family's removal rule, the operador
  appears in no list, no lookup and no history — it ceases to exist as far as every surface of
  this spec is concerned.

  It is **not erased**, and this spec does not claim otherwise. The families that feed the
  catalogue never delete a contract: a withdrawal is remembered and can be undone, which is
  what makes restoration possible. So the awardee data an operador was derived from is
  retained, and restoring the contract restores the operador. R12 records why that is
  acceptable here.

  The catalogue **re-derives from the contracts as they currently stand**, not only when
  contracts appear and disappear. Because a family may refresh a stored contract's
  published attributes in place, and may re-read old publications to catch corrections, a
  correction can change the fiscal identifier or the name a contract was published under.
  When it does: the contract's contribution **moves** to the operador its corrected identifier
  names, creating that operador if no contract named it before; an operador left with no
  visible contracts becomes unreachable under R7, however that came about; and a contract that
  becomes visible again — newly imported, or restored after withdrawal — makes its operador
  reachable again. R4's display name and identifier follow from whatever the contracts say
  after the change, not from what they said when the operador first appeared.

### Finding an operador

- **R8** — A user can reach an operador in two ways: by following the **awardee from any
  contract row that has one** — a row whose identifier is unusable (R5) has no operador to
  follow, and offers no route that dead-ends — and from a **list of operadores** that can be
  looked up by name or by fiscal identifier. Name lookup matches any part of the name,
  ignoring letter case and accents; identifier lookup matches the **whole** identifier under
  the equivalence of R3, so a padded or differently-cased query still finds it.

  That asymmetry — partial matching on names, whole-identifier matching on identifiers — is
  **deliberate and load-bearing**, not an oversight: it means the catalogue cannot be walked
  by feeding it fragments of fiscal identifiers, which R12 relies on. Relaxing identifier
  lookup to a prefix or substring match would quietly remove that protection.

  Without the list, the primary question this capability exists to answer — *what has this
  supplier been awarded?* — could only be asked by first stumbling onto one of its contracts.

### Contract history

- **R9** — Opening an operador shows its **contract history**: every contract awarded to it
  **across all Órganos and all contract families**, presented **split by contract family**,
  one section per family, under the same rule
  [SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) R15 fixes for an Órgano's
  contracts — each family reachable independently, a family the operador holds no contracts in
  **omitted** rather than shown empty, and the split **additive** as further families are
  gained. The rule is cited rather than restated because a user meets both presentations in the
  same session, and an operador's history should not be organised differently from the Órgano
  page they reached it from.

  Each section shows, per contract, the awarding Órgano and the same published attributes that
  family shows in its own list — including, where that family offers one, the route to the
  contract's publication at the official source, so a row here is as verifiable as a row there.
  A row does **not** repeat the family it belongs to: the section it sits in already names it.

  Each row's **awarding Órgano is followable to that Órgano's own page**, under the routes
  [SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) R14 fixes — the mirror of the
  awardee route R8 puts on every contract row, and the reason this history is more than a
  read-only report: a user tracing a supplier's record can cross to any of the bodies that
  awarded it, and from there back out to that body's other suppliers. It never dead-ends,
  because every Órgano the system holds contracts for is reachable.

  Each section reports the **number of contracts** and the **total amount awarded** for **its
  own current selection** (R10). The profile additionally reports a **combined count and
  total across every section**, because *what has this operador been awarded overall* is the
  question this capability exists to answer and it should not be answerable only by adding the
  sections up by hand. That combined figure is always the sum of the sections **as currently
  selected**, so one semantics governs the page: filter or re-scope a section and both that
  section's figures and the combined figures move together. Unfiltered — the state a profile
  opens in — the combined figure is therefore the operador's whole visible history.

  Every family publishes its awarded amount **including VAT** — contratos menores at award,
  licitacións at resolución — so the combined total sums figures on **one basis** and is
  labelled **VAT-inclusive**, exactly as each section labels its own. This is stated rather
  than left to each family because a cross-family total is the whole point of the history, and
  a total silently mixing VAT bases would be a number no one should act on. A family that could
  not supply an amount on this basis could not feed the catalogue.
- **R10** — A user can **filter** an operador's history **by year** and **sort** it by
  **date** or by **amount**, ascending or descending. Both act **within one family section**,
  on that section alone: sections are independently reachable (R9), so scoping one leaves the
  others as they were. Filtering, sorting, counting and totalling apply to the whole selection
  of that section, not only to the page currently displayed.

  Unlike an Órgano's contracts, which are always scoped to a single publication year
  ([SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) R18), a year here is **optional**
  and a section opens unfiltered. The bound that makes an unfiltered read acceptable there —
  one Órgano, one year — does not apply to a history that is already bounded to one operador,
  and an operador's whole record across the years is the view this capability exists to give.
- **R11** — Each **family section** of an operador's contract history, and the operadores list
  of R8, are **paginated**, under the same control
  [SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) R16 defines — first, previous,
  next, last or a chosen page, over a selection whose exact size is stated — because a user
  meets all of these lists in the same session and none should page differently from the
  others. Every entry in a section's selection is reachable this way, and the counts and totals
  of R9 describe whole selections rather than the pages on screen. Sections page
  **independently**: advancing one leaves the others where they were. Changing a filter or a
  sort re-pages **that section** from its first page, rather than leaving the user on a page
  number that no longer means what it did.

### Non-functional expectations

- **R12** — Where an operador is a natural person, its name and fiscal identifier are
  **personal data**, and this spec produces genuinely **new derived information** about
  identifiable people that the official source does not publish: R9 assembles into a single
  profile, with running totals, what the source publishes only as isolated per-Órgano
  entries, and R8 makes that profile **searchable by personal fiscal identifier**. The
  catalogue is moreover a **directory**: R8's list plus R11's guarantee that every entry is
  reachable means an authenticated user can page through every operador the system holds,
  roughly one in seven of them a natural person. All three go beyond the source, all three are
  the capability's purpose, and all three are acknowledged here rather than denied.

  The mitigations are that the application is **private, with no public exposure** and every
  read behind authentication (R1); and that identifier lookup matches only whole identifiers
  (R8), so the catalogue cannot be enumerated by feeding it fragments. Two things are recorded
  as **not** mitigations, so nobody mistakes them for protection later: the Scope exclusions
  limit what is added but do nothing about the aggregate, which is the risk named here; and
  R7 is **not** an erasure route — no operador data is removed, by decision.

  Two dependencies this rests on, named because they are outside this spec: the strength of
  the authentication mitigation is the strength of **account provisioning**, which SPEC-0002
  puts out of its own scope and no spec yet owns; and the **export capability** SPEC-0001
  promises and this spec defers must revisit this requirement when it lands, because bulk
  export of a directory is a materially different risk from paging one.
- **R13** — Every value displayed about an operador — its name, its fiscal identifier, and
  every contract attribute in its history — is exactly as the official source published it,
  with no correction, normalisation, inference or enrichment from any other source. The
  matching equivalence of R3 governs comparison only, never display.
- **R14** — The catalogue is expected to hold **hundreds of thousands of operadores** over
  **millions of contracts**, and stays responsive at that volume. It inherits the **reference
  environment** and the **10 concurrent readers** of SPEC-0005 R23, and states its own dataset,
  because that requirement fixes a contract volume and says nothing about how many operadores
  those contracts name or how deep any one history runs: at least **300 000** operadores, of
  which at least one has **10 000** or more contracts spanning **more than one Órgano**. Like
  SPEC-0005 R23, it fixes those conditions and deliberately fixes **no latency budget**: what
  is measured and recorded under them is the **operadores list** — its first page, its count,
  and a page deep into the selection — and an **operador's contract history**: a family
  section's first page, its count and its total, and the **combined figures across every
  section**, which R9 makes a whole-profile number rather than a per-section one and which
  therefore costs one aggregate per family plus their sum. A budget is set once those
  measurements exist.

  Deferring it is a bigger bet here than in SPEC-0005 and should be read as such. That spec
  can point to a bound — one Órgano, one year — that keeps its selections far below its stored
  volume. This one cannot: the operadores list spans the whole catalogue, so its deep pages are
  as deep as the catalogue is large. The measurements above are therefore the first place the
  positional-paging choice will be put under real pressure.

  The operadores **list** carries no per-operador counts or totals — R8 describes it as the way
  to find an operador by name or identifier, and R9 puts the aggregates on the profile, where
  they are computed for one operador rather than for every row of a list hundreds of thousands
  long. A list that displayed them would dominate the cost model and would need R8 to require
  them first.

## Acceptance criteria

1. **(R1)** An authenticated `USER` can view the operadores list and any operador's contract
   history; an unauthenticated visitor that requests either is denied. No surface offers a
   way to create, rename, edit or delete an operador.
2. **(R2)** An operador exists for an awardee only after a contract naming it has been
   imported; no operador exists that no contract names.
3. **(R3)** Two contracts whose published fiscal identifiers differ only in surrounding
   whitespace or letter case yield **one** operador, not two, and its history contains both
   contracts.
4. **(R3)** Two contracts whose published fiscal identifiers differ in any way **other** than
   surrounding whitespace or letter case — including internal spacing, punctuation, or a
   differing character — yield **two** operadores, not one. Over-merging is as much a failure
   as under-merging, and nothing else about an identifier is ignored.
5. **(R3, R13)** Each contract row in an operador's history displays the fiscal identifier
   exactly as published for that contract, including padding and casing the system ignored
   when matching.
6. **(R4)** Two contracts awarded to the same identifier under different published names
   yield one operador shown under the name from the more recently published of them; neither
   name creates a second operador.
7. **(R4)** An operador matched from contracts publishing its identifier with different
   padding and casing is displayed under **one** of those published spellings — the one from
   its most recently published contract — and never under an invented canonical form; two runs
   over the same data choose the same spelling.
8. **(R5)** A contract published with an absent or whitespace-only fiscal identifier is
   stored, appears in its Órgano's list, and displays its awardee name — while creating no
   operador and appearing in no operador's history, and offering no awardee route that leads
   nowhere.
9. **(R5)** A contract published with an irregular but non-empty identifier **is** attached
   to an operador rather than rejected or discarded.
10. **(R6)** An awardee published as a natural person with a personal fiscal identifier is
    catalogued and reachable exactly as a legal entity is; no view distinguishes the two, and
    no stored attribute records which it is.
11. **(R7)** Withdrawing an operador's last remaining visible contract makes the operador
    unreachable: it is afterwards found through neither the operadores list nor lookup by name
    or identifier, and appears on no contract row.
12. **(R7)** Restoring that withdrawn contract makes the same operador reachable again, with
    its history intact — demonstrating that the withdrawal hid it rather than erased it.
13. **(R7)** Withdrawing one of an operador's several contracts leaves the operador reachable
    with the remaining contracts, and its count and total reflect the withdrawal.
14. **(R7)** When a correction changes a contract's published fiscal identifier, that
    contract's contribution moves to the operador the corrected identifier names — creating it
    if no contract named it before — and disappears from the previous operador's history,
    which becomes unreachable if that was its last contract.
15. **(R8)** A user can reach an operador by following the awardee from a contract row, and
    can find the same operador from the operadores list by a partial, case- and
    accent-insensitive fragment of its name, and by its fiscal identifier — including when the
    query is padded or differently cased from the published spelling.
16. **(R8)** A fragment of a fiscal identifier that is not the whole identifier finds no
    operador, so the catalogue cannot be walked by identifier fragments.
17. **(R9)** Opening an operador presents its contracts **split by family, one section per
    family**, with every contract awarded to it across **more than one** Órgano appearing in
    the section of its family; each row shows the awarding Órgano and that family's published
    attributes, and no row repeats the family its section already names.
18. **(R9)** A family the operador holds **no** contracts in shows **no section at all** rather
    than an empty one, and its absence causes no error in the sections that remain.
19. **(R9)** Following the awarding Órgano on a history row opens that Órgano's own page, for
    every row of every section and whichever Órgano awarded it.
20. **(R9)** For an operador holding contracts of **two different families**, each section
    reports its own count and total and the profile reports a **combined** count and total
    across both; the combined figures equal the sum of the sections as currently selected, and
    every figure — per section and combined — is labelled as including VAT, so none mixes VAT
    bases.
21. **(R10)** Filtering **one family section** of an operador's history by a given year returns
    only that section's contracts dated in that year; that section's count and total reflect
    the filtered selection rather than its whole history, the other sections are unchanged, and
    the combined figures of R9 move with it. Clearing the filter restores all of them. A
    section opens with no year filter applied.
22. **(R10)** Sorting **one family section** by date returns its contracts in date order and
    sorting by amount returns them in amount order, in the chosen direction; the first page
    after sorting descending by amount contains the largest-amount contract of that section's
    **whole** filtered selection, not merely the largest of the page previously displayed, and
    the other sections' ordering is unaffected.
23. **(R11)** Both the operadores list and **each family section** of an operador's history are
    paginated: each states how many entries its current selection contains and how many pages it
    spans, a user can move to the first, previous, next and last page and jump to a chosen page,
    and paging through the whole selection yields exactly that many entries with none repeated
    and none skipped. Sections page **independently** — advancing one leaves the others on the
    page they were on. Applying a filter or changing the sort returns the user to the first page
    **of that section**.
24. **(R12)** No surface offers a way to delete or erase an operador, and no function removes
    an operador's name or fiscal identifier from the system.
25. **(R13)** Every operador name, fiscal identifier and contract attribute displayed matches
    what the official source published, with no value corrected, normalised, inferred or
    enriched; no attribute is shown that no contract supplies.
26. **(R14)** Under the reference environment and the dataset R14 states — at least 300 000
    operadores, one of them holding 10 000 or more contracts across more than one Órgano — the
    read latency of the operadores list (its first page, its count, and a page deep into the
    selection) and of an operador's history (a family section's first page, count and total,
    and the combined figures across every section) is **measured and recorded**. The criterion
    is met by those measurements existing against those conditions; it asserts no threshold,
    because R14 sets none until they do.
27. **(R14)** The operadores list displays no per-operador contract count or amount total.
