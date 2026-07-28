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

Users reach contracts by **browsing the Órgano taxonomy tree of
[SPEC-0004](SPEC-0004-import-manage-organos-contratacion.md)**, selecting an Órgano, and
opening its contracts, which are presented **split by contract family**: *contratos
menores* and *licitacións*. This spec delivers the contratos menores family only;
licitacións are a separate, future spec that fills the other side of the same split. Within
contratos menores a user filters by **year** and sorts by **date** or **amount**.

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
  an incremental run re-reads and R11 has to say that absence proves nothing.
- **Knowingly incomplete:** contracts below 5 000 € paid through *anticipo de caixa fixa*
  are legally exempt from publication, and published history begins around 2018. The
  system reproduces the source; it does not claim to hold every contrato menor ever
  awarded.
- **Weak in places:** the published duration is frequently a per-Órgano default rather than
  a per-contract value, and object text quality varies from real descriptions to generic
  budget categories.

## Scope

Deliberately **out of scope**, each owned elsewhere or by a later spec:

- **Licitacións.** This spec establishes the contract-family split and fills only the
  contratos menores side.
- **CPV.** Not published for contratos menores; CPV-based querying belongs to the
  licitacións spec.
- **Contract type (obras / servizos / subministracións) and procedural state.** Not part of
  what the source publishes per Órgano; obtaining them would mean one further retrieval per
  contract against a dataset in the millions.
- **A per-contract detail view.** For contratos menores the list row *is* the detail (R15),
  so SPEC-0001's "inspect their detail" capability is met without a separate screen for
  this family.
- **Exporting results.** SPEC-0001 promises export across the contract dataset; no
  requirement here delivers it, and it is left to the spec that owns export.
- **Contracts of Órganos that are inactive.** See the note under R3.

## Requirements

### Access

- **R1** — **Managing** contratos menores — selecting which Órganos are imported (R3–R5),
  triggering an import (R21), and removing a withdrawn contract (R12) — is reachable only
  by users with the `ADMIN` role; a `USER` or an unauthenticated visitor who attempts any of
  these is denied (consistent with SPEC-0003 R1).
- **R2** — **Reading** contratos menores (R13–R17) and operadores económicos with their
  contract history (R18–R20) is available to any authenticated user, `USER` or `ADMIN`.
  These reads grant no ability to modify anything. An unauthenticated visitor is denied —
  which is also the mitigation R27 relies on.

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
  retrievals for it but **retains** the contracts already imported — they remain stored and
  browsable, and are never deleted.

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
- **R8** — Importing an Órgano happens in two distinct modes:
  - an **initial import**, run once when the Órgano is marked (R4), which loads its **full
    published history** — every contrato menor the source holds for it, not only recent
    ones;
  - thereafter **incremental imports**, run by the scheduler (R22) or on demand (R21), which
    re-read a **recent window** of publication dates rather than the whole history.

  The window is what makes R10's refresh achievable: the source offers no "changed since"
  facility, so a correction is only discoverable by re-reading the period it falls in.
  Corrections published inside the window are therefore picked up automatically;
  corrections to older publications are not, and an administrator can re-run a full
  historical read of an Órgano to pick them up. The system knows, per Órgano, whether its
  initial import has completed, so it never treats a half-loaded Órgano as though it were
  up to date.
- **R9** — An initial import is **long-running and interruptible without loss**: a single
  Órgano may hold over a million contracts, so an initial import that fails or is
  interrupted part-way keeps everything it already stored and can be resumed or retried to
  completion, adding no duplicates (R11). While it is in progress an administrator can see
  that it is running and how far it has got, and a marked Órgano whose initial import has
  not yet finished shows the contracts loaded so far rather than nothing.

### Identity and reconciliation

- **R10** — A re-import reconciles against the stored contracts rather than replacing them:
  a contract new to the source is added; a contract already stored is matched by its
  stable identity and its source-derived attributes are refreshed in place.
- **R11** — Importing is idempotent: importing the same published contracts twice in
  succession leaves the set of stored contracts and their attributes unchanged and creates
  no duplicates. **An import never deletes a stored contract** — a contract absent from a
  later import is retained unchanged, because absence is not evidence of withdrawal.
