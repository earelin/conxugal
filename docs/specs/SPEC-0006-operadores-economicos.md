---
status: draft
---

# SPEC-0006. Operadores económicos and their contract history

## Summary

Every public contract the system holds names the party that was awarded it, together with a
fiscal identifier. This spec turns those awardees into a first-class catalogue of
**operadores económicos**, and gives each one a place in the application where its
**contract history across every Órgano de Contratación** is visible.

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
  handles personal data and produces new derived information about identifiable people. R11
  states that plainly rather than denying it.
- **Identifiers and names are published inconsistently.** The same identifier appears with
  different padding and casing, and under varying names. Matched naively, one operador
  splits into several and the aggregation this spec exists for silently fails — the failure
  mode is a quiet undercount, which is worse than an error.

Reading operadores is available to any authenticated user, consistent with
[SPEC-0002](SPEC-0002-user-authentication.md). There is no administrative surface here: the
catalogue is derived, so it is managed by managing the contracts it comes from.

## Scope

- **In scope:** the derived catalogue, operador identity and matching, the display name
  rule, awardees whose identifier is unusable, operador lifecycle, how a user finds an
  operador, and the contract history with its totals, filtering and sorting.
- **Out of scope — importing contracts.** Every family's import is owned by that family's
  spec; contratos menores by [SPEC-0005](SPEC-0005-import-browse-contratos-menores.md).
- **Out of scope — anything about an operador the contracts do not say.** No enrichment from
  company registries, no sector or size classification, no linking of related entities, and
  no inference of whether an identifier belongs to a person or an entity beyond what the
  source states. Each would be new information about identifiable people from outside the
  official publication.
- **Out of scope — exporting an operador's history.** Left to the future export spec
  SPEC-0005 also defers to.

## Requirements

### Access

- **R1** — Reading the operadores catalogue (R8) and any operador's contract history (R9) is
  available to any authenticated user, `USER` or `ADMIN`, and grants no ability to modify
  anything. An unauthenticated visitor is denied — a mitigation R11 depends on. There is no
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
  the value exactly as published (R12). Without it the same operador splits in two and the
  cross-Órgano aggregation this spec exists for fails silently.
- **R4** — The same identifier is published under **varying names**. Since R12 forbids
  normalising them, an operador is shown under the name from its **most recently published**
  contract, ties broken by the contract identifier. Name variation never produces a second
  operador.
- **R5** — An identifier is **unusable** when it is absent, or empty once surrounding
  whitespace is ignored. A contract published with an unusable identifier is still stored and
  browsable under its Órgano and still displays its awardee's name as published, but it
  yields **no** operador — never an invented or placeholder one. Nothing beyond that
  emptiness test is validated: the source publishes irregular but genuine identifiers, and
  rejecting them would discard real awards.
- **R6** — Awardees that are **natural persons** and those that are **legal entities** are
  catalogued and reachable identically. The system does not classify which is which, because
  the source does not publish that distinction (R12).
- **R7** — An operador exists exactly as long as it has at least one contract. When its last
  contract is removed — the explicit withdrawal of SPEC-0005 R13 — the operador **ceases to
  exist**, so its name and fiscal identifier survive nowhere in the system. This is what
  makes an erasure obligation over the personal data of R11 dischargeable by acting on
  contracts alone.

### Finding an operador

- **R8** — A user can reach an operador in two ways: by following the **awardee from any
  contract row**, and from a **list of operadores** that can be looked up by name or by
  fiscal identifier. Name lookup matches any part of the name, ignoring letter case and
  accents; identifier lookup matches the whole identifier under the equivalence of R3.
  Without the list, the primary question this capability exists to answer — *what has this
  supplier been awarded?* — could only be asked by first stumbling onto one of its
  contracts.

### Contract history

- **R9** — Opening an operador shows its **contract history**: every contract awarded to it
  **across all Órganos and all contract families**, showing per contract the awarding Órgano
  and the family it belongs to, plus the number of contracts and the total amount awarded
  **for the current selection**. Totals carry the same VAT labelling the contract's own
  family requires of it (for contratos menores, SPEC-0005 R7).
- **R10** — A user can **filter** an operador's history **by year** and **sort** it by
  **date** or by **amount**, ascending or descending. Filtering, sorting, counting and
  totalling apply to the whole selection, not only to the portion currently displayed.

### Non-functional expectations

