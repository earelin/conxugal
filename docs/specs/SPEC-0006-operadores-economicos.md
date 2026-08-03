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
facts about the contracts it supplies, and about itself. Stated here rather than left
implicit, because a family that
cannot supply them cannot feed this catalogue, and that is worth discovering while writing
that family's spec rather than while building it:

- an **awardee name** and an **awardee fiscal identifier**, as published (R3, R4);
- a single **publication date** per contract, comparable across families — R4's most-recent
  rule, R10's year filter and R10's date sort all order contracts of different families against
  one another. A family may publish dates it cannot interpret; R4 and R10 say what becomes of
  those, so the fact required is the date **as published**, not a guarantee that every one of
  them is interpretable;
- an **amount**, on the VAT basis R9 fixes;
- the **awarding Órgano**;
- a **stable contract identity** that is **totally ordered**, and ordered consistently across
  families — R4 breaks its tie by taking the **higher** identity, which needs more than the
  stability [SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) R7 promises. Any total
  order will do provided it is deterministic and agreed between families; the requirement is
  that one is stated, not which;
- an **explicit removal rule** that is **non-destructive and reversible** — a family must say
  what it means for one of its contracts to be withdrawn, and withdrawal must hide the contract
  rather than delete it, and be undoable. R7's lifecycle hooks onto it, and R7's promise that an
  operador is hidden rather than erased — the load-bearing half of R12's privacy analysis — is
  only true if **every** feeding family keeps this one. A family that can offer only a hard
  delete cannot feed this catalogue, in the same way and for the same reason as one that cannot
  supply an amount on R9's VAT basis;
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

One decision has since been taken:

- **Whether the catalogue is stored state or computed on read**, which this spec left open as
  architecturally significant, is settled by
  [ADR-0018](../architecture/0018-operadores-as-a-stored-projection.md): the catalogue is
  **stored state maintained by the import**, with each contract carrying a foreign key to its
  operador. That record is `proposed`, so a feature building directly onto it should confirm its
  status first. What the choice decides — whether R7's lifecycle happens automatically or has to
  be driven, and whether R14's reads are viable over hundreds of thousands of operadores — is
  argued there, not here.

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
- **R3** — An operador is identified by the **fiscal identifier** published with the award,
  held in a **canonical form**: surrounding whitespace removed and letters upper-cased. Two
  awards name the same operador when their identifiers are **equal in that form**, so
  `b12345678`, ` B12345678 ` and `B12345678` are one operador holding `B12345678`. The
  canonical form is the identity: it is what the catalogue is unique on, and there is **one**
  fiscal identifier per operador rather than a matching value and a displayed one.

  The equivalence also governs **what a user types**: R8's identifier lookup canonicalises the
  query the same way, so a padded or differently-cased query still finds the operador.

  **Nothing beyond whitespace and case is touched.** Internal spacing, punctuation and any
  differing character make a different identifier and therefore a different operador — the
  reduction is exactly these two things, because merging two real suppliers is as damaging as
  splitting one, and the cross-Órgano aggregation this spec exists for fails silently either way.

  **This is a deliberate exception to R13, and R13 states it.** The published letter case is not
  retained anywhere, so an operador is displayed under the canonical form rather than under any
  spelling a contract actually carried. That is accepted because case is the one difference R3
  declares meaningless for identity: keeping a spelling the system has already ruled
  non-distinguishing, purely to display it, would mean holding **two representations of one
  identifier** on every row — and a reader picking the wrong one would breach R13 in the display
  or R3 in the matching.
