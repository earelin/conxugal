---
feat: FEAT-0015
domain: backend
adrs: [0002, 0023]
status: todo
depends_on: [TASK-0006, TASK-0010, TASK-0011, TASK-0021]
---

# Resolve the bidders

Every **single-firm** bidder a procedure publishes, resolved to an operador from its published
identifier through [TASK-0011](TASK-0011-extract-resolve-operador.md)'s collaborator, and stored on
its participation. The first licitacións caller of the shared derivation, and the proof that the
extraction works for a second family.

Operadores are the stored projection of
[ADR-0023](../../architecture/0023-operadores-as-a-stored-projection.md).

## Scope

- **Every single-firm bidder resolves** from its published identifier and is stored on its
  participation holding **no name of its own** (R18). A name belongs on the operador an identifier
  resolves to, which is why an unusable identifier leaves a party with nothing to display rather
  than a name without a link.
- **A bidder whose identifier is unusable yields no operador** and is recorded as **no participant**
  (R16). The licitación stays stored, and the procedure's other bidders are unaffected.
- **Consortium rows are routed past this entirely**, and still are under amendment 1.
  [TASK-0010](TASK-0010-record-parse-bidders-and-consortium-detection.md)'s structural
  classification has already separated them, so no placeholder identifier ever reaches SPEC-0006 R3
  through this path. A consortium **is** catalogued as an operador now — but by
  [TASK-0013](TASK-0013-consortia-and-their-membership.md), because its identifier may come from
  the **formalisation** rather than the bidder row, and this task sees only the bidder row. Routing
  it here would decide *identified* on half the evidence.
- **What rank a bid supplies, stated narrowly.** A bid **creates** an operador that no contract
  named before, catalogued under the name the bid published — R16 requires the participation to
  exist and R3 requires an identifier to resolve to something, so this much is forced. Beyond that
  it **contributes no rank**: it does not promote a name, does not enter the retained set, and does
  not displace a name a *contract* published.

  An earlier draft had a losing bid ranking names "on the plain reading" of R4. That was the
  opposite of the plain reading — R4 selects from the operador's *"most recently published
  **contract**"*, R15 retains what *"its contracts"* published, and R16 defines a participation as a
  relation to a contract the operador *"was not awarded"*. A bid is not a contract in any of the
  three, no amendment covers it, and it would open a staleness path nobody owns: TASK-0014 withdraws
  a participation on restatement, and ADR-0023 explicitly leaves *"how R4's demotion is driven when
  a change subtracts"* unsettled, so a principal name won by a now-withdrawn bid would never be
  recomputed. Widening R4 to admit bids is a spec amendment, and this feature does not take it.
- The **awarded** bidder's rank contribution is [TASK-0012](TASK-0012-resolve-the-awardee.md)'s,
  where there is a contract to rank from and a lote to rank it by.

**Out of scope:** the awardee (TASK-0012), consortia (TASK-0013), and any change to
`FiscalIdentifier` ([TASK-0019](TASK-0019-widen-fiscal-identifier-to-reject-placeholders.md)).

## Acceptance criteria

- A single-firm bidder is stored with an operador reference and **no published name of its own** on
  the participation. ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #19, #24)
- A bidder naming an operador no contract named before **creates** it, catalogued under the name the
  bid published. ([SPEC-0006](../../specs/SPEC-0006-operadores-economicos.md) #33)
- **A losing bid does not change a catalogued operador's displayed name**, and adds nothing to its
  retained set — even when the bid is more recent than every contract that named it. This is the
  rule an earlier draft inverted, so it is asserted directly. (SPEC-0006 #33, #34)
- A bidder whose published identifier is unusable yields no operador and no participation, and the
  licitación is still stored with its other bidders intact and no withdrawal marker set.
  (SPEC-0008 #20)
- A **consortium** bidder row reaches this path not at all: nothing here is called with a
  consortium's NIF cell, and no operador holding `-` or a `TEMP-…` value is created by importing a
  fixture set containing them. *This is the observable form of the guarantee — the catalogue's
  contents after the import, not a call that did not happen. It survives amendment 1 unchanged:
  TASK-0013 catalogues such a consortium holding **no** identifier, never one holding the
  placeholder.* (SPEC-0006 #8)
- Re-importing the same procedure leaves one participation per published bidder and does not flap
  any operador's name. (SPEC-0008 #17; SPEC-0006 #37)
- The rank-less path is a **second entry point on `ResolveOperador`**, never a rank engineered to
  lose. A losing rank does not express *this publication ranks nothing*: it still reaches
  `retainName`, filing the bid's name among the operador's alternatives, and no port drops a
  retained name except as a side effect of promoting it — so that shortcut breaks SPEC-0006 #33 and
  #34 permanently and silently. Forking the lookup-or-insert into this family's importer is the
  other shortcut, and it is the divergence
  [TASK-0011](TASK-0011-extract-resolve-operador.md) exists to prevent. (SPEC-0006 #33, #34)
- The use case calling `ResolveOperador` **opens the transaction** the resolution's writes join, so
  an operador cannot be created and named while the licitación write that justified it rolls back.
  The collaborator owns no boundary of its own — that is TASK-0011's design, and it makes the
  boundary each caller's to supply. (SPEC-0006 #37)
- Repository ports stubbed with **Mockito**; the catalogue-effect criteria integration-tested
  against PostgreSQL with a real operadores table.
