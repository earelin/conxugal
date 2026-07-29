---
status: draft
---

# SPEC-0005. Import and browse Contratos Menores

## Summary

The system imports the **contratos menores** (minor contracts) published by
[contratosdegalicia.gal](https://www.contratosdegalicia.gal/portada.jsp) for the Órganos de
Contratación an administrator has selected, stores them, and lets authenticated users
browse them. A contrato menor is a low-value contract awarded directly, without a
competitive procedure; the source publishes only the minimum the law requires — object,
amount, duration and awardee — and publishes it **per Órgano**, never as one global list.

Importing is **opt-in per Órgano**: the catalogue holds hundreds of Órganos, many of which
publish no contratos menores at all, so an administrator marks the ones worth importing and
the system retrieves only those. Which Órganos are imported is therefore an
administrator's ongoing decision, managed alongside the rest of the catalogue.

Marking an Órgano starts an **initial import** that loads its entire published history in
one go, so it becomes browsable without waiting for a scheduled run. From then on a
scheduler keeps the Órgano current **incrementally**. The two modes differ in cost by
orders of magnitude — a single large Órgano holds well over a million contracts — so the
spec treats them as distinct operations with distinct expectations.

Users reach contracts by selecting an Órgano de Contratación — from the taxonomy tree or
the catalogue list of
[SPEC-0004](SPEC-0004-import-manage-organos-contratacion.md) — and opening its contracts,
which are presented **split by contract family**: *contratos menores* and *licitacións*.
This spec delivers the contratos menores family only; licitacións are a separate, future
spec that fills the other side of the same split. Within contratos menores a user filters
by **year** and sorts by **date** or **amount**.

Because every contrato menor names its awardee together with a fiscal identifier, the
system also builds a catalogue of **operadores económicos** — the parties that hold these
contracts. Each operador has its own place in the application where its contract history is
visible across all Órganos. That cross-Órgano view is the point of the capability: the
source publishes each Órgano's awards in isolation, so the accumulation of minor contracts
to one supplier is invisible until someone assembles it.

This spec consumes the Órgano catalogue of SPEC-0004 — it is the first consumer of that
spec's `USER`-facing taxonomy tree, and it adds one administrator-managed attribute to each
Órgano: whether its contracts are imported. It follows the roles of
[SPEC-0002](SPEC-0002-user-authentication.md) and the administration split of
[SPEC-0003](SPEC-0003-administration-area.md). It describes the *what*; the retrieval
mechanism, data model, scheduling and pagination technology are decided in ADRs and
features.

### What the source does and does not publish

The requirements below are constrained by what the official source actually makes
available for contratos menores, which is materially less than for licitacións:

- **Published:** a unique publication identifier, the publication date, the object, the
  amount **including VAT**, the awardee's name and the awardee's fiscal identifier, and a
  stated duration.
- **Not published:** **CPV codes**, expediente numbers, amounts excluding VAT, estimated
  value, and place of execution. CPV in particular exists only for licitacións — so this
  spec offers **no CPV filter**, and CPV-based querying arrives with the licitacións spec.
- **Retrievable only in bounded slices:** the source answers per Órgano over a limited date
  range at a time. It offers no "changed since" facility, which is why R8 has to say what
  an incremental run re-reads and R12 has to say that absence proves nothing.
- **Knowingly incomplete:** contracts below 5 000 € paid through *anticipo de caixa fixa*
  are legally exempt from publication, and published history begins around 2018. The
  system reproduces the source; it does not claim to hold every contrato menor ever
  awarded.
- **Weak or inconsistent in places:** the published duration is frequently a per-Órgano
  default rather than a per-contract value; object text quality varies from real
  descriptions to generic budget categories; and text fields, including fiscal identifiers,
  are published with inconsistent padding and casing.

## Scope

**Operadores económicos are owned here permanently.** R19–R21 are deliberately written
family-neutrally: when the licitacións spec lands it extends the same operador catalogue and
the same contract history rather than defining its own, and cites this spec for that
behaviour. The title of this spec names the family it *imports*, not the limit of what it
owns.

Deliberately **out of scope**, each owned elsewhere or by a later spec:

- **Licitacións.** This spec establishes the contract-family split and fills only the
  contratos menores side. Requirements are worded so the licitacións spec adds data without
  invalidating anything here.
- **CPV.** Not published for contratos menores; CPV-based querying belongs to the
  licitacións spec.
- **Contract type (obras / servizos / subministracións) and procedural state.** Not part of
  what the source publishes per Órgano; obtaining them would mean one further retrieval per
  contract against a dataset in the millions.
- **A per-contract detail view.** For contratos menores the list row *is* the detail (R16),
  so SPEC-0001's "inspect their detail" capability is met without a separate screen for
  this family.
- **Exporting results.** SPEC-0001 (`active`) promises export across the contract dataset
  and no requirement here delivers it. It is left to a future export spec, which does not
  yet exist; recorded here so the gap is visible rather than assumed closed.
- **Contracts of Órganos that are inactive.** See the note under R3.

### Decisions this spec leaves open

Three decisions are architecturally significant, are **not** settled here, and should be
recorded as ADRs before the features that would otherwise settle them silently:

1. **How reads are paged over millions of rows** — FEAT-0007 explicitly declined to bind
   this, noting that the contract-querying feature's "volumes are a different order of
   magnitude". R26 states the budget, not the mechanism.
2. **How a long-running, resumable import job holds its state** — required by R9 and R10.
3. **The per-source concurrency and pacing model** — R24 and R27 state obligations;
   [ADR-0014](../architecture/0014-resilient-throttled-outbound-http-client.md) owns the
   bound and makes it configurable per source. This spec must not harden it.

## Requirements

### Access

- **R1** — **Managing** contratos menores is reachable only by users with the `ADMIN` role:
  selecting which Órganos are imported (R3–R5), triggering an import (R22), requesting a
  full historical re-read (R10), and removing or restoring a contract (R13). A `USER` or an
  unauthenticated visitor who attempts any of these is denied (consistent with SPEC-0003
  R1).
- **R2** — **Reading** contratos menores (R14–R18) and operadores económicos with their
  contract history (R19–R21) is available to any authenticated user, `USER` or `ADMIN`.
  These reads grant no ability to modify anything. An unauthenticated visitor is denied —
  which is also the mitigation R28 relies on.

### Selecting which Órganos are imported

- **R3** — Only Órganos that are **both active in the catalogue and marked for import** have
  their contratos menores retrieved. An Órgano that is inactive, or that is not marked, is
  not retrieved from the source at all.
- **R4** — An administrator can **mark and unmark** an Órgano for import and see which
  Órganos are currently marked. An Órgano newly added to the catalogue starts **unmarked**:
  importing is opted into deliberately, never by default. Marking an Órgano starts its
  **initial import** (R8) without the administrator having to trigger one or wait for the
  scheduler.
- **R5** — The mark is an administrator's decision and survives re-imports of the Órgano
  catalogue: reconciling the catalogue against its source (SPEC-0004 R5, R6) never sets or
  clears it. Unmarking an Órgano, or an Órgano becoming inactive, stops **future**
  retrievals for it and stops any retrieval already in progress at a point that loses
  nothing already stored; the contracts already imported are **retained** — they remain
  stored and browsable, and are never deleted.

> Excluding inactive Órganos is a deliberate trade-off, not an oversight. Órganos are
> superseded across legislatures and the superseded ones still hold the history of the
> contracts they awarded, so this rule leaves that history unreached. Reaching it would
> require either reactivating those Órganos or a later decision to import from inactive
> ones; both are outside this spec.

### Importing contratos menores

- **R6** — The system imports the contratos menores published for each Órgano selected under
  R3 and stores each as a record of its own, so contracts are available and queryable
  independently of the source thereafter.
- **R7** — Each stored contrato menor carries the attributes the source publishes — the
  Órgano that awarded it, its **publication date**, its object, its amount, its stated
  duration, and its awardee — together with a stable identity by which the same contract is
  recognised across successive imports. The amount is the published figure **including
  VAT**, and is labelled as such wherever it or any total derived from it is shown: the
  legal thresholds that define a contrato menor are VAT-exclusive, so an unlabelled figure
  invites exactly the wrong comparison.
- **R8** — Routine importing of an Órgano happens in two modes:
  - an **initial import**, run once when the Órgano is marked (R4), which loads its **full
    published history** — every contrato menor the source holds for it, not only recent
    ones;
  - thereafter **incremental imports**, run by the scheduler (R23) or on demand (R22), which
    re-read a **recent window** of publication dates rather than the whole history.

  The window is what makes R11's refresh achievable: the source offers no "changed since"
  facility, so a correction is only discoverable by re-reading the period it falls in.
  Corrections published inside the window are picked up automatically; corrections to older
  publications are not, and R10 is how they are reached. The system knows, per Órgano,
  whether its initial import has completed, so it never treats a half-loaded Órgano as
  though it were up to date.
- **R9** — An initial import is **long-running and interruptible without loss**: a single
  Órgano may hold over a million contracts, so one that fails or is interrupted part-way
  keeps everything it already stored and is **resumed to completion** — automatically, and
  on demand by an administrator — adding no duplicates (R12). It is never left permanently
  incomplete for want of a trigger. While it is in progress an administrator can see that it
  is running and how far it has got.
- **R10** — Beyond those two routine modes, an administrator can request a **full historical
  re-read** of a single Órgano: a re-run of the initial import over an Órgano already loaded.
  This is the only way corrections to publications older than R8's incremental window are
  picked up. It is idempotent (R12) and carries the same cost and resumability as an initial
  import (R9).

### Identity and reconciliation

- **R11** — A re-import reconciles against the stored contracts rather than replacing them:
  a contract new to the source is added; a contract already stored is matched by its
  stable identity and its source-derived attributes are refreshed in place.
- **R12** — Importing is idempotent: importing the same published contracts twice in
  succession leaves the set of stored contracts and their attributes unchanged and creates
  no duplicates. **An import never deletes a stored contract** — a contract absent from a
  later import is retained unchanged, because absence is not evidence of withdrawal.
- **R13** — Because R12 makes absence meaningless, withdrawal has to be explicit: an
  administrator can **remove a stored contract** the source has withdrawn. A removed
  contract is **remembered as removed**: it disappears from every list and every history,
  and a later import that still finds it published does **not** re-add it (which R11 would
  otherwise do). An administrator can restore a removal made in error. Removal is never
  automatic, and it is not the mechanism for a *rectified* publication — a correction inside
  R8's window is refreshed in place by R11, and one outside it is reached by R10.
  Removing the last contract of an operador económico removes that operador with it, so no
  personal data survives the contracts it was derived from; this is how an erasure
  obligation over the data of R28 is discharged.

### Finding and browsing contracts

- **R14** — Any authenticated user selects an Órgano de Contratación either by browsing the
  **read-only taxonomy tree** of SPEC-0004 R9 — which offers a `USER` no control to create,
  rename, move, delete or reassign anything — or from the **catalogue list** of SPEC-0004
  R8, and opens that Órgano's contracts. The tree is the surface SPEC-0004 deferred to this
  spec. Both routes are required because the tree alone is not sufficient: SPEC-0004 R18
  makes every newly imported Órgano **unclassified**, so an Órgano can be marked, imported
  and yet absent from the tree. **Every Órgano whose contracts the system holds is
  reachable**, whether or not it is classified, marked, or still active.
- **R15** — An Órgano's contracts are presented **split by contract family**: *contratos
  menores* and *licitacións*. Each family is reachable independently, and a family for
  which the system holds no data says so rather than appearing broken.
- **R16** — Within an Órgano's contratos menores, a user sees a list showing, for each
  contract, its identifier, its publication date, its object, its amount, its stated
  duration, and its awardee, together with how many contracts the current selection
  contains. A contrato menor has **no separate detail view**: the row carries every
  attribute the system holds for it. Each row also offers a way to reach the corresponding
  publication **at the official source**, so any row can be verified against the original —
  which is what makes the system usable as evidence rather than only as a convenience.
- **R17** — An empty contratos menores list is never ambiguous. The three ways a list can be
  empty are **distinguishable to a user**, not only to an administrator:
  - the Órgano's contracts are **not being imported** (R3);
  - the Órgano's **initial import has not finished**, so what is shown is partial (R9);
  - the Órgano **was imported and awarded none**.

  An Órgano that was imported and has since been unmarked or become inactive keeps showing
  the contracts retained under R5, and says that it is no longer being updated.
- **R18** — A user can **filter** an Órgano's contratos menores **by the year of the
  publication date**, and **sort** them by **publication date** or by **amount**, ascending
  or descending. Clearing the filter returns the unfiltered list. Filtering, sorting and
  counting apply to the whole selection, not only to the portion currently displayed. No CPV
  filter is offered, because the source publishes no CPV for contratos menores.

### Operadores económicos

- **R19** — The system maintains a catalogue of **operadores económicos** — the parties
  awarded the contracts the system holds — derived from those contracts, with no separate
  import. An operador is identified by the **fiscal identifier** published with the award.
  Awardees are **not all companies**: a significant share are natural persons published with
  a personal fiscal identifier, and both are catalogued and reachable identically. Three
  properties of real published data must be handled rather than assumed away:
  - **Identifier matching.** The source publishes identifiers with inconsistent padding and
    casing. Two awards match when their identifiers are equal ignoring surrounding
    whitespace and letter case. This equivalence governs **matching only** — what is
    displayed is always the value as published (R29). Without it the same operador splits in
    two and the cross-Órgano aggregation this catalogue exists for silently fails.
  - **Name variance.** The same identifier is published under varying names; since R29
    forbids normalising them, the operador is shown under the name from its **most recently
    published contract** (ties broken by the contract identifier), and name variation never
    produces a second operador.
  - **Unusable identifiers.** An identifier is **unusable** when it is absent, or empty once
    surrounding whitespace is ignored. A contract published with an unusable identifier is
    still imported and browsable under its Órgano, and is attributed to **no** operador
    rather than to an invented or placeholder one. Nothing beyond that emptiness test is
    validated: the source publishes irregular but genuine identifiers, and rejecting them
    would discard real awards.
- **R20** — A user can reach an operador económico in two ways: by following the awardee
  from any contract row, and from a **list of operadores** that can be looked up by name or
  by fiscal identifier. Name lookup matches on any part of the name, ignoring letter case
  and accents; identifier lookup matches the whole identifier under the equivalence of R19.
  Without the list, the primary question the capability exists to answer — *what has this
  supplier been awarded?* — could only be asked by first stumbling onto one of its
  contracts.
- **R21** — Opening an operador económico shows its **contract history**: every contract
  awarded to it **across all Órganos**, with the awarding Órgano shown per contract, plus
  the number of contracts and the total amount awarded **for the current selection**. The
  history is filterable and sortable exactly as R18 requires of the Órgano list.

### Triggering imports

- **R22** — An administrator can trigger an import on demand. The trigger states its
  **scope** — every marked, active Órgano, or one chosen Órgano — and runs each covered
  Órgano in the mode R8 dictates for it: initial if its history has never been loaded,
  incremental otherwise. The administrator is shown the outcome: whether it succeeded, how
  many Órganos were covered, how many contracts were added and refreshed, and how many
  operadores económicos are newly known.
- **R23** — The system runs incremental imports automatically on a recurring schedule,
  without any human trigger, so newly published contracts appear without administrator
  action. The scheduler covers every Órgano selected under R3 whose initial import has
  completed, and resumes (R9) any whose initial import is incomplete.
- **R24** — Concurrency is bounded **per Órgano**, not globally: no Órgano is imported by two
  runs at once, but a long-running initial or historical re-read of one Órgano must **not
  indefinitely delay** scheduled incremental imports of the others — every marked Órgano
  whose initial import has completed continues to be brought up to date on a bounded
  cadence while the long run proceeds. A global lock would be unworkable: an initial import
  of a large Órgano runs for far longer than the scheduler's interval, so it would stall
  every other Órgano indefinitely.
- **R25** — An import is resilient to source failure: if the source is unreachable or
  returns an unusable response, the contracts already stored, and the operadores derived
  from them, remain intact and consistent — no partial wipe — and the failure is reported to
  the administrator (for a manual run) or otherwise recorded. Failure while importing one
  Órgano does not discard contracts already imported for other Órganos in the same run, nor
  prevent the remaining Órganos from being imported.

> R22–R25 restate SPEC-0004 R10–R13 with contracts in place of Órganos, and **two deliberate
> divergences**: SPEC-0004 R12's single-run guard is global, whereas R24 here is per-Órgano;
> and SPEC-0004 R13 makes an import strictly all-or-nothing, whereas R25 is per-Órgano.
> Neither is a copy-paste slip — a run spanning many Órganos and millions of records can
> neither be serialised behind one lock nor discarded in full because one Órgano failed.

### Non-functional expectations

- **R26** — The stored dataset is expected to reach **millions of contracts**, and browsing
  stays responsive at that volume. Measured on the deployment's target environment, with at
  least **5 000 000** stored contracts of which at least **1 500 000** belong to a single
  Órgano, and under at least **10 concurrent readers**: an Órgano's contract list, an
  operador's history, and the operadores list of R20 each return their first portion, their
  count, and any totals they display within **1 second at the 95th percentile**, and
  requesting a later portion of the same selection meets the same budget. A user can move
  through the whole selection in bounded portions; how those portions are presented is a
  feature's choice.
- **R27** — The import is **courteous to the public source**: across everything the system
  retrieves — this spec's imports and SPEC-0004's catalogue import alike — its total request
  rate stays within a budget that keeps it a negligible load on a public service, and it
  never fetches as fast as it can. The concurrency bound and the interval are configured per
  source and decided outside this spec; this requirement fixes the obligation, not the
  number. It binds every mode, and binds most sharply during an initial import or a
  historical re-read, which are the largest bursts of traffic the system ever produces.
- **R28** — Where the awardee is a natural person, the fiscal identifier and name are
  **personal data**, and the system produces genuinely **new derived information** about
  them that the source does not publish: R21 assembles into one profile, with running
  totals, what the source publishes only as isolated per-Órgano entries, and R20 makes that
  profile **searchable by personal fiscal identifier**. Both go beyond the source, both are
  the capability's purpose, and both are acknowledged rather than denied. The mitigations
  are that every read requires authentication (R2), and that removal under R13 takes the
  derived operador with the contracts it came from.
- **R29** — Published values are stored and displayed **as published**, with no correction,
  normalisation or inference, and enriched from no other source — the matching equivalence
  of R19 governs comparison only, never display. In particular the stated duration, which
  the source frequently publishes as a per-Órgano default rather than a per-contract value,
  is shown **with an indication that it is unreliable**, so a user is not invited to read it
  as a real contract term.

## Acceptance criteria

1. **(R1)** A `USER` or an unauthenticated visitor that attempts to mark or unmark an Órgano
   for import, trigger an import, request a full historical re-read, or remove or restore a
   contract is denied; an authenticated `ADMIN` is allowed.
2. **(R2)** An authenticated `USER` can view an Órgano's contratos menores and an operador
   económico's contract history; an unauthenticated visitor that requests either is denied.
3. **(R3)** After an import run, an Órgano that is active but **unmarked**, and an Órgano
   that is marked but **inactive**, both have no contratos menores stored from that run;
   only Órganos that are active **and** marked do.
4. **(R4)** An administrator can mark an Órgano, see it listed as marked, and unmark it
   again; an Órgano newly added to the catalogue is unmarked until an administrator marks
   it.
5. **(R4, R8)** Marking an Órgano results in its full published history being imported
   without any further trigger and without waiting for the scheduler; afterwards the system
   records that Órgano's initial import as complete.
6. **(R5)** Re-importing the Órgano catalogue leaves every Órgano's marked/unmarked state
   exactly as the administrator set it.
7. **(R5, R17)** Unmarking an Órgano that already has imported contracts leaves those
   contracts stored and browsable, a subsequent import retrieves nothing further for it, and
   the list says the Órgano is no longer being updated.
8. **(R5)** Unmarking an Órgano whose import is in progress stops that import without losing
   any contract it had already stored.
9. **(R6, R7, R16)** After an Órgano's initial import completes, a user viewing its
   contratos menores sees every contract the source published for it, each row showing its
   identifier, publication date, object, amount, duration and awardee — every attribute the
   system holds — with no per-contract screen to open for further data.
10. **(R7)** Every displayed amount, and every total derived from amounts, is labelled as
    including VAT.
11. **(R8)** An Órgano's initial import yields contracts published in years before the
    current one, not only recent ones.
12. **(R8)** A scheduled import of an Órgano whose initial import has completed re-reads only
    a recent window, not the whole history, and picks up a correction published inside that
    window. An Órgano whose initial import has not completed is not treated as up to date by
    the scheduler.
13. **(R9)** An initial import interrupted part-way retains the contracts it already stored,
    and is resumed to completion **without an administrator having to intervene**; an
    administrator can also resume it on demand. Resumption adds no duplicates. While it runs,
    an administrator can see that it is in progress and how far it has got.
14. **(R10)** An administrator can request a full historical re-read of an already-loaded
    Órgano; a correction to a publication **older** than the incremental window is picked up
    by that re-read and by no routine run, and the re-read creates no duplicates.
15. **(R11)** Re-importing after a contract's published attributes change updates that
    contract in place: its identity is unchanged and the refreshed attributes are shown.
16. **(R12)** Running two imports of the same published contracts in succession yields the
    same stored set with no duplicates and no attribute changes; a contract stored by an
    earlier import that is absent from a later import's results is still present and
    unchanged afterwards.
17. **(R13)** An administrator can remove a stored contract, after which it appears in no
    Órgano list and no operador history; a subsequent import that still finds it published
    does not re-add it; and an administrator can restore it.
18. **(R13)** Removing the last remaining contract of an operador económico removes that
    operador, so its name and fiscal identifier are no longer reachable anywhere.
19. **(R14)** A `USER` reaches an Órgano's contracts by browsing the taxonomy tree and
    selecting an Órgano from it; that tree offers the `USER` no control to create, rename,
    move, delete or reassign anything. *(Also satisfies SPEC-0004 #9 and the deferred half
    of SPEC-0004 #2.)*
20. **(R14)** An Órgano that is marked and imported but **unclassified** — placed in no
    taxonomy term — is still reachable and its contracts are still viewable; so is one that
    has since become inactive but retains contracts under R5.
21. **(R15)** Opening an Órgano presents its contracts split into *contratos menores* and
    *licitacións*; a family for which the system holds no data is reachable and states that
    no data is available rather than erroring.
22. **(R16)** An Órgano's contratos menores list states how many contracts the current
    selection contains, and that count matches the number of contracts reachable by moving
    through the selection.
23. **(R16)** Each contract row offers a way to reach that contract's publication at the
    official source.
24. **(R17)** A user can tell apart, without administrator access, an Órgano that is not
    being imported, an Órgano whose initial import is still running (where what is shown is
    partial), and an Órgano that was fully imported and awarded no contratos menores. All
    three present differently.
25. **(R18)** Filtering an Órgano's contratos menores by a given year returns only contracts
    whose publication date falls in that year; clearing the filter restores the full list. No
    CPV filter control is present.
26. **(R18)** Sorting by publication date returns contracts in date order, and sorting by
    amount returns them in amount order, in the chosen direction; the first portion after
    sorting descending by amount contains the largest-amount contract of the **whole**
    filtered selection, not merely the largest of the previously displayed portion.
27. **(R19)** Two contracts whose published fiscal identifiers differ only in surrounding
    whitespace or letter case yield **one** operador, not two — even when the published names
    differ — and the name shown is the one from the most recently published of them, while
    each contract row still displays its own identifier exactly as published.
28. **(R19)** An awardee published as a natural person with a personal fiscal identifier is
    catalogued as an operador económico in the same way as a legal entity, and is reachable
    through the same views.
29. **(R19)** A contract published with an absent or whitespace-only fiscal identifier is
    imported and appears in its Órgano's list, attached to no operador and creating no
    placeholder operador; a contract with an irregular but non-empty identifier **is**
    attached to an operador rather than rejected.
30. **(R20)** A user can reach an operador by following the awardee from a contract row, and
    can find it from the operadores list by a partial, case- and accent-insensitive fragment
    of its name, and by its full fiscal identifier.
31. **(R21)** Opening an operador económico shows every contract awarded to it across **more
    than one** Órgano, with the awarding Órgano shown per contract, and reports the number of
    contracts and the total amount awarded; with a year filter applied, both figures reflect
    the filtered selection and equal the sum over the listed contracts.
32. **(R21)** An operador's history can be filtered by year and sorted by publication date or
    amount, with the same behaviour criteria 25 and 26 require of the Órgano list.
33. **(R22)** An administrator can trigger an import scoped to all marked Órganos and one
    scoped to a single Órgano; each covered Órgano runs initially if its history was never
    loaded and incrementally otherwise, and the reported outcome states success, Órganos
    covered, contracts added and refreshed, and operadores newly known.
34. **(R23)** With no human trigger, the scheduler runs and contracts published since the
    previous run become browsable for every marked, active, initially-imported Órgano.
35. **(R24)** A second run for an Órgano already being imported does not start; meanwhile,
    while a multi-hour initial import of one Órgano is running, scheduled incremental imports
    of other Órganos start and complete on their normal cadence rather than waiting for it.
36. **(R25)** When the source is unreachable or returns an unusable response for one Órgano,
    the import reports failure for it, contracts already stored are unchanged, contracts
    imported for Órganos processed earlier in the same run are retained, and the remaining
    marked Órganos are still imported.
37. **(R26)** Under the stated dataset, environment and concurrency conditions, an Órgano's
    contract list, an operador's history, and the operadores list each return their first
    portion, their count and their totals within 1 s at the 95th percentile, and requesting a
    later portion of the same selection meets the same budget.
38. **(R27)** Across a period covering an initial import, a historical re-read and scheduled
    incremental runs, the system's request rate against the source stays within the
    configured budget, and no mode exceeds it.
39. **(R28)** No operador's history, totals, or identifier lookup is reachable without
    authentication.
40. **(R29)** Every awardee name, fiscal identifier, amount and duration displayed matches
    what the official source publishes for that contract — including identifiers whose
    padding or casing the system ignored when matching — with no value corrected, normalised,
    inferred, or enriched from elsewhere.
41. **(R29)** A displayed duration is accompanied by an indication that the source frequently
    publishes a per-Órgano default rather than a per-contract value.