- **R4** — The same identifier is published under **varying names**, and an operador is shown
  under the **name** taken from its **most recently published** contract — ties broken by taking
  the **higher** contract identifier, so the choice is deterministic and not merely "some
  tie-break". Name variation never produces a second operador.

  **The rule covers the name only.** R3 holds one canonical fiscal identifier per operador, so
  there is no identifier spelling to choose between; what a contract published in a different
  case is not a variant this rule ranks, it is the same identifier written differently.

  A feeding family may hold contracts whose **publication date cannot be interpreted**
  ([SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) R27 stores them rather than
  rejecting them), and "most recently published" cannot order those. They are therefore
  **ranked last** for this rule, behind every contract that has an interpretable date, and the
  higher-contract-identifier tie-break settles among them — so an operador all of whose
  contracts are undated is still shown under exactly one deterministic name, and one
  undated contract never displaces a dated one. This keeps R4 total: every operador has a
  name, whatever its contracts' dates look like.
- **R5** — An identifier is **unusable** when it is absent, or empty once surrounding
  whitespace is ignored. Such a contract yields **no** operador — never an invented or
  placeholder one — while remaining stored and browsable, showing **no awardee at all**, since
  under [SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) R7 the awardee is held on the
  operador that award did not produce (SPEC-0005 #11). Nothing
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
  reachable again. R4's name follows from whatever the contracts say after the change, not from
  what they said when the operador first appeared; the canonical identifier of R3 is unchanged by
  construction, being reached from every contract identically.

- **R15** — The system retains **every name an operador has been published under**, not only
  the one R4 selects. The R4 winner is the operador's **principal name**; every other distinct
  name its contracts have published is retained as an **alternative name**, each with the **most
  recent date on which a contract published it** and the identifier of the contract that did so.

  A name is retained **once per operador however many contracts publish it** — the retained fact
  is *this operador has been known by this name, most recently then*, not one entry per award.
  Distinctness is by the name exactly as published: R13 forbids normalising it, and two spellings
  that differ are two names, on the same reasoning that keeps R3's match rule off everything but
  whitespace and case.

  **The date and contract identifier are retained because R4's order needs both.** R4 ranks by
  publication date and breaks ties on the higher contract identifier, and ranks a contract whose
  date cannot be interpreted last; a name carrying only a date could not be ordered against
  another sharing it, and two names seen only on undated contracts could not be ordered at all.
  Retaining both means the principal name and the alternatives are ordered by **one rule**, so
  the history can never disagree with R4 about which name should be showing.

  **What this makes possible, and what it does not.** R7 requires the catalogue to re-derive as
  the contracts currently stand; when a correction or withdrawal demotes the contract that won
  R4, the principal name has to fall back to whatever now ranks highest.
  [ADR-0018](../architecture/0018-operadores-as-a-stored-projection.md) records that this cannot
  be done from stored data today and names it as the projection's real price. With the names
  retained, the fallback is a choice among rows the system already holds rather than a re-read of
  every contract. **It does not restore per-contract spelling.** R13 shows an operador's history
  under one name, and knowing a name and the last contract to publish it is not knowing which of
  the others carried it,
  so #25 is unaffected: this retains the *set* of names an operador has borne, not a per-row
  record.

  **The fiscal identifier needs no equivalent.** R3 holds one canonical identifier per operador,
  derived from every contract identically, so there is no published spelling that could go stale
  and nothing to demote — the whole class of problem this requirement solves for names does not
  arise for identifiers. Retention is a name-only concern precisely because canonicalisation
  settled the other half.

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

  The list is ordered by **display name (R4) ascending**, compared ignoring letter case and
  accents as name lookup is, with the **fiscal identifier** breaking ties so the order is total
  and two runs over the same data agree. The ordering is **fixed**: the list exists to find an
  operador, and R14 keeps per-operador figures off it, so there is nothing on it worth sorting
  by. An order has to be stated because R11's paging is over an ordered selection — without one,
  *last page* does not denote and exhaustive paging cannot be shown.

  Without the list, the primary question this capability exists to answer — *what has this
  supplier been awarded?* — could only be asked by first stumbling onto one of its contracts.

### Contract history

- **R9** — Opening an operador shows its **contract history**: every contract **the system
  holds** that was awarded to it, across all Órganos and all contract families, presented
  **split by contract family**,
  one section per family, under the same rule
  [SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) R15 fixes for an Órgano's
  contracts — each family reachable independently, a family the operador holds no contracts in
  **omitted** rather than shown empty, and the split **additive** as further families are
  gained. The rule is cited rather than restated because a user meets both presentations in the
  same session, and an operador's history should not be organised differently from the Órgano
  page they reached it from.

  *"Every contract the system holds"* is deliberately narrower than *every contract awarded*,
  and the difference is disclosed rather than left for a user to discover. Import is **opt-in
  per Órgano**, so an award by an Órgano nobody marked is absent; and the source is itself
  knowingly incomplete. The history therefore **states that it covers only imported Órganos**,
  and a section **says it is still filling** while any Órgano contributing to it has an
  unfinished initial import — the same obligation
  [SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) R18 places on an Órgano's own
  section, and for the same reason: a user must not read a growing list as a complete one. It
  matters more here, because this spec attaches a **total amount awarded** to that list, and a
  total is acted on in a way a list of rows is not.

  Each section shows, per contract, the awarding Órgano and the same published attributes that
  family shows in its own list — including, where that family offers one, the route to the
  contract's publication at the official source, so a row here is as verifiable as a row there.
  A row does **not** repeat the family it belongs to: the section it sits in already names it.
  It **does** state its awardee, even though every row on this page was awarded to the operador
  the page is about, so that a row carries its own meaning: a row copied, cited or read out of
  the surrounding page still says who was paid, exactly as it does on an Órgano's list. It is not
  stating a per-contract variant — R3 holds one canonical identifier and R4 one name, and #25
  requires every row to show those same two.

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
  section's figures and the combined figures move together.

  Because sections scope independently (R10), the combined figure can span a 2023-only section
  and an unfiltered one, and **it states the scoping it was computed under** rather than
  presenting itself as everything. A total that silently mixed year scopes would fail exactly
  the test that makes this spec label VAT bases.

  A contract whose published **amount cannot be interpreted** is counted but cannot be summed
  ([SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) R27 stores such contracts rather
  than rejecting them). Where any exist in a selection, **the total says how many contracts it
  could not include**, so a count and a total that disagree are explained rather than merely
  inconsistent. Dropping those contracts from the count instead would hide real awards, and
  reporting a total that silently omits them is the same defect as one that silently mixes VAT
  bases.

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

  A section offers **only the years it actually has contracts in**, so choosing a year is never
  the reason a section is empty — the same rule
  [SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) R19 applies to an Órgano's years,
  adopted here so a section is never rendered empty and R9's omission rule stays a statement
  about families rather than about selections.

  Unlike an Órgano's contracts, which are always scoped to a single publication year (R19
  there), a year here is **optional** and a section opens unfiltered. The bound that makes an
  unfiltered read acceptable there — one Órgano, one year — does not apply to a history that is
  already bounded to one operador, and an operador's whole record across the years is the view
  this capability exists to give. Contracts whose publication date cannot be interpreted belong
  to no year and are therefore visible in the unfiltered section and in none of the year
  filters, which is how they stay reachable without a separate affordance.