- **R12** — Because R11 makes absence meaningless, withdrawal has to be explicit: an
  administrator can **remove a stored contract** that the source has withdrawn or rectified.
  This is the only way a contract leaves the system, and it is also how an erasure
  obligation over the personal data of R27 is discharged. Removal is never automatic.

### Finding and browsing contracts

- **R13** — Any authenticated user selects an Órgano de Contratación by browsing the
  **read-only taxonomy tree** of SPEC-0004 R9 — the tree offers a `USER` no control to
  create, rename, move, delete or reassign anything — and opens that Órgano's contracts.
  This is the surface SPEC-0004 deferred to this spec.
- **R14** — An Órgano's contracts are presented **split by contract family**: *contratos
  menores* and *licitacións*. Each family is reachable independently, and a family for
  which the system holds no data says so rather than appearing broken. Only the contratos
  menores family carries data within this spec.
- **R15** — Within an Órgano's contratos menores, a user sees a list showing, for each
  contract, its identifier, its publication date, its object, its amount, its stated
  duration, and its awardee, together with how many contracts the current selection
  contains. A contrato menor has **no separate detail view**: the row carries every
  attribute the system holds for it. Each row also offers a way to reach the corresponding
  publication **at the official source**, so any row can be verified against the original —
  which is what makes the system usable as evidence rather than only as a convenience.
- **R16** — An Órgano whose contracts are **not imported** (R3) is distinguishable from one
  that was imported and awarded none: the empty list states that this Órgano's contracts are
  not being imported, rather than implying it awarded no contratos menores. An Órgano that
  was imported and has since been unmarked or become inactive keeps showing the contracts
  retained under R5.
- **R17** — A user can **filter** an Órgano's contratos menores **by the year of the
  publication date**, and **sort** them by **publication date** or by **amount**, ascending
  or descending. Clearing the filter returns the unfiltered list. Filtering, sorting and
  counting apply to the whole selection, not only to the portion currently displayed. No CPV
  filter is offered, because the source publishes no CPV for contratos menores.

### Operadores económicos

- **R18** — The system maintains a catalogue of **operadores económicos** — the parties
  awarded the contracts the system holds, which today means contratos menores only —
  derived from those contracts, with no separate import. An operador is identified by the
  **fiscal identifier** published with the award. Awardees are **not all companies**: a
  significant share are natural persons published with a personal fiscal identifier, and
  both are catalogued and reachable identically. Two consequences of real published data
  must be handled rather than assumed away:
  - the same fiscal identifier is published under **varying names**; since R28 forbids
    normalising them, the operador is shown under the **most recently published** name and
    name variation never produces a second operador;
  - a contract whose published fiscal identifier is **absent or unusable** is still imported
    and browsable under its Órgano, and is attributed to **no** operador rather than to an
    invented or placeholder one.
- **R19** — A user can reach an operador económico in two ways: by following the awardee
  from any contract row, and from a **list of operadores** that can be looked up by name or
  by fiscal identifier. Without the second, the primary question the capability exists to
  answer — *what has this supplier been awarded?* — could only be asked by first stumbling
  onto one of its contracts.
- **R20** — Opening an operador económico shows its **contract history**: every contract
  awarded to it **across all Órganos**, with the awarding Órgano shown per contract, plus
  the number of contracts and the total amount awarded **for the current selection**. The
  history is filterable and sortable exactly as R17 requires of the Órgano list.

### Triggering imports

- **R21** — An administrator can trigger an import on demand and is shown its outcome:
  whether it succeeded and a summary of what changed — how many Órganos were covered, how
  many contracts were added and refreshed, and how many operadores económicos are newly
  known.
- **R22** — The system runs incremental imports automatically on a recurring schedule,
  without any human trigger, so newly published contracts appear without administrator
  action. The scheduler covers every Órgano selected under R3 whose initial import has
  completed.
- **R23** — Concurrency is bounded **per Órgano**, not globally: no Órgano is imported by two
  runs at once, but an initial import of one Órgano does **not** block scheduled incremental
  imports of the others. A global lock would be unworkable — an initial import of a large
  Órgano runs for far longer than the scheduler's interval, so it would stall every other
  Órgano indefinitely. The system caps how much it retrieves concurrently so that R26 still
  holds.
