---
status: active
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

Marking an Órgano **requests an initial import** that loads its entire published history in
one go. It starts at once when no other import is running; when one is, the request is
refused and the next scheduled run picks the Órgano up, so marking never has to be repeated
and no publication is missed — only its arrival is delayed. From then on a scheduler keeps
the Órgano current **incrementally**. The two modes differ in cost by
orders of magnitude — a single large Órgano holds well over a million contracts — so the
spec treats them as distinct operations with distinct expectations.

Users reach contracts by selecting an Órgano de Contratación — from the taxonomy tree or
the catalogue list of
[SPEC-0004](SPEC-0004-import-manage-organos-contratacion.md) — and opening its contracts,
which are presented **split by contract family**: *contratos menores* and *licitacións*.
This spec delivers the contratos menores family only; licitacións are a separate, future
spec that fills the other side of the same split. Within contratos menores a user browses
**one publication year at a time** and sorts by **date** or **amount**.

Every contrato menor names its awardee together with a fiscal identifier. The catalogue of
**operadores económicos** built from those awardees, and the cross-Órgano contract history
each one carries, are specified separately in
[SPEC-0006](SPEC-0006-operadores-economicos.md): they are fed by every contract family, not
only this one, so they outlive this spec. What this spec owes SPEC-0006 is the awardee data
on each stored contract (R7) and the removal rule that keeps a derived operador honest
(R13).

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
  stated duration. Each publication is **individually addressable at the source**, which is
  what makes R16's per-row link to the original achievable; without that, verifying a row
  against the source would mean reproducing a query rather than following a link.
- **Not published:** **CPV codes**, expediente numbers, amounts excluding VAT, estimated
  value, and place of execution. CPV in particular exists only for licitacións — so this
  spec offers **no CPV filter**, and CPV-based querying arrives with the licitacións spec.
- **Retrievable only in bounded slices:** the source answers per Órgano over a limited date
  range at a time. It offers no "changed since" facility, which is why R8 has to say what
  an incremental run re-reads and R12 has to say that absence proves nothing.
- **Ordered by publication date, and assumed not to back-date:** a contract is retrievable by
  the date it was published, and the system assumes the source does not later add a
  publication whose date falls before a period already read. R8's window design depends on
  this: a back-dated entry would fall outside every future window and be reachable only by
  R10's historical re-read. The assumption is stated because it is load-bearing, not because
  it has been proven.
- **Knowingly incomplete:** contracts below 5 000 € paid through *anticipo de caixa fixa*
  are legally exempt from publication, and published history begins around 2018. The
  system reproduces the source; it does not claim to hold every contrato menor ever
  awarded.
- **Weak or inconsistent in places:** the published duration is frequently a per-Órgano
  default rather than a per-contract value; object text quality varies from real
  descriptions to generic budget categories; and text fields, including fiscal identifiers,
  are published with inconsistent padding and casing.

## Scope

Deliberately **out of scope**, each owned elsewhere or by a later spec:

- **Operadores económicos** — the catalogue of awardees, their identity rules, how a user
  finds one, and the cross-Órgano contract history they carry — belong to
  [SPEC-0006](SPEC-0006-operadores-economicos.md). They are derived from the contracts of
  **every** family, so they are not this spec's to own; this spec supplies the awardee data
  they are derived from.
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
- **Free-text contract search.** SPEC-0001 (`active`) is titled for *searching* contracts and
  promises browsing, searching and filtering across the contract dataset. Every read here
  starts by choosing one Órgano (R14), and **nothing searches contract objects by text** — so a
  user who knows *what* was bought but not who bought it cannot find it. That gap is at least
  as large as the export one and is recorded for the same reason: so it is visible rather than
  assumed closed.

  The **cross-Órgano** half of that promise is answered, but only from the awardee side:
  [SPEC-0006](SPEC-0006-operadores-economicos.md) R9 assembles one operador's contracts across
  every Órgano, which is why R14 has a route back into an Órgano from such a row. Nothing spans
  Órganos from the **object** side, which is the half left open.
- **Import run history and the administration surface that reviews it.** R9, R20 and R23 say
  what an administrator must be able to see about a run; the durable per-run record, the
  progress indicator, the diagnostics that make a failure debuggable, and the admin page over
  all of it belong to [SPEC-0007](SPEC-0007-monitor-import-runs.md). It is importer-neutral —
  it covers SPEC-0004's catalogue import and this spec's four modes alike — so it is not this
  spec's to own. What this spec owes it is the facts a run produces: its mode, its counts, its
  outcome, and how far a resumable run has got.
- **Contracts of Órganos that are inactive.** See the note under R3.

### Decisions taken, and what is left open

Two decisions that earlier drafts deferred are **settled**:

1. **Reads are paged by position, and the cost of doing so is measured before it is
   optimised.** R17 keeps the full control — first, previous, next, last, or a chosen page —
   over a selection whose exact size is known, and states it as its own requirement because
   two sibling specs take the same control. No ADR is raised for the mechanism, because
   the constraint that made this hard was overstated: a user never pages over the stored
   dataset, only over **one Órgano's contracts of one family in one publication year**, which
   R19 makes mandatory precisely so that bound holds. At that scale the straightforward
   positional read is the thing to build first, so R24 states **no latency budget** — only the
   conditions under which one will be measured, and what would falsify the bet.