- **R11** — Each **family section** of an operador's contract history, and the operadores list
  of R8, are **paginated**, under the same control
  [SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) R17 defines — first, previous,
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

**Authentication is the only real mitigation**, and it is recorded as such rather than
  padded out. The application is **private, with no public exposure**, and every read sits
  behind it (R1). That the application is not publicly exposed is a **deployment property no
  requirement here can enforce** — named as a third external dependency alongside the two below,
  not claimed as something this spec delivers.

  Whole-identifier lookup (R8) is **not** a second mitigation of the same risk, and this spec
  no longer presents it as one. It cannot be: R8's list and R11's guarantee that every entry is
  reachable together let an authenticated user page the entire directory and open each entry,
  where R13 displays the identifier in full. What whole-identifier matching does prevent is
  narrower and worth keeping — **probing the identifier space**, confirming whether a given
  identifier is held without already knowing it, and harvesting by feeding fragments — so it is
  a speed bump against a specific technique, not a barrier around the data. Relaxing it to a
  prefix match would remove that, and would still not be what stands between this catalogue and
  an authenticated reader.

  Two further things are recorded as **not** mitigations, so nobody mistakes them for protection
  later: the Scope exclusions limit what is added but do nothing about the aggregate, which is
  the risk named here; and R7 is **not** an erasure route — no operador data is removed, by
  decision.

  Three dependencies this rests on, named because they are outside this spec: the strength of
  the authentication mitigation is the strength of **account provisioning**, which SPEC-0002
  puts out of its own scope and no spec yet owns; the **private deployment** named above, which
  no requirement in any spec asserts and no criterion covers; and the **export capability**
  SPEC-0001 promises and this spec defers must revisit this requirement when it lands, because
  bulk export of a directory is a materially different risk from paging one.