- **R24** — An import is resilient to source failure: if the source is unreachable or
  returns an unusable response, the contracts already stored, and the operadores derived
  from them, remain intact and consistent — no partial wipe — and the failure is reported to
  the administrator (for a manual run) or otherwise recorded. Failure while importing one
  Órgano does not discard contracts already imported for other Órganos in the same run, nor
  prevent the remaining Órganos from being imported.

> R21–R24 restate SPEC-0004 R10–R13 with contracts in place of Órganos, and one **deliberate
> divergence**: SPEC-0004 R13 makes an import strictly all-or-nothing, whereas R24 here is
> per-Órgano. That is not a copy-paste slip — a run spanning many Órganos and millions of
> records cannot sensibly be discarded in full because one Órgano failed.

### Non-functional expectations

- **R25** — The stored dataset is expected to reach **millions of contracts**, and browsing
  stays responsive at that volume. With at least **1 000 000** stored contracts, an Órgano's
  contracts and an operador's history return their first portion, their count and their
  totals within **1 second at the 95th percentile**, and moving to a later portion of the
  same selection does not degrade that. A user can move through the whole selection in
  bounded portions; how those portions are presented is a feature's choice.
- **R26** — The import is **courteous to the public source**: it never has more than one
  request in flight to the source and leaves a minimum interval between consecutive
  requests. This binds most sharply during an initial import, which is the largest burst of
  traffic the system ever puts on the source.
- **R27** — Where the awardee is a natural person, the fiscal identifier and name are
  **personal data**. Every *value* the system stores or displays is exactly as the official
  source publishes it, enriched from no other source. The system does, however, produce
  genuinely **new derived information**: R20 assembles into one profile, with running
  totals, what the source publishes only as isolated per-Órgano entries. That aggregation is
  the capability's purpose and is acknowledged rather than denied; the mitigations are that
  every read requires authentication (R2) and that removal is available under R12.
- **R28** — Published values are stored **as published**, with no correction or inference. In
  particular the stated duration, which the source frequently publishes as a per-Órgano
  default rather than a per-contract value, is shown **with an indication that it is
  unreliable**, so a user is not invited to read it as a real contract term.

## Acceptance criteria

1. **(R1)** A `USER` or an unauthenticated visitor that attempts to mark or unmark an Órgano
   for import, trigger an import, or remove a contract is denied; an authenticated `ADMIN`
   is allowed.
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
7. **(R5, R16)** Unmarking an Órgano that already has imported contracts leaves those
   contracts stored and browsable, and a subsequent import retrieves nothing further for it.
8. **(R6, R7, R15)** After an Órgano's initial import completes, a user viewing its
   contratos menores sees every contract the source published for it, each row showing its
   identifier, publication date, object, amount, duration and awardee — every attribute the
   system holds — with no per-contract screen to open for further data.
9. **(R7)** Every displayed amount, and every total derived from amounts, is labelled as
   including VAT.
10. **(R8)** An Órgano's initial import yields contracts published in years before the
    current one, not only recent ones.
11. **(R8)** A scheduled import of an Órgano whose initial import has completed re-reads only
    a recent window, not the whole history; a correction published inside that window is
    picked up, and an administrator can re-run a full historical read to pick up a
    correction outside it. An Órgano whose initial import has not completed is not treated
    as up to date by the scheduler.
12. **(R9)** An initial import interrupted part-way retains the contracts it already stored;
    resuming or retrying it completes the history with no duplicates. While it runs, an
    administrator can see that it is in progress and how far it has got, and a user browsing
    that Órgano sees the contracts loaded so far.
13. **(R10)** Re-importing after a contract's published attributes change updates that
    contract in place: its identity is unchanged and the refreshed attributes are shown.
14. **(R11)** Running two imports of the same published contracts in succession yields the
    same stored set with no duplicates and no attribute changes; a contract stored by an
    earlier import that is absent from a later import's results is still present and
    unchanged afterwards.