2. **A long-running, resumable import job holds its state in the database** — required by R9
   and R10, and recorded in
   [ADR-0017](../architecture/0017-import-run-state-in-postgresql.md), which also settles what
   [SPEC-0007](SPEC-0007-monitor-import-runs.md) R5, R7 and R8 need. That ADR is **accepted**,
   so a feature may build directly onto it.

One decision remains outside this spec:

- **The pacing model against the source** — how fast requests may be issued and at what
  interval. R25 states the obligation;
  [ADR-0014](../architecture/0014-resilient-throttled-outbound-http-client.md) owns the bound
  and makes it configurable per source, and this spec must not harden it. Note that **run
  concurrency is no longer part of this**: R22 fixes it at one import at a time, system-wide.
  What remains open is the rate a single run may sustain, not how many runs may sustain one.

## Requirements

### Access

- **R1** — **Managing** contratos menores is reachable only by users with the `ADMIN` role:
  selecting which Órganos are imported (R3–R5), triggering an import (R20), resuming an
  incomplete initial import (R9), requesting a full historical re-read (R10), and removing or
  restoring a contract (R13). A `USER` or an unauthenticated visitor who attempts any of these
  is denied (consistent with SPEC-0003's administration-area access rule).
- **R2** — **Reading** contratos menores (R14–R19) is available to any authenticated user,
  `USER` or `ADMIN`. These reads grant no ability to modify anything. An unauthenticated
  visitor is denied — which is also the mitigation R26 relies on.

### Selecting which Órganos are imported

- **R3** — Only Órganos that are **both active in the catalogue and marked for import** have
  their contratos menores retrieved. An Órgano that is inactive, or that is not marked, is
  not retrieved from the source at all.
- **R4** — An administrator can **mark and unmark** an Órgano for import and see which
  Órganos are currently marked. An Órgano newly added to the catalogue starts **unmarked**:
  importing is opted into deliberately, never by default. Marking an Órgano **requests an
  import**, **in whatever mode R8 dictates for it**, without the administrator having to issue
  a separate trigger. The request starts immediately when R22's single-import guard is free;
  when it is not, it is **refused** like any other trigger and R21's scheduler picks the Órgano
  up on its next run, so a mark is never repeated and never silently lost. What the guard costs
  is stated rather than hidden: a newly marked Órgano can wait behind a long-running import
  before it becomes browsable.

  The mode R8 dictates settles all three ways an Órgano can arrive at a mark:

  - never loaded before: an **initial import**;
  - **left half-loaded** by an earlier interruption, including one caused by unmarking it
    mid-import (R5): a **resumed** import, which continues that load rather than restarting it
    and never treats the Órgano as up to date;
  - fully loaded, then unmarked and marked again: an **incremental** import covering
    **everything published since its last successful import** under R8's window floor — closing
    the gap accumulated while it was unmarked without reloading a history already stored.

  A re-mark is not a reason to re-read a million rows, it is not a reason to lose what was
  published in the meantime, and it is not a reason to abandon a history only partly loaded.
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
  duration, and its **awardee** — together with a stable identity by which the same contract is
  recognised across successive imports.

  **The awardee is held once, on the operador it names, not on each contract.** The system
  stores the awardee's name and fiscal identifier on the
  [SPEC-0006](SPEC-0006-operadores-economicos.md) operador row, and each contract references
  that row; two awards to the same party do not store the party twice. What this costs is stated
  rather than hidden, and R27 states it: a contract does not record the exact spelling **it** was
  published under, and an award whose identifier is unusable (SPEC-0006 R5) yields no operador
  and therefore records **no awardee at all** — a case the source is not observed to produce,
  since every published contract names its awardee with a NIF/CIF.

  The amount is the published figure **including VAT**, and is
  labelled as such wherever it or any total derived from it is shown: the legal thresholds
  that define a contrato menor are VAT-exclusive, so an unlabelled figure invites exactly
  the wrong comparison.
- **R8** — An Órgano is imported in one of **four modes**. This requirement enumerates them and
  fixes the single rule that picks between them, wherever the import was triggered — by marking
  (R4), by an administrator (R20), by the scheduler (R21), or by automatic resumption (R9):
  - an **initial import**, which loads the Órgano's **full published history** — every contrato
    menor the source holds for it, not only recent ones. It is the mode for an Órgano whose
    history has **never been loaded**;
  - a **resumed** import, which continues an initial import left incomplete rather than
    restarting it (R9). It is the mode for an Órgano whose initial import **has not completed,
    however it was interrupted** — by failure, by the process dying, or by the Órgano being
    unmarked mid-import and marked again (R4, R5);
  - an **incremental** import, which re-reads a **recent window** of publication dates rather
    than the whole history. It is the mode for an Órgano whose initial import **has completed**;
  - a **historical re-read** (R10), which an administrator requests explicitly for a single
    Órgano and which no trigger ever selects automatically.

  The mode is chosen **per Órgano, not per run**: one run covering many Órganos may be initial
  for one, resumed for another and incremental for a third. What makes the rule decidable is
  that the system knows, per Órgano, which of **three** states its initial import is in —
  **never started**, **started and incomplete**, or **complete**. Two states would not do it: a
  never-loaded Órgano and a half-loaded one are both "not complete", and they take different
  modes. The distinction is what stops a half-loaded Órgano ever being treated as up to date.

  For an incremental import the window always covers **at least the period since that Órgano's last successful import**,
  plus a lookback margin for corrections. Without that floor a fixed window is a silent
  data-loss mechanism: a scheduler outage, or the days an Órgano waits while R22's single-import
  guard is held by a long initial import elsewhere, would leave publications that no future run
  ever re-reads and that only R10 could reach — and no requirement obliges anyone to run R10.
  With the floor, importing simply catches up when it resumes. The floor is what makes R22's
  serialisation affordable: waiting costs freshness, never data.

  The window is what makes R11's refresh achievable: the source offers no "changed since"
  facility, so a correction is only discoverable by re-reading the period it falls in.
  Corrections published inside the window are picked up automatically; corrections to older
  publications are not, and R10 is how they are reached.
- **R9** — An initial import is **long-running and interruptible without loss**: a single
  Órgano may hold over a million contracts, so one that fails or is interrupted part-way
  keeps everything it already stored and is **resumed to completion** — automatically, and
  on demand by an administrator — adding no duplicates (R12). It is never left permanently
  incomplete for want of a trigger. While it is in progress an administrator can see that it
  is running and how far it has got.
  > That last obligation — seeing a run in flight and how far it has got — is rendered by
  > [SPEC-0007](SPEC-0007-monitor-import-runs.md) R5–R7, which also relates successive
  > resumptions of one Órgano's initial import to each other so an administrator sees it
  > converging rather than a sequence of unrelated failures.
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
  R8's window is refreshed in place by R11, and one outside it is reached by R10. Because
  SPEC-0006 derives its catalogue from these contracts, a removal here propagates there under
  that spec's **operador lifecycle** rule (R7): an operador left with no visible contracts
  becomes unreachable, without either rule erasing the awardee data it came from.

### Finding and browsing contracts

- **R14** — Any authenticated user selects an Órgano de Contratación either by browsing the
  **read-only taxonomy tree** of SPEC-0004 R9 — which offers a `USER` no control to create,
  rename, move, delete or reassign anything — or from the **catalogue list** of SPEC-0004
  R8, and opens that Órgano's contracts. The tree is the surface SPEC-0004 deferred to this
  spec. Both routes are required because the tree alone is not sufficient: SPEC-0004 R18
  makes every newly imported Órgano **unclassified**, so an Órgano can be marked, imported
  and yet absent from the tree. **Every Órgano whose contracts the system holds is
  reachable**, whether or not it is classified, marked, or still active.

  There is a third route, and it is stated here with the other two so that no cross-Órgano
  surface has to invent one: **wherever a contract row names its awarding Órgano, following
  that Órgano opens that Órgano's contracts** (R15). The surface that has such rows is not this
  spec's — it is an operador's contract history, which lists contracts from many Órganos at once
  ([SPEC-0006](SPEC-0006-operadores-economicos.md) R9) — so the rule is **stated** here, where
  the routes into an Órgano live, and **proved** there, by that spec's criterion for it. The
  route never dead-ends, because R14 makes every Órgano the system holds contracts for
  reachable.

  No row **repeats** the awarding Órgano on a list already scoped to one, since every row there
  belongs to the Órgano open. That is a rule about what a row states, not about what a page
  looks like, and it is the same rule SPEC-0006 R9 applies when it stops a row repeating the
  family its section names. It does **not** extend to the awardee: a row states its awardee, on
  an operador's own history as anywhere else, under the single published spelling SPEC-0006 R4
  selects for that operador. Per-contract spelling variance is not recorded (R7, R27) and so is
  visible nowhere.

  This route and its mirror — following a row's **awardee** to its operador
  ([SPEC-0006](SPEC-0006-operadores-economicos.md) R8, rendered on the row by R16) — are the
  two crossings every contract list offers, so a user can move between an awarding body and a
  supplier from either side without returning to a search.
- **R15** — An Órgano's contracts are presented **split by contract family**, one section per
  family, of which this spec delivers *contratos menores* and the licitacións spec delivers
  the second. Each family is reachable independently, and a family for which the system holds
  no data is **omitted** rather than shown empty (R18). The split is **additive**: a family the
  system gains later takes its place alongside the others without this requirement changing,
  because more families than the two known today are expected.
- **R16** — Within an Órgano's contratos menores, a user sees a list showing, for each
  contract, its identifier, its publication date, its object, its amount, its stated
  duration, and its awardee, together with how many contracts the current selection
  contains. A contrato menor has **no separate detail view**: the row carries every
  attribute the system holds for it. Each row also offers a way to reach the corresponding
  publication **at the official source**, so any row can be verified against the original —
  which is what makes the system usable as evidence rather than only as a convenience. The
  row's **awardee** is also where a user crosses into
  [SPEC-0006](SPEC-0006-operadores-economicos.md): that spec's requirement that an operador
  can be reached by following the awardee from any contract row (R8) is an affordance rendered
  **here**, on this row, so both specs' features know which side builds it. The list is
  **paginated** under the control R17 defines.
- **R17** — **The paging control.** A long list is shown one page at a time. Whoever is reading
  it is told **how many entries the current selection contains** and how many pages it spans,
  and can move to the **first**, **previous**, **next** or **last** page, or **jump to a chosen
  one**. Every entry in the selection is reachable this way — none repeated, none skipped. The
  two ends are offered directly because they are the two a user asks for by name — the newest
  and the oldest of a selection — and reaching them by counting is the one navigation a page
  number cannot express.

  Paging is over an **ordered** selection: without a deterministic total order "the next page"
  and "the last page" do not denote, and exhaustive paging cannot be shown. Each list that
  takes this control states its own ordering; this requirement fixes only that one exists.

  **Changing what the selection is re-pages it from its first page** — a filter applied or
  cleared, a sort changed, a scope changed — rather than leaving the reader on a page number
  that no longer means what it did. Paging itself never changes the selection, so moving
  between pages leaves the count, the page total and the ordering alone.

  This is stated as its own requirement because it is not a rule about contratos menores: it is
  the control **every** paginated list in this system takes, and
  [SPEC-0006](SPEC-0006-operadores-economicos.md) and
  [SPEC-0007](SPEC-0007-monitor-import-runs.md) both cite it rather than defining their own. A
  reader meets several of these lists in one session, and one of them paging differently from
  the rest is a defect they would experience as inconsistency rather than as a design.
- **R18** — **An Órgano holding no contratos menores shows no contratos menores section at
  all.** The section appears once at least one contract is stored for that Órgano, and not
  before — whether it is empty because the Órgano is not being imported (R3), because it was
  imported and awarded none, or because its initial import has not yet stored anything (R9).
  An empty section is never rendered.

  This is a deliberate trade-off, and what it costs is worth stating: absence is silent, so a
  user cannot tell an Órgano the system does not import from one that was imported and awarded
  nothing. Both simply have no section. The judgement is that an empty section on the many
  Órganos that publish no contratos menores at all — 234 of the catalogue's 429 organismos are
  Concellos, which publish none — is noise on every one of them to disambiguate a question few
  users are asking. Whether an Órgano is imported remains answerable by an administrator
  (R4, which owns the mark, and [SPEC-0007](SPEC-0007-monitor-import-runs.md) R15), and if
  users turn out to
  need it, exposing it is a later increment rather than something this rule forecloses.

  **Once the section is present it is never empty**, because R19 offers only years the Órgano
  actually has contracts in. What the section must still make plain is that it is
  **incomplete**: while the **initial import has not finished** what is shown is partial (R9),
  and it says so, because a user must not read a growing list as a complete one.

  An Órgano that was imported and has since been unmarked or become inactive keeps its section
  and the contracts retained under R5, and says that it is no longer being updated.
- **R19** — An Órgano's contratos menores are **always scoped to a single publication year**.
  A year is part of every selection; there is no unfiltered, all-years list, and no way to
  clear the year to obtain one. A user changes which year they are looking at, never whether
  they are looking at one.

  The year in effect when the section is first opened is the **most recent year for which the
  Órgano has contracts**, so a user lands on data rather than on a chooser. Only years the
  Órgano actually has contracts in are offered, so choosing a year can never be the reason a
  list is empty.

  Alongside those years the chooser offers an **undated** selection wherever the Órgano holds
  contracts whose publication date cannot be interpreted (R27). Without it a mandatory year
  would make those contracts unreachable — stored, never shown — which would cost by omission
  exactly what R27 refuses to cost by rejecting them at import. The selection is offered only
  when such contracts exist, so it is absent for the Órganos that have none.

  Within that year a user can **sort** by **publication date** or by **amount**, ascending or
  descending. Sorting and counting apply to the **whole year's selection**, not only to the
  page displayed. No CPV filter is offered, because the source publishes no CPV for contratos
  menores.

  The year is mandatory rather than optional because it is what **bounds the size of every
  paged read** — the constraint R24 relies on when it defers a latency budget. That is why the
  scoping is a requirement here rather than a default a feature might quietly relax.

### Triggering imports

- **R20** — An administrator can trigger an import on demand. The trigger states its
  **scope** — every marked, active Órgano, or one chosen Órgano — and runs each covered
  Órgano in the mode R8 dictates for it, which for a multi-Órgano trigger may differ from one
  Órgano to the next.

  **R3 binds an explicit single-Órgano trigger exactly as it binds the scheduler.** An Órgano
  that is unmarked or inactive is not retrieved, however the trigger arrived — the same holds
  for R10's historical re-read. This is stated rather than derived because "an administrator
  asked for this one specifically" is precisely the case where R3 looks like it might not
  apply. Such a trigger does not start an import, and the administrator is told **why** — that
  the Órgano is not eligible, which is a different reason from R22's guard being held and is
  recorded as a distinct reason on the same *refused* outcome
  ([SPEC-0007](SPEC-0007-monitor-import-runs.md) R4).

  The administrator is shown the outcome: whether it **succeeded, failed, or partially
  succeeded**, which Órganos were covered and which of them failed, and how many contracts
  were added and refreshed. Partial success has to be expressible here because R23 requires a
  run to carry on past a failing Órgano, which makes it the likeliest verdict on a
  multi-Órgano run — a binary succeeded/failed would report the normal case as a lie.
- **R21** — The system imports automatically on a recurring schedule, without any human
  trigger, so newly published contracts appear without administrator action. The scheduler
  covers every Órgano selected under R3, each in the mode R8 dictates for it — **incremental**
  for an Órgano already loaded, **resumed** for one whose initial import is incomplete (R9), so
  no Órgano is left incomplete for want of a trigger.
- **R22** — **At most one import runs at a time, across the whole system.** The guard is
  global, not per Órgano: while any import is in progress — this spec's, in any of its four
  modes, or SPEC-0004's catalogue import — a further trigger does not start. It is **refused**
  and recorded as such (SPEC-0007 R4), never queued and never silently dropped, so a trigger
  that did nothing is distinguishable from one that ran.

  **A refused mark-triggered import is recovered, not lost.** R4 makes marking a trigger like
  any other, so a mark landing while an import runs is refused like any other. It is the one
  refusal with a guaranteed second chance: R21's scheduler covers **every** marked Órgano, so
  the Órgano is picked up on the next scheduled run without the administrator marking it again.
  That is why "never queued" costs nothing here — the schedule is the queue, and R8 gives the
  recovered run the same mode the refused one would have had.

  **Within a run, Órganos are imported serially too** — one Órgano is finished before the next
  is begun, never several in parallel. The reason is **reportability**, not pacing: it gives
  R20's per-Órgano outcomes and SPEC-0007 R5's progress a well-defined order to report, so at
  any moment a run is working on one identifiable Órgano and "how far has it got" has an answer.
  Pacing is not the reason, and this requirement does not claim it is:
  [ADR-0014](../architecture/0014-resilient-throttled-outbound-http-client.md) bounds requests
  **per source**, so Órganos processed in parallel would contend for the same permit and the
  source would see the same aggregate stream either way.

  The reason is the source, not the system. Everything the system retrieves comes from one
  public site that owes us nothing, and being throttled or blocked by it costs every capability
  at once. [ADR-0014](../architecture/0014-resilient-throttled-outbound-http-client.md) already
  paces requests, but a pace shared between concurrent runs is a pace each run experiences as
  contention and the source experiences as one aggregate stream; serialising runs keeps the
  aggregate stream identical to what any single run produces, which is the behaviour least
  likely to be read as abuse. It also makes the request budget something an administrator can
  reason about, rather than a quantity divided among however many runs happen to overlap.

  **The cost is real and accepted.** An initial import of a large Órgano runs for days, and for
  those days no other Órgano is imported and no scheduled incremental run starts. Nothing is
  lost by that — R8's window floor absorbs the backlog rather than skipping it — but Órganos
  do go stale while a long import proceeds, and an administrator should expect it rather than
  discover it. Prioritising which import runs first when several are due is left to the feature;
  this requirement fixes only that they do not overlap.
- **R23** — An import is resilient to source failure: if the source is unreachable or
  returns an unusable response, the contracts already stored remain intact and consistent —
  no partial wipe — and the failure is reported to the administrator (for a manual run) or
  otherwise recorded. Failure while importing one Órgano does not discard contracts already
  imported for other Órganos in the same run, nor prevent the remaining Órganos from being
  imported.
  > *"Otherwise recorded"* is made concrete by
  > [SPEC-0007](SPEC-0007-monitor-import-runs.md): every run is recorded whatever triggered it
  > (SPEC-0007 R2), with diagnostics sufficient to identify the failure without server logs
  > (SPEC-0007 R9) and the outcome recorded **per Órgano** (SPEC-0007 R10), so an administrator
  > can tell which one needs attention.

> R20–R23 restate SPEC-0004 R10–R13 with contracts in place of Órganos. R22 matches SPEC-0004
> R12 exactly — one import at a time, system-wide — and the guard spans both specs rather than
> each holding its own, since they draw on the same source. **One deliberate divergence
> remains**: SPEC-0004 R13 makes an import strictly all-or-nothing, whereas R23 is per-Órgano.
> That is not a copy-paste slip — a run spanning many Órganos and millions of records cannot be
> discarded in full because one Órgano failed.

### Non-functional expectations

- **R24** — The stored dataset is expected to reach **millions of contracts**, and browsing
  stays responsive at that volume. This requirement fixes the **conditions under which that is
  measured**, and deliberately fixes **no latency budget**.

  The conditions are the **reference environment** — the **production deployment**: the
  hardware and configuration the system actually runs on, not a separate rig provisioned for
  measurement. SPEC-0006 and SPEC-0007 measure on the same one, so all three are comparable,
  and no environment has to be kept in step with production for their numbers to mean anything.

  Because the environment is production, **the dataset conditions are stated relative to what
  production holds** rather than as fixed figures: the measurement is taken once the system
  holds the contracts of **at least ten imported Órganos**, including **the largest Órgano the
  system holds**, under at least **10 concurrent readers**. Fixed floors were tried and
  withdrawn: an earlier draft required 1 500 000 contracts in a single Órgano, which is above
  what the real source can supply — the largest publisher, SERGAS, has published on the order
  of 1.4 million — so meeting it would have required seeding synthetic data into production,
  which is the separate measurement rig this requirement rules out. A relative condition is
  always reachable and always describes the system as it really is; what it costs is that the
  measurement reflects the volume held on the day it is taken, so it is **re-taken as the
  dataset grows** and the volume it was taken at is recorded beside it.

  What is measured under those conditions is an Órgano's contract list, taken at **the busiest
  single year of that largest Órgano**: its **first page and its count**; a page **deep** in
  that year's selection; and both of those **sorted by amount descending**, not only in the
  default ordering — an arbitrary sort over the largest selection the system can produce is the
  read that actually breaks, and measuring only the default first page would prove nothing
  about it.

  No number is asserted because none has been observed. A user pages over one Órgano's
  contracts of one family **in one publication year** (R19), never over the whole stored
  dataset, so the deepest selection reachable is the largest Órgano's busiest year rather than
  the table — a bound smaller than the stored volume by the number of years the source covers.
  Whether positional paging is adequate there is a question for measurement, not for a figure
  chosen in advance. A budget is set once these measurements exist, **by revising this
  requirement** — not by a task quietly adopting a number, and not left to whoever notices
  first. Naming the destination is what stops the obligation expiring unowned. Until then the
  obligation is to **measure and record**, which is what makes "responsive" falsifiable rather
  than decorative.
- **R25** — The import is **courteous to the public source**: across everything the system
  retrieves — this spec's imports and SPEC-0004's catalogue import alike — its total request
  rate stays within a budget that keeps it a negligible load on a public service, and it
  never fetches as fast as it can. The concurrency bound and the interval are configured per
  source and decided outside this spec; this requirement fixes the obligation, not the
  number. It binds every mode, and binds most sharply during an initial import or a
  historical re-read, which are the largest bursts of traffic the system ever produces.
- **R26** — Where the awardee is a natural person, the name and fiscal identifier on a
  contract are **personal data**. At the level this spec owns — a contract row — the system
  reproduces exactly what the official source already publishes about that award and adds
  nothing, and every read requires authentication (R2). The genuinely new derived
  information the system produces about an awardee — aggregating their awards across
  Órganos and making them searchable by identifier — is created by
  [SPEC-0006](SPEC-0006-operadores-economicos.md) and acknowledged there.

  One place where "reproduces what the source publishes" **stops being true** is named rather
  than glossed: R13 keeps a removed contract **remembered as removed**, so once the source
  withdraws a publication the system still holds that award's awardee name and fiscal
  identifier, indefinitely, after the source has stopped publishing them. That is deliberate —
  it is what makes a removal restorable and what stops a later import silently re-adding a
  withdrawn contract — and it is the same decision, at contract level, that
  [SPEC-0006](SPEC-0006-operadores-economicos.md) R12 records at operador level when it states
  that no operador data is removed. No requirement here erases it, and none is proposed.
- **R27** — Published values are stored and displayed **as published**, with no correction,
  normalisation or inference, and enriched from no other source. In particular the stated
  duration, which the source frequently publishes as a per-Órgano default rather than a
  per-contract value, is shown **with an indication that it is unreliable**, so a user is not
  invited to read it as a real contract term.

  **Interpreting a value is not correcting it.** R19 sorts by amount and filters by the year
  of the publication date, which means reading a published amount as a number and a published
  date as a date.

  **Two values are stored interpreted rather than as text, and the rule above yields to that.**
  The **amount** is published as a number and stored as one. The **publication date** is
  published as text and stored as a date: the interpretation replaces the published string
  rather than accompanying it, so a date the system cannot interpret is stored as *no date* and
  its published text is not retained. This is a deliberate narrowing of "as published", taken
  because a second column per value earns its keep only where the two can differ meaningfully,
  and it is bounded to these two values — every other published value is stored as published,
  byte for byte within its trimmed bounds.

  **Surrounding whitespace is not a published value.** The source pads its text fields out to
  fixed widths, so a value arrives carrying spaces that carry no information: they are an
  artefact of how the source serialises its fields, not something it published about the
  contract. Every text value is therefore stored with **leading and trailing whitespace
  removed, and nothing else** — no internal spacing collapsed, no case folded, no punctuation
  touched. A value that is **only** whitespace published nothing and is stored as absent, on the
  same rule that stores an uninterpretable date as no date.

  This is a narrowing of "as published" in the same sense as the two above, and a much smaller
  one: it discards only characters the source itself does not treat as content. It is bounded
  deliberately — trimming further, to collapse internal runs or fold case, would start merging
  values that genuinely differ, which is what R7's *store what is published* forbids and what
  [SPEC-0006](SPEC-0006-operadores-economicos.md) R3 keeps out of its match rule for the same
  reason.

  **The stated duration is stored capped at 64 characters**, and it is the only value with a
  length bound. The source publishes short phrases there — the field carries a per-Órgano default
  far more often than a real term, which is why R27 already requires it to be shown as
  unreliable — so the cap is not expected to be reached. It is set because a value that overran
  its column would fail the batch and **reject a real award**, which the last paragraph of this
  requirement refuses; losing the tail of an already-unreliable field is the smaller loss, and it
  is taken knowingly rather than discovered. No other value is capped: the contract's object in
  particular has no bound at any layer.

  **The awardee is the remaining exception, and R7 states it**: it is stored once on its operador
  rather than on each contract, so what a row shows is the spelling SPEC-0006 R4 selects for that
  operador, not the spelling that contract was published under.

  Neither exception is a reason to **reject** a contract. A published amount or date that cannot
  be interpreted leaves that value absent, the contract is stored like any other, and it takes no
  part in the ordering it cannot support, being ordered last when sorting by the value it lacks.
  A contract with **no date** belongs to no year and is reached through R19's **undated**
  selection. Discarding such contracts would lose real awards, which is the same reasoning
  SPEC-0006 applies to an unusable fiscal identifier.

## Acceptance criteria

1. **(R1)** A `USER` or an unauthenticated visitor that attempts to mark or unmark an Órgano
   for import, trigger an import, resume an incomplete initial import, request a full
   historical re-read, or remove or restore a contract is denied; an authenticated `ADMIN` is
   allowed.
2. **(R2)** An authenticated `USER` can view an Órgano's contratos menores; an
   unauthenticated visitor that requests them is denied.
3. **(R3)** After an import run, an Órgano that is active but **unmarked**, and an Órgano
   that is marked but **inactive**, both have no contratos menores stored from that run;
   only Órganos that are active **and** marked do.
4. **(R4)** An administrator can mark an Órgano, see it listed as marked, and unmark it
   again; an Órgano newly added to the catalogue is unmarked until an administrator marks
   it.
5. **(R4, R8)** Marking an Órgano results in its full published history being imported
   without the administrator issuing any further trigger: immediately when no other import is
   running, and otherwise on the next scheduled run once the guard frees. Afterwards the system
   records that Órgano's initial import as complete.
6. **(R5)** Re-importing the Órgano catalogue leaves every Órgano's marked/unmarked state
   exactly as the administrator set it.
7. **(R5, R18)** Unmarking an Órgano that already has imported contracts leaves those
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
11. **(R7)** A contract whose published awardee data yields no operador under SPEC-0006 R5 is
    **stored and browsable like any other**, showing every attribute it holds and **no awardee**
    — since the awardee is held on the operador that award did not produce. It is not rejected,
    and it offers no awardee route that leads nowhere.
12. **(R8)** An Órgano's initial import yields **every publication the source holds for it,
    back to its earliest published date** — not merely contracts from years before the current
    one, which a run covering only the last few years would also produce.
13. **(R8)** A scheduled import of an Órgano whose initial import has completed re-reads only
    a recent window, not the whole history, and picks up a correction published inside that
    window. An Órgano whose initial import has not completed is not treated as up to date by
    the scheduler.
14. **(R9)** An initial import interrupted part-way retains the contracts it already stored,
    and is resumed to completion **without an administrator having to intervene**; an
    administrator can also resume it on demand. Resumption adds no duplicates. While it runs,
    an administrator can see that it is in progress and how far it has got. *(That last half is
    proven by [SPEC-0007](SPEC-0007-monitor-import-runs.md)'s progress requirement
    (SPEC-0007 R5); a task
    claiming this criterion should say which half it proves.)*
15. **(R10)** An administrator can request a full historical re-read of an already-loaded
    Órgano; a correction to a publication **older** than the incremental window is picked up
    by that re-read and by no routine run, and the re-read creates no duplicates.
16. **(R11)** Re-importing after a contract's published attributes change updates that
    contract in place: its identity is unchanged and the refreshed attributes are shown.
17. **(R12)** Running two imports of the same published contracts in succession yields the
    same stored set with no duplicates and no attribute changes; a contract stored by an
    earlier import that is absent from a later import's results is still present and
    unchanged afterwards.
18. **(R13)** An administrator can remove a stored contract, after which it appears in no
    Órgano list and no operador history; a subsequent import that still finds it published
    does not re-add it; and an administrator can restore it.
19. **(R14)** A `USER` reaches an Órgano's contracts by browsing the taxonomy tree and
    selecting an Órgano from it; that tree offers the `USER` no control to create, rename,
    move, delete or reassign anything. *(Also satisfies SPEC-0004 #9 and the deferred half
    of SPEC-0004 #2.)*
20. **(R14)** An Órgano that is marked and imported but **unclassified** — placed in no
    taxonomy term — is still reachable and its contracts are still viewable; so is one that
    has since become inactive but retains contracts under R5.
21. **(R14)** An Órgano's own contratos menores list states no row's awarding Órgano, every
    row on it belonging to the Órgano already open — while still stating each row's awardee,
    under the spelling SPEC-0006 R4 selects for that operador.
    > R14's third route — following a row's awarding Órgano to that Órgano's contracts — is
    > **proved by [SPEC-0006](SPEC-0006-operadores-economicos.md)'s criterion for it**, since no
    > surface this spec delivers has rows naming an awarding Órgano. Stated here, proved there;
    > a task claiming this criterion owes only the half above.
22. **(R15)** Opening an Órgano presents its contracts split by family, with *contratos
    menores* as one family among those the system knows about; a family for which the system
    holds no data is omitted from the presentation rather than shown as an empty section, and
    its absence causes no error in the families that remain.
23. **(R16, R17)** An Órgano's contratos menores list is paginated under R17's control: it
    states how many contracts the current selection contains and how many pages it spans, a user
    can move to the first, previous, next and last page and jump to a chosen page, and paging
    through the whole selection yields exactly that many contracts with none repeated and none
    skipped. From the last page the control offers no further next, and from the first no
    further previous.
24. **(R17)** Paging alone never changes the selection: moving between pages leaves the stated
    count, the page total and the ordering unchanged. Changing the selection — applying or
    clearing a filter, or changing the sort — returns the reader to the first page.
25. **(R16)** Each contract row offers a way to reach that contract's publication at the
    official source, and a way to reach its awardee's operador where one exists.
26. **(R18)** An Órgano with no stored contratos menores presents no contratos menores section
    at all — equally so whether it is unmarked, or marked and imported having awarded none —
    while an Órgano holding at least one presents the section; and an Órgano whose initial
    import is still running presents it stating that what is shown is partial, distinguishably
    from one whose import has completed.
27. **(R19)** An Órgano's contratos menores are always scoped to one publication year: opening
    the section selects the most recent year the Órgano has contracts in, the years offered are
    exactly those it has contracts in, no control clears the year to produce an all-years list,
    and the contracts shown for a chosen year are exactly those whose publication date falls in
    it. No CPV filter control is present.
28. **(R19)** Sorting by publication date returns contracts in date order, and sorting by
    amount returns them in amount order, in the chosen direction; the first page after
    sorting descending by amount contains the largest-amount contract of the **whole**
    selected year, not merely the largest of the page previously displayed.
29. **(R20)** An administrator can trigger an import scoped to all marked Órganos and one
    scoped to a single Órgano; each covered Órgano runs **initially** if its history was never
    loaded, **resumes** if its initial import is incomplete, and runs **incrementally**
    otherwise; and the reported outcome states succeeded, failed or partially succeeded, the
    Órganos covered and which of them failed, and contracts added and refreshed.
30. **(R20, R23)** A manual run covering several Órganos where one fails reports **partially
    succeeded** and names the failing Órgano — not a bare success and not a bare failure.
31. **(R21)** With no human trigger, the scheduler runs and contracts published since the
    previous run become browsable for every marked, active, initially-imported Órgano.
32. **(R22)** While any import is in progress, a further trigger — of the same Órgano, of a
    different Órgano, of all marked Órganos, or of SPEC-0004's catalogue import — does not
    start a second import, and is neither queued nor dropped without trace. Within a run
    covering several Órganos, no two are imported at the same time: each is finished before the
    next begins.
    > That the refusal is **recorded as a refused run** is
    > [SPEC-0007](SPEC-0007-monitor-import-runs.md) R4's to require and its criterion's to
    > prove; a task claiming this criterion owes the guard, not the record.
33. **(R4, R22)** A mark applied while another import is running is refused rather than queued,
    and the newly marked Órgano is nonetheless imported by the next scheduled run without being
    marked again — so a refused mark costs freshness, not data.
34. **(R20)** An explicit trigger naming a single Órgano that is unmarked or inactive starts no
    import and tells the administrator the Órgano is not eligible, distinguishably from a
    trigger refused because another import held the guard.
35. **(R22)** While a long-running initial import of one Órgano is in progress — simulated if
    necessary rather than waited out — the scheduler's incremental runs do not start, and once
    that import ends the next incremental run for each affected Órgano covers the whole period
    since its last successful import, so nothing published during the wait is missed.
36. **(R23)** When the source is unreachable or returns an unusable response for one Órgano,
    the import reports failure for it, contracts already stored are unchanged, contracts
    imported for Órganos processed earlier in the same run are retained, and the remaining
    marked Órganos are still imported.
37. **(R24)** Under the reference environment, dataset and concurrency conditions R24 states,
    the read latency of an Órgano's contract list is **measured and recorded** at the busiest
    single year of the largest Órgano — its first page and its count, a page deep in that
    year's selection, and both of those sorted by amount descending. The criterion is met by
    the measurements existing and being recorded against those conditions; it asserts no
    threshold, because R24 sets none until they do.
38. **(R25)** Across a period covering an initial import, a historical re-read and scheduled
    incremental runs, the system's request rate against the source stays within the
    configured budget, and no mode exceeds it.
39. **(R26)** No contract list is reachable without authentication, and every awardee name
    and fiscal identifier shown on a contract row is one the official source published for that
    awardee — the spelling SPEC-0006 R4 selects for its operador — with nothing added that the
    source did not publish.
40. **(R27)** Every value displayed matches what the official source publishes for that
    contract, with no value corrected, normalised, inferred, or enriched from elsewhere —
    **except the three R27 names**: the publication date, which is displayed as the date it was
    interpreted to rather than as its published text; the awardee, whose stored spelling is its
    operador's rather than that contract's; and every text value's **surrounding whitespace**,
    which is removed on the way in. A text value differing from its published form in any way
    other than leading and trailing whitespace is a defect — save the stated duration, capped at
    64 characters, which is the only bounded value and is never expected to reach its bound.
41. **(R27)** A displayed duration is accompanied by an indication that the source frequently
    publishes a per-Órgano default rather than a per-contract value.
42. **(R27)** A contract whose published amount or publication date cannot be interpreted is
    **stored rather than rejected at import**, with that value absent, and is ordered last when
    sorting by the value it lacks; one with no date appears in no year's selection but is
    reachable through the undated selection of R19, so no stored contract is unreachable. It is
    *displayed as published* only for the values R27 keeps as published — an uninterpretable
    date is shown as absent, its published text having not been retained.
43. **(R19)** An Órgano holding no contract whose publication date resists interpretation is
    offered **no undated selection at all** — the affordance is absent rather than present and
    empty, on the same reasoning that keeps an empty section from being rendered.
44. **(R4)** An Órgano that is marked, unmarked, and marked again imports everything published
    while it was unmarked, without re-reading the history it had already stored.
45. **(R8)** An Órgano that goes un-imported for longer than the incremental window — because
    the scheduler was down, or because a long import elsewhere held R22's single-import guard —
    loses no publications when importing resumes: the next run covers the whole period since
    its last successful import.
46. **(R4, R8)** An Órgano **unmarked while its initial import was still running** and later
    marked again is **resumed**, not treated as up to date: the import continues from what was
    already stored, adds no duplicates, and completes the Órgano's full published history.
    Contrast the criterion above for an Órgano **marked, unmarked and marked again**, where the
    same sequence on a history that was **already complete** runs incrementally instead — the
    two differ only in whether the initial import had finished, which is the fact R8 makes the
    system track.
47. **(R8)** Each of the four modes is distinguishable in what it retrieves: an initial import
    reaches the Órgano's earliest publication, a resumed import continues an incomplete one
    without restarting it, an incremental import reads only the recent window, and a historical
    re-read covers the full history of an already-loaded Órgano. No trigger selects a
    historical re-read automatically.