- **R13** — Every value displayed about an operador — its name, its fiscal identifier, and
  every contract attribute in its history — is exactly as the official source published it,
  with no correction, normalisation, inference or enrichment from any other source, save the two
  exceptions below. R3's canonical form governs both the matching **and** the display of the
  fiscal identifier; it governs nothing else.

  **Surrounding whitespace is not covered by "exactly".** The source pads its text
  fields out to fixed widths, and
  [SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) R27 strips that padding as the value
  enters the system, on the grounds that it is an artefact of serialisation rather than something
  published. So an operador the source pads out to twenty characters is displayed under
  `33545498K`, never under that value with its eleven trailing spaces.

  **The fiscal identifier's letter case is the one further exception, and R3 makes it.** The
  identifier is held in R3's canonical form — trimmed and upper-cased — so an operador published
  as `b12345678` is displayed as `B12345678`. The published case is retained nowhere. This is the
  only value about an operador that is displayed in a form no contract necessarily carried, and
  it is accepted because case is precisely the difference R3 declares meaningless for identity:
  retaining a spelling the system has already ruled non-distinguishing, only in order to show it,
  would put **two representations of one identifier** on every row, where picking the wrong one
  breaches this requirement in the display or R3 in the matching. **Everything else about the
  identifier is untouched** — internal spacing and punctuation are as published, and no other
  value is canonicalised anywhere.

  **Names are not canonicalised.** Internal spacing, casing and punctuation in a name are
  displayed exactly as published, which is the variance this requirement exists to preserve, and
  R15 retains every name an operador has borne rather than folding them together.

  **"As published" means published somewhere, not published on that row.** The awardee's name is
  stored **once**, on the operador
  ([SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) R7), under the single spelling R4
  selects; a history row therefore shows that spelling rather than the one its own contract
  carried. Every name shown is still one the source published for that awardee — none is
  invented or merged from several — but the variance between them is not shown per row, and a
  feature may not present a name the system does not hold. The same narrowing applies to
  a contract's publication date, which SPEC-0005 R27 stores interpreted.

  A family's own caveats travel with its attributes into this history. Where a family shows an
  attribute **marked unreliable** in its own list — as contratos menores must for the stated
  duration ([SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) R27, which the source
  frequently publishes as a per-Órgano default) — it is marked the same way here. "As published"
  is not a licence to drop the warning that made the published value safe to show.
