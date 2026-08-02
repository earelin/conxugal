---
feat: FEAT-0010
domain: backend
adrs: [0002, 0018]
status: todo
depends_on: [TASK-0001, TASK-0003]
---

# Derivation during the contratos menores import

The step that turns an awardee into an operador: resolve every stored contract to one inside the
import's own batch transaction. This is where
[ADR-0018](../../architecture/0018-operadores-as-a-stored-projection.md)'s stored projection is
actually maintained, and it is the only thing that ever fills in a contract's `operadorEconomico`
association — which, under the normalised schema, is the only thing that makes a contract's
awardee knowable at all.

**Prerequisite outside this feature:**
[FEAT-0009 TASK-0009](../FEAT-0009-contratos-menores-initial-import/TASK-0009-single-organo-initial-import.md)
builds the walk and the batch this hangs inside. Unlike this feature's first three tasks, which
land **before** the contratos menores store so the foreign key is created rather than added, this
one lands **after** there is an import to derive from.

## Scope
- Inside the batch's transaction, for each contract being upserted: reduce **the fiscal
  identifier on the source row** to a match key, find or create the operador, advance the
  operador's display fields and rank if this contract outranks the incumbent, and write the
  contract with its `operadorEconomico` association pointing there. **Contract and link commit
  together**, so a crash cannot leave a stored contract whose operador was never created.
- **The published awardee comes from the source row, not from the stored contract.** The schema
  is normalised: `contrato_menor` keeps no awardee name or identifier, so the values this task
  matches on and copies into the operador's display fields are only in hand **while the batch is
  being imported**. A derivation that tried to run over already-stored contracts would find
  nothing to derive from — which is why this step lives inside the import and cannot be a
  backfill.
- **An unusable identifier yields no operador** (R5): the contract is stored with a **null**
  `operadorEconomico`. Never a placeholder, and never a shared *unknown* row that would pool
  unrelated awards under one identity — which under the normalised schema means such a contract
  records **no awardee at all**, the cost the feature README states.
- **Resolution happens on every upsert, not only on insert.** That is what makes a correction
  changing a contract's published identifier repoint its foreign key, creating the operador the
  corrected identifier names if no contract named it before.
- **Idempotent on both tables**: the contract upserts by source identifier, the operador by
  match key, and the rank comparison is a strict win — so replaying a batch after a crash
  produces no duplicate operador and no display flapping.
- Two contracts of the same batch naming a new operador: the first creates it, the second finds
  it. There is one importer system-wide (SPEC-0005 R22), so the create-if-absent path rests on
  that guarantee rather than on the store; the unique match key is what catches it being wrong.
- **What this task does not do:** demote a display name when the winning contract is later
  withdrawn or corrected away, and make an operador with no visible contracts unreachable. Both
  are R7's lifecycle, and both wait on SPEC-0005 R13's withdrawal, which no feature builds — so
  today nothing is invisible and there is nothing to subtract.

## Acceptance criteria
- Importing two contracts whose identifiers differ only in padding or case yields **one**
  operador, referenced by both contracts, displayed under the spelling its highest-ranked
  contract published — the same spelling on both rows, since neither contract keeps one of its
  own. ([SPEC-0006](../../specs/SPEC-0006-operadores-economicos.md) #3, #5)
- Importing two contracts whose identifiers differ in internal spacing, punctuation or a
  character yields **two** operadores. (SPEC-0006 #4)
- Two contracts under the same identifier with different published names yield one operador
  displayed under the name from the **more recently published**; the older name creates nothing
  and displaces nothing. (SPEC-0006 #6 storage half, #7)
- An undated contract never displaces a dated one however late it arrives, and an operador all of
  whose contracts are undated still has exactly one display spelling, chosen by the higher
  source identifier. (SPEC-0006 #7)
- A contract published with an absent or whitespace-only identifier is stored with a **null**
  `operadorEconomico` association and creates no operador row at all — and therefore records no
  awardee, which is the branch the source is not expected to take. (SPEC-0006 #8, no-operador
  half)
- A contract with an irregular but non-empty identifier is attached to an operador rather than
  skipped. (SPEC-0006 #9)
- Re-importing the same contracts changes nothing: no operador added, no display field moved.
  (SPEC-0006 #2)
- Re-importing a contract whose published identifier changed repoints its `operadorEconomico`
  association to the operador the corrected identifier names, creating it if new. The previous
  operador is left stored with one fewer contract — making it unreachable is R7's, and is not
  asserted here. (SPEC-0006 #14, moves-and-creates half)
- A crash simulated mid-batch leaves no stored contract whose operador is missing: either both
  are there or neither is.
- Unit-tested with the ports stubbed (Mockito) for the rules and the branches; the
  commit-together and idempotency cases are integration-tested against PostgreSQL.