15. **(R12)** An administrator can remove a stored contract, after which it no longer appears
    in its Órgano's list or in any operador's history, and a subsequent import does not
    silently restore it as an unremoved contract.
16. **(R13)** A `USER` reaches an Órgano's contracts by browsing the taxonomy tree and
    selecting an Órgano from it; that tree offers the `USER` no control to create, rename,
    move, delete or reassign anything. *(Also satisfies SPEC-0004 #9 and the deferred half
    of SPEC-0004 #2.)*
17. **(R14)** Opening an Órgano presents its contracts split into *contratos menores* and
    *licitacións*; a family for which the system holds no data is reachable and states that
    no data is available rather than erroring.
18. **(R15)** An Órgano's contratos menores list states how many contracts the current
    selection contains, and that count matches the number of contracts reachable by moving
    through the selection.
19. **(R15)** Each contract row offers a way to reach that contract's publication at the
    official source.
20. **(R16)** An Órgano that is not marked for import shows a message saying its contracts
    are not imported; an imported Órgano with no contratos menores shows an empty list
    without that message. The two states are distinguishable to the user.
21. **(R17)** Filtering an Órgano's contratos menores by a given year returns only contracts
    whose publication date falls in that year; clearing the filter restores the full list. No
    CPV filter control is present.
22. **(R17)** Sorting by publication date returns contracts in date order, and sorting by
    amount returns them in amount order, in the chosen direction; the first portion after
    sorting descending by amount contains the largest-amount contract of the **whole**
    filtered selection, not merely the largest of the previously displayed portion.
23. **(R18)** After importing contracts awarded to a previously unknown awardee, that awardee
    exists as an operador económico identified by its published fiscal identifier; two
    contracts awarded to the same fiscal identifier yield **one** operador, not two, even
    when the published names differ — and the name shown is the most recently published one.
24. **(R18)** An awardee published as a natural person with a personal fiscal identifier is
    catalogued as an operador económico in the same way as a legal entity, and is reachable
    through the same views.
25. **(R18)** A contract published with an absent or unusable fiscal identifier is imported
    and appears in its Órgano's list, and is attached to no operador; no placeholder operador
    is created for it.
26. **(R19)** A user can reach an operador by following the awardee from a contract row, and
    can also find it from a list of operadores by looking it up by name and by fiscal
    identifier.
27. **(R20)** Opening an operador económico shows every contract awarded to it across **more
    than one** Órgano, with the awarding Órgano shown per contract, and reports the number of
    contracts and the total amount awarded; with a year filter applied, both figures reflect
    the filtered selection and equal the sum over the listed contracts.
28. **(R20)** An operador's history can be filtered by year and sorted by publication date or
    amount, with the same behaviour criteria 21 and 22 require of the Órgano list.
29. **(R21)** After an administrator triggers an import manually, the system reports whether
    it succeeded and a summary of how many Órganos were covered and how many contracts were
    added and refreshed.
30. **(R22)** With no human trigger, the scheduler runs and contracts published since the
    previous run become browsable for every marked, active, initially-imported Órgano.
31. **(R23)** A second run for an Órgano already being imported does not start; meanwhile a
    scheduled incremental import of a **different** Órgano does start and complete while that
    Órgano's initial import is still running.
32. **(R24)** When the source is unreachable or returns an unusable response for one Órgano,
    the import reports failure for it, contracts already stored are unchanged, contracts
    imported for Órganos processed earlier in the same run are retained, and the remaining
    marked Órganos are still imported.
33. **(R25)** With at least 1 000 000 stored contracts, an Órgano's contracts and an
    operador's history return their first portion, their count and their totals within 1 s at
    the 95th percentile, and requesting a later portion of the same selection meets the same
    budget.
34. **(R26)** During an initial import, the system never has more than one request in flight
    to the source, and consecutive requests are separated by at least the configured minimum
    interval.
35. **(R27)** Every awardee name and fiscal identifier displayed matches what the official
    source publishes for that contract, with no value corrected, inferred, or enriched from
    elsewhere; and no operador's history or totals are reachable without authentication.
36. **(R28)** A displayed duration matches the source exactly and is accompanied by an
    indication that the source frequently publishes a per-Órgano default rather than a
    per-contract value.