- **R14** — The catalogue is expected to hold **hundreds of thousands of operadores** over
  **millions of contracts**, and stays responsive at that volume. It inherits the **reference
  environment** and the **10 concurrent readers** of SPEC-0005 R24, and states its own dataset,
  because that requirement fixes a contract volume and says nothing about how many operadores
  those contracts name or how deep any one history runs. Stated the way SPEC-0005 R24 states
  its own — relative to what production holds, because that is the environment — the conditions
  are: **the largest operador the catalogue holds**, which must itself hold contracts spanning
  **more than one Órgano** and, once a second family exists, **more than one family**, since a
  combined figure measured over a single section is not a measurement of a combined figure at
  all.

  Like SPEC-0005 R24, it fixes those conditions and deliberately fixes **no latency budget**.
  What is measured and recorded under them is:

  - the **operadores list** — its first page, its count, and its **last** page, which R11 makes
    reachable in one click and which is the deepest read the catalogue offers;
  - **name lookup** on that list — a partial, case- and accent-insensitive match, which is the
    single most expensive read this spec defines and the one whose result most directly decides
    the open stored-versus-computed question;
  - an **operador's contract history** — a family section's first page, its count and its
    total; that same section **sorted by amount descending**, on SPEC-0005 R24's reasoning that
    an arbitrary sort over the largest selection is the read that actually breaks and a default
    first page proves nothing about it; and the **combined figures across every section**, which
    R9 makes a whole-profile number and which therefore costs one aggregate per family plus
    their sum.

  A budget is set once those measurements exist, **by revising this requirement**, as SPEC-0005
  R24 does for its own.

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
5. **(R3, R13)** Each contract row in an operador's history displays the operador's **one
   canonical fiscal identifier**, and the same identifier appears on every row. Per-contract
   spelling variance is not stored
   ([SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) R7), and the published letter case
   is retained nowhere, so no row shows a spelling the system does not hold.
6. **(R4)** Two contracts awarded to the same identifier under different published names
   yield one operador shown under the name from the more recently published of them; neither
   name creates a second operador.
7. **(R3)** An operador matched from contracts publishing its identifier with different padding
   and casing holds and displays **one** identifier — trimmed and upper-cased — whichever of
   those contracts is imported first, and re-importing them in any order leaves it unchanged.
   The canonical form is reached from every published spelling identically, so no contract's
   arrival can move it.