- **R11** — Where an operador is a natural person, its name and fiscal identifier are
  **personal data**, and this spec produces genuinely **new derived information** about
  identifiable people that the official source does not publish: R9 assembles into a single
  profile, with running totals, what the source publishes only as isolated per-Órgano
  entries, and R8 makes that profile **searchable by personal fiscal identifier**. Both go
  beyond the source, both are the capability's purpose, and both are acknowledged here
  rather than denied. The mitigations are that every read requires authentication (R1), that
  no attribute is added beyond what the contracts state (R12 and the Scope exclusions), and
  that R7 makes erasure reachable by removing the underlying contracts.
- **R12** — Every value displayed about an operador — its name, its fiscal identifier, and
  every contract attribute in its history — is exactly as the official source published it,
  with no correction, normalisation, inference or enrichment from any other source. The
  matching equivalence of R3 governs comparison only, never display.
- **R13** — The catalogue is expected to hold **hundreds of thousands of operadores** over
  **millions of contracts**, and stays responsive at that volume. Measured under the same
  dataset, environment and concurrency conditions SPEC-0005 R23 states: the operadores list
  and an operador's contract history each return their first portion, their count and their
  totals within **1 second at the 95th percentile**, and requesting a later portion of the
  same selection meets the same budget. A user can move through the whole selection in
  bounded portions; how those portions are presented is a feature's choice.

## Acceptance criteria

1. **(R1)** An authenticated `USER` can view the operadores list and any operador's contract
   history; an unauthenticated visitor that requests either is denied. No surface offers a
   way to create, rename, edit or delete an operador.
2. **(R2)** An operador exists for an awardee only after a contract naming it has been
   imported; no operador exists that no contract names.
3. **(R3)** Two contracts whose published fiscal identifiers differ only in surrounding
   whitespace or letter case yield **one** operador, not two, and its history contains both
   contracts.
4. **(R3, R12)** Each contract row in an operador's history displays the fiscal identifier
   exactly as published for that contract, including padding and casing the system ignored
   when matching.
5. **(R4)** Two contracts awarded to the same identifier under different published names
   yield one operador shown under the name from the more recently published of them; neither
   name creates a second operador.
6. **(R5)** A contract published with an absent or whitespace-only fiscal identifier is
   stored, appears in its Órgano's list, and displays its awardee name — while creating no
   operador and appearing in no operador's history.
7. **(R5)** A contract published with an irregular but non-empty identifier **is** attached
   to an operador rather than rejected or discarded.
8. **(R6)** An awardee published as a natural person with a personal fiscal identifier is
   catalogued and reachable exactly as a legal entity is; no view distinguishes the two.
9. **(R7)** Removing an operador's last remaining contract removes the operador: its name
   and fiscal identifier are afterwards reachable through neither the operadores list nor
   lookup by name or identifier.
10. **(R7)** Removing one of an operador's several contracts leaves the operador in place
    with the remaining contracts, and its count and total reflect the removal.
11. **(R8)** A user can reach an operador by following the awardee from a contract row, and
    can find the same operador from the operadores list by a partial, case- and
    accent-insensitive fragment of its name, and by its full fiscal identifier.
12. **(R9)** Opening an operador shows every contract awarded to it across **more than one**
    Órgano, showing per contract the awarding Órgano and its contract family, and reports the
    number of contracts and the total amount awarded; the totals equal the sum over the
    listed contracts and are labelled as their family requires.
13. **(R10)** Filtering an operador's history by a given year returns only contracts dated in
    that year, and the reported count and total reflect that filtered selection rather than
    the whole history; clearing the filter restores both.
14. **(R10)** Sorting by date returns contracts in date order and sorting by amount returns
    them in amount order, in the chosen direction; the first portion after sorting descending
    by amount contains the largest-amount contract of the **whole** filtered selection, not
    merely the largest of the previously displayed portion.
15. **(R11)** No operador profile, total, or identifier lookup is reachable without
    authentication.
16. **(R12)** Every operador name, fiscal identifier and contract attribute displayed matches
    what the official source published, with no value corrected, normalised, inferred or
    enriched; no attribute is shown that no contract supplies.
17. **(R13)** Under the stated conditions, the operadores list and an operador's history each
    return their first portion, their count and their totals within 1 s at the 95th
    percentile, and requesting a later portion of the same selection meets the same budget.
