---
status: draft
---

# SPEC-0008. Import and browse Licitacións

## Summary

The system imports the **licitacións** (tender procedures) published by
[contratosdegalicia.gal](https://www.contratosdegalicia.gal/portada.jsp) for the Órganos de
Contratación an administrator has selected, stores each procedure whole, and lets
authenticated users browse them. A licitación is a contract awarded through a competitive
procedure: unlike a contrato menor it is **published as a process rather than as a fact** —
announced before it is awarded, changed while it is open, sometimes split into **lotes** that
are awarded separately, and decided among **several operadores económicos who competed for
it**.

This is the second contract family, and it fills the side of the split
[SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) R15 left open. It reuses that
spec's machinery deliberately: the same per-Órgano opt-in mark, the same four import modes,
the same single-import guard, the same paging control, the same mandatory publication year.
What it adds is everything that follows from a procedure being competitive:

- **Every operador that applied is recorded, not only the one that won.** The source
  publishes the full list of *licitadores presentados* — per lote where a procedure has lotes,
  and for the procedure itself where it does not — and knowing who competed —
  and who competed and lost — is information the contratos menores family cannot produce at
  all.
- **A bidder or an awardee may be a UTE** (*unión temporal de empresas*): a temporary
  consortium with a fiscal identifier of its own, published together with the fiscal
  identifier and name of **each member firm**. A UTE is an operador económico in its own
  right and its membership is stored, so *what has this firm been part of* is answerable.
- **A licitación is worth opening.** Where a contrato menor's row is the whole record
  (SPEC-0005 R16), a licitación carries a procedure type, a classification, budget and
  estimated value, lotes, bidders, an award and a formalisation — so it has a page of its
  own.
- **A licitación is not finished when it is published.** It moves through states — open for
  offers, pending award, awarded, formalised, suspended by appeal — and the system follows it
  through them rather than importing it once.

Users reach licitacións exactly as they reach contratos menores: by selecting an Órgano from
the read-only taxonomy tree of [SPEC-0004](SPEC-0004-import-manage-organos-contratacion.md)
R9 or by searching it by name, and opening the **licitacións** section of its contracts. The
catalogue of operadores económicos those licitacións feed, and the cross-Órgano history each
operador carries, belong to [SPEC-0006](SPEC-0006-operadores-economicos.md); this spec is the
second family to feed it and the first to feed it something other than awards.

It describes the *what*. Retrieval mechanism, data model, scheduling and pagination
technology are decided in ADRs and features.

### What the source publishes

Measured against the live source on **2026-08-20**. The shape differs from contratos menores
in ways that drive several requirements below, so it is recorded rather than assumed:

- **Two retrievals per procedure, not one.** A per-Órgano **listing** gives one entry per
  licitación — its identifier, object, an amount, its **state**, its publication date and
  **the date it was last modified**. Everything else lives on a **per-procedure page**, one
  retrieval per licitación, which carries the whole record **in a single response**: the
  classification, the economics, the lotes, the bidders with their UTE members, the award and
  the formalisation. Nothing needs a third request.
- **The listing is retrievable whole.** Unlike the contratos menores listing, which answers
  only over a bounded date range at a time, this one returns an Órgano's **entire published
  history** in bounded pages. There is no date window to walk and none is needed.

  **The two families' listings are genuinely different mechanisms**, and this is recorded
  because the Órgano page makes them look alike: the date-range control it shows belongs to the
  **contratos menores** tab alone, and the licitacións tab offers a state filter and no dates at
  all. Requesting a date range against this listing is not an error and not a filter — it is
  **silently ignored**, and every entry comes back regardless. A feature that assumed otherwise
  would believe it had read a window and would in fact have read the whole history in an
  arbitrary order, which is a failure that reports success.
- **The source states when an entry last changed, and will order by it.** The listing carries
  two dates per entry — when the procedure was published in CPG, and **when it was last
  updated** — and it can be asked to return entries **ordered by that update date, newest
  first**. This is the facility contratos menores lacks entirely, and it decides what an
  incremental import can promise: a change to a procedure of any age is **discoverable**, and
  discoverable **cheaply**, rather than reachable only by re-reading a window it happens to fall
  in. SPEC-0005 R8's window design exists because neither fact was available; here both are, and
  R11 rests on them.

  **The update order is not the order the listing comes back in by default**, and asking for it
  is not optional for an incremental run: read in the default order, a run walking until it
  recognises what it already has would stop in the wrong place and miss every change behind it.
  A feature building this must order explicitly and must verify it got what it asked for.
- **The volumes are one to two orders of magnitude smaller.** The largest publisher, SERGAS,
  holds **16 798** licitacións against roughly 1.4 million contratos menores; Axencia Turismo
  de Galicia holds 1 064, Augas de Galicia 625, Portos de Galicia 385. The family is small
  enough that re-reading an Órgano's whole listing would be affordable — and, because the
  listing orders by update date, a routine run need not: it reads recent updates until it
  reaches what it already holds.
- **The cost is in the pages, not the listing.** One retrieval per licitación means an
  initial import of SERGAS is ~16 800 retrievals — hours at a courteous rate (R31) — while
  its incremental runs cost one listing walk plus one retrieval per changed procedure.
  R29's yielding and R30 exist because of that asymmetry.
- **Most procedures have no lotes, and the ones that do publish everything per lote.** CPV
  codes, NUTS codes, the award and the bidder list are published **per lote** on a procedure
  that has them, and against the procedure as a whole on one that does not; a procedure states
  how many it has, or states none. **Lotes are the minority case**: of a sample of licitacións
  taken on 2026-08-20, **4 of 100** procedures across three Órganos — SERGAS, Augas de Galicia and
  Axencia Turismo de Galicia — had any at all. A **later sample of 100 across five Órganos,
  weighted toward the largest publishers, found 15**; the direction is unchanged and the design
  below stands, but the second figure is the one to size against, since multi-lote procedures are
  commoner among the big publishers an initial import spends its time on. The requirements below are written so the common case —
  one procedure, one award, one bidder list — reads as the plain one, and lotes are what that
  case generalises to rather than the shape everything is modelled on.
- **A UTE is published with its members, and usually without an identifier of its own.** A
  bidder row for a consortium carries the UTE's name plus **each member's own fiscal identifier
  and name**. What it usually does not carry is a fiscal identifier for the UTE: measured over
  **35 consortium rows in 250 procedures**, only **2** published a real one, while 25 published
  a placeholder dash and 8 a `TEMP-…` value that is local to the publication and identifies
  nothing. Members are unaffected — all **80** member entries carried ordinary identifiers.

  **A consortium is recognisable by how the row is structured**, not by its identifier and not
  by its name. Membership is **published**, and the publication is the structure: a consortium's
  entry lists its members beneath it, and a single firm's does not. An earlier reading of this
  source recorded that a UTE "is distinguishable by its form — it begins with `U`"; that holds
  for the 6% the source identifies and fails for the rest. **7 of the 35** are published under a
  name that does not begin *UTE* at all, so the name is no better a test.

  R17 therefore rests on the publication, as it always did — but on the part of it the source
  reliably provides.
- **An awardee's fiscal identifier is published — by the formalisation, not by the award.** The
  resolution names its awardee **in text only**: over **119 award rows**, not one carried an
  identifier. The **formalisation** does, per lote, holding the contratista's name and identifier
  together, a UTE's own included. Measured over **284 award rows**, the identifier is published by
  the formalisation for **58%** and recoverable from the procedure's own bidder list for a further
  **7%**, leaving **36%** with a name and nothing else.

  **That split is almost exactly the state.** A **formalizado** procedure — the terminal state, in
  which the contract is signed — publishes it for **96%** of its awards; an **adxudicado** one,
  which by definition has no formalisation yet, for none, and depends on its bidder list. And the
  awards that resolve by neither route are **overwhelmingly pre-2013 records left in an
  intermediate state**: of those measured, 59 of 60 were published between 2008 and 2012 and one
  in 2026, while every recent adxudicado award resolved. So the gap is a **historical tail an
  initial import meets and a routine run barely sees**, not a standing property of the family.
  R18 states what follows for the tail.
- **Not every procedure has an awardee.** A licitación open for offers, pending award, or
  suspended by appeal has no award and no awarded amount, and may never acquire one — a
  procedure can end deserted or withdrawn. This is the single largest departure from
  contratos menores, where an award is what a publication *is*.
- **Weak or inconsistent in places, as before.** Text fields are padded, object text quality
  varies, and a procedure's own reference (*expediente*) is free text with no guaranteed
  form.

## Scope

Deliberately **out of scope**, each owned elsewhere or left to a later increment:

- **Operadores económicos** — the catalogue, identity and matching rules, how a user finds
  one, and the cross-Órgano history — belong to
  [SPEC-0006](SPEC-0006-operadores-economicos.md). This spec supplies what that catalogue is
  derived from, and R18 states exactly what it owes it. **UTE membership is the one thing
  this spec adds to that catalogue's shape**, and R17 states it here because this is the only
  family that publishes it.
- **Documents, mesas de contratación, the event history, appeals and annulment
  proceedings.** All are published on the procedure page and none is imported. They roughly
  double the record and the parsing surface, and none is needed to answer *who was awarded
  what, and who competed for it*. A later spec may add them; nothing here forecloses it.
- **Encargos a medios propios**, the third family the source publishes. It is a third side of
  SPEC-0005 R15's family split, not part of this one.
- **Exporting results** and **free-text search over contract objects.** Both are gaps
  SPEC-0005 records against SPEC-0001 and neither is closed here. Recorded so they stay
  visible rather than assumed closed.
- **Contracts of Órganos that are inactive**, on SPEC-0005 R3's rule and the note beneath
  it, and for its reasons.
- **Import run history and the administration surface over it**, owned by
  [SPEC-0007](SPEC-0007-monitor-import-runs.md). What this spec owes it is the facts a run
  produces: its mode, its counts, its outcome, and how far a resumable run has got.

### What this spec requires of its sibling specs

This family supplies things no earlier family did, and two sibling specs have to move before
the requirements that depend on them can be built. They are named here so that no feature
claims an acceptance criterion whose surface another spec still contradicts:

- **[SPEC-0006](SPEC-0006-operadores-economicos.md) has been amended to accommodate what R17
  and R18 produce**, and the amendments are named here so a reader of either spec can see which
  requirements are load-bearing across the pair. Its **Scope** now admits a relationship the
  source itself publishes while still excluding every link the system would have to work out;
  its **R7** makes an operador reachable while it holds a visible contract, **participation or
  UTE membership**, so a firm that has only ever bid and lost — or has only ever been a UTE
  member — is not catalogued and then left reachable from nowhere; its **R9** gains two sections
  that are not families and keeps both out of every count and total; its **family-supply list**
  admits participation and membership as **optional** facts, which contratos menores cannot
  supply and this family can; and its new **R16** and **R17** state what the catalogue does with
  them and what they add to its privacy analysis.

  **A second round of amendments followed**, once the source was measured against rather than
  assumed, and they are named on the same principle. In SPEC-0006: **R5** treats a published
  placeholder — a lone dash, a `TEMP-…` value — as unusable, so it cannot become an identity;
  **R16** and **#40** admit a membership whose consortium the source did not identify, and **R9**
  notes that such a consortium has no totals of its own; and **R3** admits a contract attaching to
  an operador whose identifier it did not publish, where its family bounds the derivation. In this
  spec: **R8**, **#9** and **#10** admit a classification the source does not put on a lote;
  **R16**, **R17**, **R18** and **#20**–**#24** admit the unidentified consortium; and **R18**,
  **R33** and a new **#46** settle how an awardee's identifier is reached.

  Criteria #20, #21 and #24 below are consequently **stated here and proved there**, on the
  device [SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) uses for the same situation:
  the surfaces belong to that spec, the import and storage half belongs to this one.
- **[SPEC-0007](SPEC-0007-monitor-import-runs.md) must be able to describe a run that
  yielded.** R29 requires a bulk load to release the import guard and resume later; that spec's
  R4 outcome vocabulary has no such outcome, and its R8 reads a run whose progress stops
  advancing as **abandoned** — which is exactly what a yielded run looks like from outside. What
  this spec owes it is therefore one more fact: a run that yielded is **distinguishable from one
  that was stopped or that failed**, and is **related to the run that resumes it**, so an
  administrator sees one import converging rather than a sequence of unexplained halts.

### Decisions taken, and what is left open

Five decisions are **settled** by this spec and stated here so no feature reopens them:

1. **The licitación is the browsable unit; the award is per thing awarded.** A procedure is
   one row and one page — whether it has no lotes, which is the common case, or several, each
   carrying its own classification, award and bidders beneath it (R8). The alternative — one row
   per lote — was rejected because it makes an Órgano's count mean something other than
   *procedures* and repeats a procedure across a list a reader is scanning, and it would do so
   to accommodate a minority of procedures.
2. **A UTE's members are operadores, the UTE is one where the source identifies it, and the
   award belongs to the UTE alone either way** (R17). A member's history shows the award as won
   *through* the UTE and excludes it from that member's own totals. Attributing it to every
   member as well was rejected: the same euro would be counted once per member and every
   cross-operador total would overstate real spending.

   **The identified/unidentified split is the source's doing, not a design choice.** It publishes
   a fiscal identifier for 2 of every 35 consortia, so the catalogue can hold only those; the rest
   are recorded on the bid they made, under their published name and with their membership. The
   no-double-counting property above holds under both, which is what makes the split acceptable
   rather than a compromise.
3. **Losing bidders are shown, and kept away from the money** (R18). Participation appears in
   an operador's history in a section of its own, and never in any awarded total.
4. **Open procedures are imported and shown**, marked by their state (R25). The system is
   meant to show what is being bought now, not only what has been bought.
5. **The single-import guard stays, and long runs must yield** (R29). One public source, one
   polite request stream — but an initial licitacións import that would hold the guard for
   hours has to be interruptible and resumable so daily freshness is never starved. That
   yielding behaviour is the one architecturally significant consequence, and it needs a
   decision **against**
   [ADR-0017](../architecture/0017-import-run-state-in-postgresql.md) rather than confirmation
   from it: that record derives an abandoned run from a progress timestamp and warns that a run
   row is inserted in exactly one place, "and a second insertion path would silently bypass the
   guard" — which is what re-claiming the guard after a yield would be.

One decision remains outside this spec:

- **The pacing model against the source** — R31 states the obligation;
  [ADR-0014](../architecture/0014-resilient-throttled-outbound-http-client.md) owns the bound
  and makes it configurable per source. This spec must not harden it. It matters more here
  than anywhere else in the system, because per-procedure retrieval is the largest volume of
  outbound requests the system will ever produce.

## Requirements

### Access

- **R1** — **Managing** licitacións is reachable only by users with the `ADMIN` role:
  triggering an import (R27), resuming an incomplete initial import (R10), requesting a full
  historical re-read (R12), and removing or restoring a licitación, a lote or a participation
  (R15). Marking an Órgano for import remains SPEC-0005 R4's affordance — R3 and R27 settle
  what it now means and what it triggers. A `USER` or an unauthenticated visitor that attempts
  any of these is denied.
- **R2** — **Reading** licitacións (R19–R25) is available to any authenticated user, `USER`
  or `ADMIN`, and grants no ability to modify anything. An unauthenticated visitor is denied.

### Selecting which Órganos are imported

- **R3** — **One mark covers both families.** The per-Órgano import mark that
  [SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) R4 puts in an administrator's
  hands governs this family too: an Órgano that is **active in the catalogue and marked for
  import** has its licitacións retrieved, and one that is inactive or unmarked has neither
  family retrieved. There is no second mark to set, and marking is still opted into
  deliberately and never by default.

  **What this costs is stated rather than hidden.** An administrator cannot take one family
  and decline the other, so marking a large publisher commits to both — the cheap licitacións
  and the expensive contratos menores alike. That is accepted because the alternative asks an
  administrator to reason about per-family retrieval cost, which is a property of the source
  rather than a decision they have information to make. If it turns out to be needed,
  splitting the mark per family is a later increment that would refine SPEC-0005 R4 rather
  than contradict anything here.
- **R4** — **Every already-marked Órgano gains a licitacións history to load.** When this
  family arrives, every Órgano already marked under SPEC-0005 R4 is, for licitacións,
  an Órgano whose initial import has **never started** — so R9's rule alone puts it in the
  initial mode on the next run it is covered by, with no administrator action and no
  re-marking. Import state is **tracked per Órgano and per family**: an Órgano may have
  completed its contratos menores history and not begun its licitacións one, and neither
  family's progress may be read as the other's.
- **R5** — Unmarking an Órgano, or an Órgano becoming inactive, stops **future** retrievals
  for it and stops any retrieval already in progress at a point that loses nothing already
  stored; the licitacións already imported are **retained** — still stored, still browsable,
  never deleted — on SPEC-0005 R5's rule and for its reasons.

### Importing licitacións

- **R6** — The system imports the licitacións published for each Órgano selected under R3 and
  stores each **whole procedure** as a record of its own, so a licitación is available and
  queryable independently of the source thereafter.
- **R7** — Each stored licitación carries the attributes the source publishes about the
  procedure: the **Órgano** that convened it, its **publication date**, the date it was **last
  modified at the source**, its **reference** (*expediente*), its **object**, its **state**,
  its **type of contract**, **type of procedure** and **type of processing** (*tramitación*),
  its **number of lotes**, and its **base budget** (*orzamento base*, VAT-inclusive) and
  **estimated value** (*valor estimado*, VAT-exclusive) — together with a stable identity by
  which the same procedure is recognised across successive imports.

  **Its classification, its award and its formalisation are held where R8 puts them**, which is
  per lote on a procedure that has lotes and against the procedure itself on one that does not.
  This requirement does not hold a second copy of them at procedure level: R8 promises exactly
  one place for each, and two would be one too many to keep in step.

  **The two economic figures are different things and are labelled as such wherever either is
  shown or totalled.** The base budget includes VAT and the estimated value excludes it;
  presenting either unlabelled invites exactly the wrong comparison, as SPEC-0005 R7 already
  requires of the contratos menores amount.
- **R8** — **Every licitación records its award and its competition in one place per thing
  awarded.** For the ordinary procedure — which has **no lotes**, and is most of them — that
  place is the procedure itself: one classification, one award, one bidder list. For a procedure
  **split into lotes**, it is each lote, because that is how the source publishes it and how the
  award was actually made.

  Whichever it is, the facts held are the same: a **CPV** and **NUTS** classification, an
  **award** — the awarded operador, the awarded amount, the resolution and its date, and the
  stated execution period — a list of **bidders** (R16), and a **formalisation** where there is
  one. The formalisation is **where the awarded operador's fiscal identifier is published**, which
  the award itself does not carry; R18 says what that means for a procedure not yet formalised. A procedure with lotes also holds each lote's number and description.

  **Classification is the exception, and the source is the reason.** A procedure that has lotes
  does not reliably publish its CPV and NUTS per lote — it often states them for the procedure as
  a whole while awarding each lote separately. So a classification is held **per lote where the
  source publishes it per lote, and against the procedure otherwise**, which is true of a
  procedure with lotes as well as one without. The award and the formalisation are unaffected:
  those genuinely are per lote, and the one-place rule holds for them exactly as stated.

  This is narrower than it looks. It does not create a second copy of anything — a classification
  still lives in exactly one place — it only admits that which place that is, is the source's
  choice rather than a consequence of whether the procedure has lotes. R23's CPV filter is
  written to match: a licitación is in the selection when **any** of its classifications carries
  the code, wherever it hangs.

  So every licitación has **exactly one place** its award and bidders are recorded, or several
  where it has several lotes, and no requirement below has to distinguish the two cases. Where
  one below says *per lote*, read it as *per lote, or per procedure where there are none* — the
  spec says it the short way rather than doubling every sentence.

  **The licitación remains the unit a user browses** (R20) and the unit whose identity R13
  reconciles on. Lotes are not separate contracts and are not separately listed, searched or
  counted. They are, however, the unit an **award** is — R18 and R24 say what follows from
  that for an operador's history and for a row's amount.
- **R9** — A licitación is imported in one of **four modes**, chosen **per Órgano and per
  family**, on the same rule and with the same names as SPEC-0005 R8:
  - an **initial import**, which loads the Órgano's **full published licitacións history**. It
    is the mode for an Órgano whose licitacións history has **never been loaded**;
  - a **resumed** import, which continues an initial import left incomplete rather than
    restarting it (R10);
  - an **incremental** import, which re-reads the Órgano's listing and retrieves only what has
    changed (R11). It is the mode for an Órgano whose initial import **has completed**;
  - a **historical re-read** (R12), requested explicitly by an administrator for a single
    Órgano, which no trigger ever selects automatically.

  As in SPEC-0005 R8, the mode is decidable because the system knows, per Órgano **and per
  family**, which of three states its initial import is in — never started, started and
  incomplete, or complete — and a half-loaded Órgano is never treated as up to date.
- **R10** — An initial import is **long-running and interruptible without loss**: the largest
  Órgano's runs for hours, so one that fails, is interrupted, or **yields the import guard**
  under R29 keeps everything it already stored and is **resumed to completion** —
  automatically, and on demand by an administrator — adding no duplicates (R14). It is never
  left permanently incomplete for want of a trigger.
  While it is in progress an administrator can see that it is running and how far it has got,
  which [SPEC-0007](SPEC-0007-monitor-import-runs.md) R5–R7 render.
- **R11** — **An incremental import is driven by what the source says has changed.** Two
  promises define it, and both are observable without knowing how it is done:

  - a licitación the source declares **new or changed** is reflected by the **next routine
    run**, **whatever its age** — a procedure awarded years ago and formalised today is
    refreshed today;
  - a licitación the source declares **unchanged** is **not retrieved again**, which is what
    keeps a daily run over an Órgano's whole history affordable.

  This is a stronger promise than SPEC-0005 R8's window, and it is the source that makes the
  difference: there, a correction was reachable only by re-reading a window it happened to fall
  in, so anything older was reachable only by R12. Here age is irrelevant.

  **The source is asked for its listing in last-updated order, newest first**, which is what
  makes the promises above cost a few pages rather than an Órgano's whole history. That is
  stated as a requirement, not left as a feature's choice, because it is the one thing the
  promises depend on that the source does not do by default — and getting it wrong yields a run
  that reports success while reflecting nothing. What a run does with that order — where it
  stops, and how much it re-reads to be sure — is a feature's to decide, and R14's idempotence
  makes re-reading harmless.

  **What it still cannot promise is a change the source does not declare.** If the source
  amends a procedure without advancing its last-modified date, no routine run will notice, and
  R12's historical re-read is the only route to it. The assumption that the date advances with
  every change is **load-bearing and stated because it is load-bearing**, not because it has
  been proven.

  **So it is checked rather than trusted**, on the practice SPEC-0005 R28 sets for its own
  unproven population: a historical re-read of an imported Órgano (R12) reveals how many stored
  procedures it finds changed that no routine run had reflected, and that figure is recorded. If
  it is not near zero this requirement is revised — the incremental promise above is worth only
  what the source's declaration is worth.
- **R12** — An administrator can request a **full historical re-read** of a single Órgano: a
  re-run of the initial import over an Órgano already loaded, retrieving every procedure again
  regardless of what the listing says has changed. It is the only route to a correction the
  source made without declaring it, it is idempotent (R14), and it carries the same cost and
  resumability as an initial import (R10).

### Identity and reconciliation

- **R13** — A re-import reconciles against the stored licitacións rather than replacing them:
  a procedure new to the source is added; a procedure already stored is matched by its stable
  identity and its source-derived attributes are refreshed in place. **Within a refreshed
  procedure, its lotes, classifications, bidders and awards are reconciled to what the source
  now publishes** — one the source no longer publishes for that procedure stops being shown,
  because unlike a whole publication these are parts of a record the source has just restated
  in full.

  **Stops being shown, not erased.** A lote, a bidder or an award dropped by a restatement is
  **retained and marked withdrawn**: it appears in no list, no history and no total, and an
  administrator can restore it exactly as R15 allows for a whole licitación. This is not
  tidiness — [SPEC-0006](SPEC-0006-operadores-economicos.md) requires every feeding family's
  removal rule to be **non-destructive and reversible**, and rests the reversibility half of its
  R12 privacy analysis on every family keeping it. A participation that an ordinary import could
  erase, with no administrator act and no way back, would break that promise for the whole
  catalogue and could make an operador unreachable without anyone deciding to.
- **R14** — Importing is idempotent: importing the same published procedures twice in
  succession leaves the stored set and its attributes unchanged and creates no duplicates.
  **An import never deletes a stored licitación** — one absent from a later import is retained
  unchanged, because absence is not evidence of withdrawal.
- **R15** — Because R14 makes absence meaningless, withdrawal has to be explicit: an
  administrator can **remove a stored licitación** the source has withdrawn. A removed
  licitación is **remembered as removed**: it disappears from every list, every operador
  history and every total, and a later import that still finds it published does **not**
  re-add it. An administrator can restore a removal made in error. Removal is never automatic,
  and it is not the mechanism for a corrected publication — a correction is refreshed in place
  by R13. The same act is available for a **lote** and for a **participation**, which is what
  R13's withdrawal produces and what an administrator restores.

  Removal propagates to [SPEC-0006](SPEC-0006-operadores-economicos.md) under that spec's
  operador lifecycle rule (R7) — for awardees, bidders and UTE members alike, once that rule
  reaches the participations and memberships this family supplies, which is one of the
  amendments Scope names.

### Operadores: who competed, who won, and UTEs

- **R16** — **Every operador that applied is recorded.** For each award point (R8) the system
  stores the **full list of licitadores presentados** the source publishes — whether or not it
  won, and which of them was awarded. Each becomes an operador económico under
  [SPEC-0006](SPEC-0006-operadores-economicos.md) R3's identity rule, exactly as an awardee
  does.

  **A published fiscal identifier that is unusable under SPEC-0006 R5 yields no operador**, and
  this requirement states what follows rather than borrowing a rule from elsewhere. The same
  rule governs a bidder, an awardee and a UTE member:

  - the **licitación is stored and stays visible** — R25's test is the publication date and
    nothing else, so a procedure is never withheld over a party it could not resolve;
  - that party **is recorded as no participant and no awardee**, because the name it was
    published under is held on the operador it did not produce (R18) and there is nothing
    to hold it on;
  - **every other party on the same procedure is unaffected** — one unusable identifier
    removes one row from a bidder list, not the list.

  So a licitación can show an award and name nobody, and R25 says why that is accepted here
  when [SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) R28 refuses it there.

  **A consortium is the one party this rule does not remove**, because the source names it,
  structures it and lists its members while publishing no identifier for it in 94% of cases. A
  party the source declines to identify is not the same as a party it does not publish: the
  first has nothing to catalogue, the second has nothing at all. So an unidentified consortium
  **is recorded as a participant** — under its published name, with its membership — and R17
  states what that record holds. Removing it would discard the competitive information this
  spec exists to expose, on 33 of every 35 consortia.

  This is information the contratos menores family cannot produce at all, and it is the reason
  this spec touches SPEC-0006's shape rather than merely feeding it.
- **R17** — **A UTE is an operador in its own right, and its membership is stored.** Where the
  source publishes a bidder or awardee as a *unión temporal de empresas*, the system stores:

  - the **UTE itself** as an operador **where the source publishes a fiscal identifier for
    it**, under SPEC-0006 R3, and **as a named consortium on the bid it made** where it does
    not. Both are records of the same fact; they differ only in whether the catalogue can hold
    one, and the source settles that, not the system;
  - **each member firm** as an operador, identified by its own published fiscal identifier —
    and a member whose identifier is unusable yields no operador and no membership, under
    R16's rule, without costing the UTE or its other members anything;
  - the **membership** between them, so a UTE states who its members are and a member states
    which UTEs it has been part of.

  **A consortium the source does not identify is still recorded in full.** It cannot be
  catalogued as an operador — SPEC-0006 R3 makes the fiscal identifier the identity and R5
  rightly forbids inventing one — so what holds it is the **bid itself**: the consortium's
  published name, the fact that the bidder was a consortium, and each membership. Its members
  are operadores either way, since they publish ordinary identifiers, so *what has this firm
  been part of* stays answerable from the member's end. What is not answerable is the reverse:
  an unidentified consortium has no catalogue entry to open, and its members are named on the
  licitación that published it rather than on a page of its own.

  A UTE's identifier, where there is one, is recognisable by its form — in Spain it begins with
  `U`. That is a fact about how the identifier reads and **not a test the system identifies
  consortia by**: the source publishes such an identifier for only 6% of them, and 7 of every 35
  are published under a name that does not begin *UTE* either. The paragraph below says what the
  system actually relies on.

  **The award belongs to the UTE alone**, catalogued or not. A licitación awarded to a UTE is
  one award, held by the UTE where it is an operador and naming the consortium where it is not —
  and in **neither case** does it enter a member's totals. That property does not depend on the
  UTE being catalogued, which is what makes the two records above equivalent where it matters:
  no euro is counted twice under either. Where the consortium is uncatalogued there is simply no
  total of its own for the award to appear in, and a member's history shows it as won *through*
  that consortium exactly as it would otherwise.

  The same holds for participation (R18): a bid submitted by a UTE is the UTE's bid, visible
  from each member as *through* it, and counted as no member's own.

  **Membership is published, not inferred**, and this requirement rests on the publication —
  on the way a consortium's entry is **structured**, listing its members beneath it, which is
  what the source reliably provides. The `U` prefix corroborates it where present and is absent
  far more often than not; it is not the source of the fact. This
  is why the requirement does not breach
  [SPEC-0006](SPEC-0006-operadores-economicos.md) R6's refusal to classify operadores: that
  rule declines to **derive** whether an operador is a natural person or a legal entity, while
  this one records a relationship the source itself publishes.

  **Membership is recorded as published and is not maintained over time.** A UTE is
  constituted for one procedure, so its membership is a fact about that publication rather
  than a standing relationship the system tracks; R13's reconciliation keeps it in step with
  what the source currently publishes and nothing else updates it.
- **R18** — **What this family supplies to the operadores catalogue.**
  [SPEC-0006](SPEC-0006-operadores-economicos.md) requires seven facts from every feeding
  family and admits two further ones from a family able to supply them. This requirement settles
  all nine for licitacións, which supplies every one. The seven required first:

  - **awardee name and fiscal identifier** — the name as published at the award point (R8),
    and for a UTE the UTE's own (R17). **The identifier is published elsewhere on the record than
    the award**, which is a departure from every other family and is stated rather than glossed.
    It is taken from the first of these that has it:

    - the **formalisation**, which publishes the contratista's identifier per lote and is the
      route for the majority of awards, and for 96% of those on a formalised procedure;
    - the **bidder list of the same procedure**, where the awardee appears among the bidders with
      its own identifier;
    - the **catalogue**, by matching the published name — the only step that infers anything, and
      the only route left for a procedure that is awarded but not yet formalised and published no
      bidder list. That population is dominated by pre-2013 records, so it is a historical tail
      rather than a standing gap.

    **What the last step may and may not do** is fixed here, because it is the only inference
    this spec permits (R33) and an unbounded one would silently merge suppliers:

    - it **never creates an operador**. Only a published fiscal identifier does that
      (SPEC-0006 R3, R5), so a name that matches nothing yields **no awardee**, and R16's
      consequences follow unchanged;
    - it **links only where exactly one operador matches**. Two operadores sharing a name yield
      no link, because SPEC-0006 R3 holds that merging two real suppliers is as damaging as
      splitting one;
    - it **is recorded as derived**, so a link the system inferred is distinguishable from one
      the source published, and can be withdrawn without disturbing the rest.

    **The name is supplied, not stored on the contract**: as in SPEC-0005 R7 it is held once on
    the operador the identifier resolves to, so what any row shows is the name SPEC-0006 R4
    selects and every published spelling feeds that spec's retained names (R15). This family
    stores **one** per-row name and no other: the published name of a **consortium the source
    does not identify** (R17), which has no operador to hold it and would otherwise appear as a
    bidder that is nobody. Every other party is named through its operador, which is why R16's
    unusable identifier leaves it with nothing to display rather than a name without a link;
  - **a comparable date** — the licitación's **publication date**, which every licitación has
    from the day it appears, including one never awarded. The award date is published too and
    is shown on the licitación, but it is not what orders the history: an open procedure has
    none, and a family whose rows could not be ordered until they were decided could not be
    merged with another family's at all;
  - **an amount** — the **awarded amount**, VAT-inclusive, so it is comparable with a contrato
    menor's. The base budget and estimated value stay on the licitación and never enter a
    cross-family total (R24);
  - **the awarding Órgano** — the Órgano that convened the procedure;
  - **a stable, totally ordered contract identity** — the **publication identifier**, and, on a
    procedure that has lotes, **the lote alongside it**, ordered by publication identifier and
    then by lote. A procedure with no lotes is identified by its publication identifier alone,
    which is the ordinary case; the lote joins it only where one exists, and the order is total
    either way. The lote is part of it at all because a procedure awarding five lotes to five
    operadores would otherwise offer one identifier for five distinct history rows — which
    SPEC-0006 R4's tie-break and R9's rows both need to tell apart;
  - **an explicit, non-destructive, reversible removal rule** — R15;
  - **a family name** — *licitacións*.

  **One row of an operador's history is one award.** For most licitacións that is one row per
  procedure, since most have no lotes. Where a procedure has lotes the award is the **lote's**,
  so an operador that won two of five holds **two** rows, each carrying that lote's own awarded
  amount and each naming the procedure it belongs to and the Órgano that convened it. This
  follows from R8 recording the award where the source makes it: any other unit would either
  attribute money to an operador that another firm was awarded, or state a figure the source
  never published.

  **And one fact no other family supplies: participation.** An operador's history shows the
  licitacións it **bid on and did not win**, in a section of its own, distinct from what it
  was awarded. A participation is never counted in an awarded total, never summed with one,
  and never presented as money the operador received — it carries no amount of its own. This
  is what makes *who competes for this Órgano's work* answerable while leaving every spending
  figure in the system meaning what it says.

  **The two are per lote as well**, and an operador can hold both for one procedure: winning
  lote 1 and losing lote 2 puts a row in its awards and a row in its participations, naming the
  same licitación. That is not a contradiction to be resolved — it is what happened.

  **Participation and UTE membership are the two optional facts** SPEC-0006 R16 admits, and
  this family is the first to supply either. Scope names the amendments that let it.

### Finding and browsing licitacións

- **R19** — Any authenticated user reaches an Órgano's licitacións exactly as they reach its
  contratos menores: by browsing the **read-only taxonomy tree** of SPEC-0004 R9, by
  **searching an Órgano by name** (SPEC-0004 R19), or by following a **contract row's awarding
  Órgano** from an operador's history (SPEC-0005 R14, SPEC-0006 R9). The licitacións section
  takes its place in the family split SPEC-0005 R15 defines, alongside contratos menores.

  **A visible licitación puts its Órgano in the visible set.** SPEC-0004 R9 defines that set
  as every Órgano with at least one visible contract **in any family**, with each contract
  spec defining *visible* for its own; R25 is this spec's definition. So an Órgano that
  publishes licitacións and no contratos menores becomes reachable to a `USER` on the strength
  of this family alone — which is the case SPEC-0005 R15's family split was written to
  anticipate.
- **R20** — Within an Órgano's licitacións, a user sees a list showing, for each licitación,
  its identifier, its publication date, its object, its **state**, and its **amount** as R24
  defines it, together with how many licitacións the current selection contains. The list is
  **paginated** under
  [SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) R17's paging control, which this
  spec cites rather than redefining. Each row offers a way to reach the corresponding
  publication **at the official source**. No row repeats the awarding Órgano on a list already
  scoped to one.

  **A row names its awardee only when it has exactly one** — which a procedure with no lotes
  does once it is awarded, as does one whose lotes all went to the same operador. Where a
  procedure's lotes were awarded to more than one, the row states **how many** rather than
  picking one of them, and R21's page is where they are named. A row that names an awardee is a route to that operador under SPEC-0006 R8; a row that
  states a count is a route to the procedure, which is where the routes then are. Nothing here
  is a route that dead-ends: a party R16 could not resolve is simply not counted among the
  awardees the row states.

  **The list is ordered by publication date descending, with the publication identifier
  descending as its tie-break**, and that is the ordering SPEC-0005 R17 requires each list
  taking its control to state. Newest first is what a reader of tenders wants; the tie-break is
  not decoration — many procedures of one Órgano share a publication date, and without a total
  order "the next page" does not denote and exhaustive paging cannot be shown. R23's sorts
  replace the first key and keep the same tie-break, so every ordering the list can be in is
  total.
- **R21** — **A licitación has a page of its own**, reached from its row, showing everything
  the system holds about it: its reference, object, state, types of contract, procedure and
  processing, its base budget and estimated value, its classification, award and formalisation —
  held once for the procedure, or once per **lote** where it has them, each lote also naming
  itself — and its **bidders** —
  each named with its fiscal identifier, each a route to its operador, each UTE showing its
  member firms — with the winning bidder distinguished from the rest. Every party is named
  under the name SPEC-0006 R4 selects for that operador and that operador's canonical fiscal
  identifier, since R18 holds no per-row name of its own. The page states which
  bidders belong to which lote wherever the procedure has them, and links to the publication
  at the official source.

  This is where this family departs from contratos menores, which deliberately have no detail
  view because the row is the whole record (SPEC-0005 R16). A licitación's record does not fit
  on a row, and the competitive information this spec exists to expose lives almost entirely
  below it.
- **R22** — An Órgano's licitacións are **always scoped to a single publication year**, on the
  same rule SPEC-0005 R19 applies to contratos menores: a year is part of every selection,
  there is no all-years list and no way to obtain one, the year in effect when the section is
  first opened is the **most recent year the Órgano has visible licitacións in**, and only
  years it actually has visible licitacións in are offered.
- **R23** — Within that year a user can **narrow** the selection by **CPV code** and by
  **state**, and **sort** by **publication date** or by **amount**, ascending or descending.
  Narrowing, sorting and counting apply to the **whole year's selection**, not only to the
  page displayed, and changing the selection re-pages it from its first page under SPEC-0005
  R17.

  **Only codes and states the year's selection actually contains are offered**, on R22's rule
  for years and for its reason: choosing one can then never be the reason a list is empty. The
  **states offered are the source's own**, since R33 stores a procedure's state as published
  and this spec derives none of its own — so the vocabulary a reader filters by is whatever the
  source publishes, not a set fixed here.

  **The CPV filter closes a promise this spec inherited.** SPEC-0005 offers none because the
  source publishes no CPV for contratos menores, and defers CPV-based querying explicitly to
  this spec. A licitación whose CPV is published **per lote** (R8) is in the selection when
  **any** of its classifications carries the chosen code — one hanging off a lote or one held
  against the procedure as a whole, which R8 admits because the source publishes both — since the
  licitación is the unit selected (R8).
- **R24** — **Which amount a row states, and what a total counts.** An **awarded** licitación
  states its **awarded amount**, VAT-inclusive — and where the procedure has lotes, the **sum of
  the lotes awarded so far**, which for the ordinary lotless procedure is simply the one amount
  it was awarded. One with **nothing awarded yet** states its **base budget**, labelled as such,
  so the row says something about the size of what is being tendered rather than nothing. The
  two are never presented as the same figure, and a row always states which of the two it is
  showing.

  **A partly awarded procedure states the awarded part and says so.** Where some lotes are
  awarded and others are not, the row shows the awarded sum, marked as covering part of the
  procedure — not the budget, which the awards have already partly superseded, and not a mixture
  of the two, which would be a figure nothing published. Its **state** is the source's own
  (R33), which is where a reader learns the procedure is not finished.

  **Every total and every sum counts awarded amounts only.** A count of a year's licitacións
  counts procedures; a sum of a year's money sums awards. A budgeted figure is what an Órgano
  intends to spend, not what it has committed, and adding the two produces a number that is
  neither.

  **Sorting is the exception, and it is stated rather than left to contradict the rule above.**
  Sorting by amount orders a selection by the figure **each row states**, so an unawarded row is
  placed by its budget — the only figure it has — among rows placed by their awards. That is
  accepted because an ordering makes no claim of comparability the way a sum does: it puts rows
  in an order, and each row still says which figure put it there. What is forbidden is
  **adding** them together.
- **R25** — **A licitación the system stores but does not show.** A licitación is **visible**
  when it has an **interpretable publication date** — and that is the whole test. One without
  a date cannot be placed in the year every selection is scoped to (R22), so there is no list
  it could appear in; it is **stored** as it arrived (R33) and shown to no reader.

  **An award is deliberately not required**, and this is the sharpest departure from
  SPEC-0005 R28. There, a contract with no awardee is withheld because an award with no
  recipient answers the question it exists to raise. Here a procedure with no awardee is not
  an incomplete record — it is a **complete record of a procedure that has not been decided**,
  and showing it is the point of importing open procedures at all (R9's initial mode loads
  them, R11 follows them to their conclusion). Neither is an **awarded amount** required: a
  procedure may end deserted or withdrawn and never acquire one.

  **Neither is a resolvable awardee**, and this is the second departure. Under R16 an award
  whose published identifier is unusable yields no operador, so the licitación shows an award
  and names nobody. SPEC-0005 R28 withholds exactly that row; this spec shows it, because here
  the award is one fact among many the procedure publishes — its object, budget, classification,
  state and bidder list are all intact and all worth reading — while there the award **was** the
  publication, and a contrato menor row with no awardee held nothing left to say. Adding a second
  limb to this test would also cost it the property that makes it worth having: that a reader can
  be told, in one sentence, what the system does and does not show them.

  An **administrator** can obtain an Órgano's undated licitacións, each identifiable and each
  carrying the route to its publication at the source. As in SPEC-0005 R28, the withholding
  must not create rows nobody can see; unlike there, the population is expected to be
  negligible, because the source publishes its dates in one fixed form.

  > **The administrator's surface is the one SPEC-0005 R28 already owes**, whose criterion that
  > spec carries as **unowned** because no anomalies surface exists yet. This requirement adds
  > licitacións to it rather than asking for a second one, and #30 below is carried unowned for
  > the same reason.
- **R26** — **An Órgano holding no visible licitacións shows no licitacións section at all**,
  under SPEC-0005 R18's rule and vocabulary — *visible* rather than *stored*, so an Órgano all
  of whose licitacións have been removed under R15 shows no section, exactly as one that never
  had any. An empty section is never rendered. Once present the section is never empty, since
  R22 offers only years the Órgano has visible licitacións in.

  While the Órgano's **initial licitacións import has not finished**, the section says so:
  what is shown is partial (R10), and a user must not read a growing list as a complete one.
  An Órgano that was imported and has since been unmarked or become inactive keeps its section
  and its retained licitacións (R5), and says that it is no longer being updated.

### Triggering imports

- **R27** — An administrator can trigger a licitacións import on demand. The trigger states
  its **scope** — every marked, active Órgano, or one chosen Órgano — and runs each covered
  Órgano in the mode R9 dictates for it. An Órgano that is unmarked or inactive is not
  retrieved however the trigger arrived, and the administrator is told why. The administrator
  is shown the outcome: whether it **succeeded, failed, or partially succeeded**, which
  Órganos were covered and which of them failed, and how many licitacións were added and
  refreshed.

  **Marking an Órgano triggers both families.** SPEC-0005 R4 makes a mark a **request to
  import**, not merely a flag, and R3 makes one mark govern two families — so a new mark
  requests an import of **that Órgano**, each family in the mode its own state dictates,
  **contratos menores first and licitacións after**, within **one run** under R29's guard. It is
  refused as a whole when the guard is held, and recovered as a whole by R28's next scheduled
  run, exactly as SPEC-0005 R22 describes for a single-family mark.

  The order is fixed rather than left open so that a partly loaded Órgano is always partly
  loaded in the same way, and it is that order because contratos menores is the family a marked
  Órgano is most likely to hold nothing of — settling it quickly, and leaving the long load
  last, where R29's yielding can interrupt it without stranding the other family behind it.
- **R28** — The system imports licitacións automatically on a recurring schedule, **daily or
  more often**, without any human trigger, covering every Órgano selected under R3 in the mode
  R9 dictates for it — **initial** for one whose licitacións history has never been loaded,
  **resumed** for one whose initial import is incomplete, **incremental** for one already
  loaded.

  **The initial mode belongs in that list and is not an oversight**: R4 rests on it entirely.
  Every Órgano already marked when this family arrives has never had its licitacións loaded, and
  nothing re-marks it — so a scheduler that only ever ran incremental and resumed imports would
  leave every one of them permanently empty while reporting success.
- **R29** — **At most one import runs at a time, across the whole system**, on SPEC-0005 R22's
  rule: the guard is global, it spans every family and the catalogue import alike, and a
  trigger that finds it held is **refused** and recorded as such rather than queued.

  **Routine daily work is never starved by a bulk load.** That is the obligation, and it is
  what a reader should be able to check: R28's schedule keeps running while an Órgano's history
  is being loaded, rather than waiting hours behind it. Without it, R28's daily promise and
  R10's hours-long initial import contradict each other, and the contradiction resolves silently
  in favour of whichever ran first.

  What the obligation costs is one behaviour this spec does require: an initial import or
  historical re-read **releases the guard at a point that loses nothing already stored**, and is
  resumed under R10, rather than holding it to completion. The interval, the yield points and
  how a yielded run is scheduled back belong to a feature and to the ADR Scope names.

  **A yielded run must not look like a failed one.** It is neither stopped nor abandoned, and
  [SPEC-0007](SPEC-0007-monitor-import-runs.md) cannot yet say so — which is the sibling
  obligation Scope records, and the reason an administrator watching a large Órgano load must be
  shown one import converging rather than a series of unexplained halts.
- **R30** — **A run carries on past a failure and reports it.** A failing Órgano does not stop
  a multi-Órgano run, and — because this family retrieves one page per procedure — **a
  procedure whose retrieval fails does not fail its Órgano**: the run records it, continues,
  and leaves that procedure to be retried, since it remains new or changed as far as R11 is
  concerned. A run in which anything failed reports **partially succeeded**, naming what
  failed, rather than a bare success or a bare failure.

  **A failure leaves what is already stored intact**, on SPEC-0005 R23's rule: a run that fails
  part-way never wipes, truncates or half-updates an Órgano's stored licitacións, so a failed
  run costs freshness and never data. [SPEC-0007](SPEC-0007-monitor-import-runs.md) R9's
  diagnostics assume it, and a reader looking at a partly succeeded run must be able to trust
  that what it did not reach is untouched rather than unknown.

### Non-functional expectations

- **R31** — The import is **courteous to the public source**: across everything the system
  retrieves — every family's imports and the catalogue import alike — its total request rate
  stays within a budget that keeps it a negligible load on a public service, and it never
  fetches as fast as it can. The concurrency bound and the interval are configured per source
  and decided outside this spec.

  This family binds hardest of all, and the reason is arithmetic: one retrieval per procedure
  makes an initial import of a single large Órgano the longest sustained stream of requests
  the system produces against the source. R29's yielding is what keeps that stream from being
  the only thing the system does for hours; R31 is what keeps it thin while it lasts.
- **R32** — Browsing stays responsive at the volumes this family reaches. It is measured under
  [SPEC-0005](SPEC-0005-import-browse-contratos-menores.md) R24's **reference environment and
  conditions** — the production deployment, its busiest year, first page and a deep page, in
  the default order and sorted by amount descending — so the four specs' numbers stay
  comparable. **The Órgano measured is the one holding the most licitacións**, stated because
  "the largest Órgano" no longer denotes now two families exist: the largest by contratos
  menores need not be the largest by licitacións, and a measurement taken on the wrong one would
  describe neither family's worst case. As there, **no latency budget is fixed**: the
  obligation is to measure and record, and a budget is set by revising this requirement once
  measurements exist.

  Two reads are measured here that have no counterpart in SPEC-0005, because they are the ones
  this family adds: **a year's selection narrowed by CPV**, and **a single licitación's page
  with its lotes and bidders**.
- **R33** — Published values are stored and displayed **as published**, with no correction,
  normalisation or inference, and enriched from no other source — save the **one** inference
  R18 defines and bounds, which is the derivation of an awardee's fiscal identifier the source
  does not publish. That exception exists because the alternative is a family that cannot supply
  an awardee at all, and it is confined to **which operador a row links to**: no published value
  is altered by it, the comparison it uses is never stored or displayed, and a link it produces
  is recorded as derived. Everything below governs values and is unqualified.

  The rule for values follows SPEC-0005 R27 and its narrowings: text values trimmed of
  surrounding whitespace and nothing else, amounts stored as numbers, and dates interpreted
  rather than kept as text. A value that cannot be
  interpreted leaves that value absent and the licitación **stored**, never rejected — losing
  a real procedure is the larger harm.

  **The state is published and is stored as published.** The system does not derive a state
  from what a procedure holds — an unawarded procedure is not inferred to be open — because
  the source's own vocabulary distinguishes cases the system cannot: pending award, deserted,
  withdrawn, and suspended by appeal are different facts, and only the source knows which
  applies.

  **Two of SPEC-0005 R27's narrowings do not carry over**, and this requirement says so rather
  than leaving a reader to wonder which rule governs the nearest analogue. The **stated
  execution period** a lote's award carries (R8) is **not capped in length** and is **not marked
  unreliable**: both of those exist there for a field the source fills with a per-Órgano default
  far more often than a real term, and this one is published per award as part of a resolution.
  If it turns out to behave like its contratos menores counterpart, marking it is a later
  increment — measured first, as SPEC-0005 R27's own cap was not.
- **R34** — Where a bidder or awardee is a natural person, its name and fiscal identifier are
  **personal data**, and every read requires authentication (R2). At the level this spec owns
  the system reproduces what the source already publishes and adds nothing to it. The derived
  information — aggregation across Órganos, and searchability by identifier — is created by
  [SPEC-0006](SPEC-0006-operadores-economicos.md) and acknowledged there.

  **This family adds one class of derived information that spec does not yet cover**, and it
  is named rather than glossed: a record of **who competed and lost**, which the source
  publishes per procedure but nowhere aggregates. Assembling one operador's unsuccessful bids
  across every Órgano is new information about that operador, and for a natural person it is
  new personal data. It is judged acceptable on the same grounds SPEC-0006 R12 uses — every
  underlying fact is already published by an official source, and access requires
  authentication — and it is recorded here so the judgement is visible rather than implicit.

## Acceptance criteria

> **Criteria are appended, not inserted.** Numbers are cited from features and tasks, so a new
> criterion takes the next free one rather than a place in the sequence.

1. **(R1)** A `USER` or an unauthenticated visitor that attempts to trigger a licitacións
   import, resume an incomplete initial import, request a historical re-read, or remove or
   restore a licitación, a lote or a participation is denied; an authenticated `ADMIN` is
   allowed.
2. **(R2)** An authenticated `USER` can view an Órgano's licitacións; an unauthenticated
   visitor that requests them is denied.
3. **(R3)** After an import run, an Órgano that is active but **unmarked**, and one that is
   marked but **inactive**, both have no licitacións stored from that run; only Órganos that
   are active **and** marked do — with no mark other than the one SPEC-0005 R4 defines.
4. **(R3, R27)** Marking a previously unmarked Órgano results in **both** families being
   imported for it, contratos menores first and licitacións after, within one run: immediately
   when the guard is free, and otherwise refused as a whole and recovered by the next scheduled
   run without the administrator marking it again.
5. **(R4)** An Órgano whose contratos menores initial import has already completed, and which
   has never had licitacións loaded, is covered by the next run **initially** for licitacións
   without being re-marked; its contratos menores state is unchanged by that run, and neither
   family's completion is read as the other's.
6. **(R5)** Unmarking an Órgano that already has imported licitacións leaves them stored and
   browsable, a subsequent import retrieves nothing further for it, and its section says it is
   no longer being updated.
7. **(R6, R7)** After an Órgano's initial licitacións import completes, every licitación the
   source publishes for it is stored, each carrying its reference, publication date,
   last-modified date, object, state, contract/procedure/processing types, number of lotes,
   base budget and estimated value.
8. **(R7)** Wherever a base budget or an estimated value is shown, it is labelled as
   VAT-inclusive or VAT-exclusive respectively, and the two are never presented as one figure.
9. **(R7, R8)** A procedure's award and formalisation are held in exactly one place — per lote
   where it has lotes, against the procedure where it does not — and nowhere is a second copy of
   them held at procedure level. A **classification** is likewise held in exactly one place, but
   which place is the source's: per lote where it publishes it per lote, and against the
   procedure otherwise, **including on a procedure that has lotes**.
10. **(R8)** A licitación with **no lotes** holds one CPV, NUTS, award and bidder list against
    the procedure itself; one with several holds its awards and bidder lists per lote, stored as
    **one** licitación holding those lotes, and holds each classification wherever the source
    published it — against a lote, or against the procedure as a whole. Neither appears more than
    once in any list or count, and no requirement reads differently for the two.
11. **(R9)** One run covering several Órganos runs **initially** for an Órgano never loaded,
    **resumes** one whose initial import is incomplete, and runs **incrementally** for one
    already loaded — the mode differing per Órgano within the same run, and per family for the
    same Órgano.
12. **(R10, R29)** An initial licitacións import interrupted part-way — by failure, by the
    process dying, or by **yielding the import guard** — retains everything it stored, is
    resumed to completion without administrator intervention, and adds no duplicates. While it
    runs, an administrator can see it in progress and how far it has got. *(That last half is
    proven by [SPEC-0007](SPEC-0007-monitor-import-runs.md) R5–R7; a task claiming this
    criterion should say which half it proves.)*
13. **(R11)** A licitación the source declares changed is reflected by the next routine run
    **whatever its age** — including one published years before and modified today — and a
    licitación the source declares unchanged is not retrieved again by that run.
14. **(R11)** An incremental run reads the Órgano's listing **ordered by last-updated date,
    newest first**, and not in the order the source returns by default; a run that receives the
    default order instead does not treat what it read as an account of what changed.
15. **(R12)** An administrator can request a historical re-read of an already-loaded Órgano;
    it retrieves every procedure again regardless of what the source declares changed, and
    creates no duplicates.
16. **(R13)** Re-importing after a procedure changes updates it in place, its identity
    unchanged and its refreshed attributes shown; a lote or bidder the source no longer
    publishes for that procedure is **retained and marked withdrawn** — absent from every list,
    history and total — and an administrator can restore it. No import erases one.
17. **(R14)** Two imports of the same published procedures in succession leave the same stored
    set with no duplicates and no attribute changes; a licitación stored earlier and absent
    from a later import is still present and unchanged.
18. **(R15)** An administrator can remove a stored licitación, after which it appears in no
    list, no operador history and no total; a later import that still finds it published does
    not re-add it; and an administrator can restore it.
19. **(R16)** For a licitación with several bidders, every bidder the source publishes is
    stored and shown with its name and fiscal identifier, the awarded one distinguished from
    the rest, and each resolves to the operador its identifier identifies under SPEC-0006 R3.
20. **(R16, R25)** A licitación one of whose published fiscal identifiers is unusable under
    SPEC-0006 R5 is **stored and stays visible**: that party is recorded as neither participant
    nor awardee, every other party on the same procedure is unaffected, and where the
    unresolvable party was the awardee the licitación shows an award naming nobody — offering
    no route that dead-ends. **A consortium the source does not identify is not such a party**:
    it is recorded as a participant under its published name, with its membership, per R17.
21. **(R17)** A licitación whose bidder is a UTE **the source identifies** stores the UTE as an
    operador under its own fiscal identifier, each member firm as an operador under its own, and
    the membership between them; opening the UTE names its members and opening a member names
    the UTEs it has belonged to. One whose UTE the source **does not** identify — the ordinary
    case — stores the consortium on the bid it made, under its published name and with the same
    membership, and each member firm as an operador; opening a member still names it, while the
    consortium itself has no page to open and is named on the licitación instead. In both cases
    a member whose own identifier is unusable yields no operador and no membership, and the
    consortium and its other members are unaffected.
    > **Stated here, proved in [SPEC-0006](SPEC-0006-operadores-economicos.md).** The surfaces
    > this criterion describes are that spec's, and it cannot host them until the amendments
    > Scope names have landed. A feature under this spec owes the import and storage half.
22. **(R17)** A licitación **awarded** to a UTE counts as one award to that UTE and to no
    member: **no member's awarded total includes it**, and each member's history shows it
    identified as won through that consortium. Where the source identifies the UTE, the award
    also appears in the UTE's own awarded total; where it does not, there is no such total and
    the award is named on the licitación — the no-double-counting property holds either way, and
    it is the property this criterion exists to test. *(Stated here, proved in SPEC-0006.)*
23. **(R18)** An operador awarded a procedure with **no lotes** holds exactly **one** row in
    its contract history, identified by that procedure's publication identifier alone; one
    awarded **two of a procedure's five lotes** holds **two**, each carrying that lote's own
    awarded amount and each naming the procedure and the awarding Órgano. No row carries an
    amount another operador was awarded.
24. **(R18)** A licitación appears in its awardee's history in a *licitacións* section carrying
    its **awarded amount** — never its base budget or estimated value — and every party named on
    any row is named under the name SPEC-0006 R4 selects for that operador. This family holds no
    per-row name for any party the catalogue can hold; its **one** exception is the published
    name of a consortium the source does not identify (R17), which no operador can carry.
25. **(R18)** An operador that bid for a licitación and did not win it sees that licitación in
    a **participation** section of its history, separate from its awards, carrying no amount,
    and included in no awarded total. An operador that won one lote and lost another of the
    same procedure holds a row in each. *(Stated here, proved in SPEC-0006.)*
26. **(R19)** An Órgano that publishes licitacións and **no** contratos menores is reachable by
    a `USER` from the taxonomy tree and by name search, and its licitacións are viewable.
27. **(R19, R26)** Opening an Órgano presents its contracts split by family with *licitacións*
    alongside *contratos menores*; an Órgano with visible contracts of only one family shows
    only that family's section, and the absent one causes no error.
28. **(R20)** An Órgano's licitacións list states how many licitacións the current selection
    contains and pages through exactly that many under SPEC-0005 R17's control — first,
    previous, next, last and a chosen page, none repeated, none skipped — with each row
    offering a route to the publication at the official source and no row naming the awarding
    Órgano.
29. **(R20)** A row whose procedure has exactly one awardee names it and offers a route to that
    operador; a row whose lotes were awarded to more than one states how many awardees it has
    rather than naming one of them, and the page reached from it names them all.
30. **(R20)** The list is ordered by publication date descending with the publication
    identifier descending as tie-break, so two procedures published on the same date have a
    determinate order; every ordering R23 offers is likewise total, and paging over any of them
    repeats and skips nothing.
31. **(R21)** Opening a licitación from its row shows its lotes with each lote's
    classification, award and formalisation, and its bidders with the winner distinguished,
    each bidder a route to its operador, each UTE naming its member firms, and states which
    bidders belong to which lote.
32. **(R22)** An Órgano's licitacións are always scoped to one publication year: the section
    opens on the most recent year with visible licitacións, the years offered are exactly those
    with visible licitacións, and no control produces an all-years list.
33. **(R23)** Filtering a year by a CPV code returns exactly the licitacións of that year
    carrying it — including one that carries it on **any** of its lotes, and one that carries it
    against the procedure as a whole while having lotes — and filtering by
    state returns exactly those in it. Only codes and states the year's selection actually
    contains are offered, and the states offered are the source's own.
34. **(R23)** Narrowing, sorting and counting apply to the **whole** year's selection: the
    first page after sorting by amount descending holds the highest-amount licitación of the
    year, not merely of the page previously displayed, and applying or clearing a filter
    returns the reader to the first page.
35. **(R24)** An awarded licitación with no lotes states its **awarded amount**; one with
    lotes states the **sum of those awarded so far**, marked as covering part of the procedure
    while any lote is still undecided; one with nothing awarded states its **base budget**,
    labelled as a budget. Every total or sum over a selection counts **awarded amounts only**,
    and a sort by amount places each row by the figure it states.
36. **(R25)** A licitación **not yet awarded** — open for offers, pending award, or suspended
    by appeal — is imported, stored and **shown**, stating its state and naming no awardee. A
    licitación whose publication date cannot be interpreted is stored and shown to no reader.
    > The administrator's view of undated licitacións is **unowned**, as SPEC-0005 carries the
    > anomalies surface its R28 requires: this requirement adds licitacións to that surface
    > rather than asking for a second one, and no feature claims it until that surface exists.
37. **(R26)** An Órgano with no visible licitacións presents no licitacións section at all; one
    whose initial licitacións import is still running presents it stating that what is shown is
    partial, distinguishably from one whose import has completed.
38. **(R27)** An administrator can trigger a licitacións import scoped to all marked Órganos
    and one scoped to a single Órgano, and the reported outcome states succeeded, failed or
    partially succeeded, the Órganos covered, which failed, and how many licitacións were added
    and refreshed.
39. **(R28)** With no human trigger, the scheduler runs at least daily and covers each marked,
    active Órgano in the mode its own licitacións state dictates — **initial** for one never
    loaded, resumed for one incomplete, incremental for one loaded — so an Órgano marked before
    this family existed becomes loaded without any administrator action.
40. **(R29)** A trigger arriving while any import is running — of any family — is refused and
    recorded as refused rather than queued; an initial licitacións import of a large Órgano does
    not prevent the day's scheduled work from running; and a run that yielded is reported
    distinguishably from one that failed or was stopped, related to the run that resumes it.
    *(The reporting half is proven by SPEC-0007 once its outcome vocabulary admits a yield.)*
41. **(R30)** A run in which one Órgano fails reports **partially succeeded** and names it; a
    run in which a single procedure's retrieval fails records that procedure, completes the rest
    of its Órgano, and retrieves it on a later run; and in both cases every licitación already
    stored is present and unchanged afterwards.
42. **(R31)** During an initial licitacións import — the longest sustained stream of outbound
    requests the system produces — the aggregate request rate against the source stays within
    the configured per-source bound, counted across every import running or yielding at the
    time.
43. **(R32)** The measurements R32 names — first page and a deep page in the default order and
    sorted by amount descending, a year's selection narrowed by CPV, and a single licitación's
    page with its lotes and bidders — are taken on the Órgano holding the most licitacións,
    under SPEC-0005 R24's reference environment, and recorded with the volume they were taken
    at.
44. **(R33)** A licitación's published **text** values are stored trimmed of surrounding
    whitespace and otherwise byte-for-byte as published, while its amounts are stored as
    numbers and its dates interpreted; a value that cannot be interpreted is absent and the
    licitación is stored regardless. Its state is the one the source published, never one
    derived from whether it holds an award.
45. **(R34)** Every read of a licitación, of a bidder list, and of an operador's participation
    history requires authentication; an unauthenticated visitor is denied all three.
46. **(R18, R33)** An award on a **formalised** procedure is linked to the operador the
    formalisation's published identifier identifies; one whose awardee appears in the procedure's
    **bidder list** is linked to the operador that bidder's published identifier identifies.
    Neither is a derived link. An award with **neither** is linked only where the published name
    matches **exactly one** operador in the catalogue; where it matches none or several, the
    licitación is stored, stays visible, and **shows an award naming nobody**. No such match ever
    creates an operador, and a link made this way is recorded as **derived** and is
    distinguishable from one the source published.