8. **(R5)** A contract published with an absent or whitespace-only fiscal identifier is
   stored and appears in its Órgano's list showing **no awardee** — the awardee is held on the
   operador that award did not produce
   ([SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) #11) — while creating no
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
    with the remaining contracts; both its own family section's count and total and the
    profile's combined figures reflect the withdrawal.
14. **(R7)** When a correction changes a contract's published fiscal identifier, that
    contract's contribution moves to the operador the corrected identifier names — creating it
    if no contract named it before — and disappears from the previous operador's history,
    which becomes unreachable if that was its last contract.
15. **(R8)** A user can reach an operador by following the awardee from a contract row, and
    can find the same operador from the operadores list by a partial, case- and
    accent-insensitive fragment of its name, and by its fiscal identifier — including when the
    query is padded or differently cased from the canonical form it holds.
16. **(R8)** Querying a fragment of a held fiscal identifier finds **no operador whose
    identifier merely contains it** — the catalogue cannot be walked by identifier fragments —
    even where that fragment is itself some other operador's whole identifier, which is found
    and is the only thing found.
17. **(R8)** The operadores list is ordered by operador name ascending, ignoring case and
    accents, with the fiscal identifier breaking ties; two runs over the same data produce the
    same order, and no control changes it.
18. **(R9)** Opening an operador presents its contracts **split by family, one section per
    family**, with every contract awarded to it across **more than one** Órgano appearing in
    the section of its family; each row shows the awarding Órgano and that family's published
    attributes, and no row repeats the family its section already names.
19. **(R9)** A family the operador holds **no** contracts in shows **no section at all** rather
    than an empty one, and its absence causes no error in the sections that remain.
20. **(R9)** Following the awarding Órgano on a history row opens that Órgano's own page, for
    every row of every section and whichever Órgano awarded it.
21. **(R9)** For an operador holding contracts of **two different families**, each section
    reports its own count and total and the profile reports a **combined** count and total
    across both; the combined figures equal the sum of the sections as currently selected, and
    every figure — per section and combined — is labelled as including VAT, so none mixes VAT
    bases.
22. **(R9)** With one section filtered to a year and another unfiltered, the combined figures
    state the scoping they were computed under rather than presenting themselves as the
    operador's whole history.
23. **(R9)** A selection containing a contract whose published amount cannot be interpreted
    counts that contract and reports, alongside the total, how many contracts the total could
    not include — so count and total never disagree without saying why.
24. **(R9)** The history states that it covers only imported Órganos, and a section whose
    contributing Órganos include one with an unfinished initial import says it is still
    filling — distinguishably from one that is complete.
25. **(R9, R13)** Each history row states its awardee under the one spelling R4 selects for the
    operador, matching the profile above it. A row never shows a spelling that differs from it,
    because no other spelling is stored.
26. **(R10)** Filtering **one family section** of an operador's history by a given year returns
    only that section's contracts dated in that year; that section's count and total reflect
    the filtered selection rather than its whole history, the other sections are unchanged, and
    the combined figures of R9 move with it. Clearing the filter restores all of them. A
    section opens with no year filter applied, offers exactly the years it has contracts in, and
    shows any contract whose date cannot be interpreted while unfiltered and under no year.
27. **(R10)** Sorting **one family section** by date returns its contracts in date order and
    sorting by amount returns them in amount order, in the chosen direction; the first page
    after sorting descending by amount contains the largest-amount contract of that section's
    **whole** filtered selection, not merely the largest of the page previously displayed, and
    the other sections' ordering is unaffected.
28. **(R11)** Both the operadores list and **each family section** of an operador's history are
    paginated: each states how many entries its current selection contains and how many pages it
    spans, a user can move to the first, previous, next and last page and jump to a chosen page,
    and paging through the whole selection yields exactly that many entries with none repeated
    and none skipped, under the ordering that selection states. Sections page
    **independently** — advancing one leaves the others on the page they were on. Applying a
    filter or changing the sort returns the user to the first page **of that section**.
29. **(R12)** No surface offers a way to delete or erase an operador, and no function removes
    an operador's name or fiscal identifier from the system.
30. **(R13)** Every operador name and contract attribute displayed matches what the official
    source published **once its surrounding whitespace is removed**
    ([SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) R27), with no value otherwise
    corrected, normalised, inferred or enriched — internal spacing, casing and punctuation
    displayed exactly as published; no attribute is shown that no contract supplies. The
    **fiscal identifier is the single exception**, displayed in R3's canonical upper-cased form,
    and no other value is canonicalised anywhere.
31. **(R14)** Under the reference environment, dataset and concurrency conditions R14 states,
    the read latency of **every read R14 names** is **measured and recorded**: the operadores
    list — its first page, its count and its **last** page; **name lookup** on that list, a
    partial, case- and accent-insensitive match; and an operador's contract history — a family
    section's first page, count and total, that same section **sorted by amount descending**,
    and the combined figures across every section. The criterion is met by those measurements
    existing and being recorded against those conditions; it asserts no threshold, because R14
    sets none until they do.
32. **(R14)** The operadores list displays no per-operador contract count or amount total.
33. **(R15)** An operador whose contracts publish three different names retains **all three** —
    the R4 winner as its principal name and the other two as alternatives — and an operador whose
    contracts all publish the same name retains that name once, with **no** alternative beside it.
34. **(R15)** A name published by many of an operador's contracts is retained **once**, carrying
    the publication date and contract identifier of the **most recent** contract that published
    it; importing a further contract under that same name advances those two and adds no second
    entry.
35. **(R15)** Two names differing only in letter case or internal spacing are retained as **two**
    names, not merged — R13 forbids normalising a published name, and the retention is by the name
    exactly as published.
36. **(R15)** Ordering an operador's principal name against its alternatives by publication date,
    then by higher contract identifier, with undated contracts last, puts the **principal name
    first** — the retained data and R4 agree by construction, including when the top two names
    share a date and when every name comes from an undated contract.
37. **(R15)** Re-importing contracts the system already holds leaves the retained names, their
    dates and their contract identifiers **unchanged** — the retention is idempotent, as the
    catalogue it belongs to is.
