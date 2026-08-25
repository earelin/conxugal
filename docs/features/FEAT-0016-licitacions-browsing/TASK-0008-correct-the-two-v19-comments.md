---
feat: FEAT-0016
domain: backend
adrs: [0008, 0023]
status: todo
depends_on: [TASK-0002, TASK-0003]
---

# Correct what V19 says about `awardee_name`, without editing V19

Two comments FEAT-0015 shipped about its own columns are made false by the reads
[TASK-0002](TASK-0002-visible-browse-schema-and-indexes.md) and
[TASK-0003](TASK-0003-paged-ordered-counted-reads.md) build. **Neither may be corrected in place.**

> ❗ **`V19` is an applied migration and its comments are inside its checksum.** `V17`'s own header
> refuses exactly this edit, in shipped SQL: *"That comment is left as written: its checksum is
> recorded in every `flyway_schema_history` that has run it, and **editing an applied migration to
> correct a count fails validation on every such database**."* FEAT-0014's TASK-0001 re-argues it and
> adds the reason nothing catches the mistake — *a Testcontainer always migrates from empty and so
> records the checksum it then validates* — so CI stays green while every developer's database fails
> at boot. `validateOnMigrate` is not disabled anywhere, and `server/docker-compose.yml` keeps a named
> volume, so those databases are real.
>
> An earlier draft of this task proposed editing V19 and cited FEAT-0015's TASK-0019 as precedent.
> That precedent does not reach the case: TASK-0019 corrected a **README**, which carries no checksum.

So the two corrections land in two different places, and **correction 1 is not this task's**:

| Correction | Lands in | Owned by |
| --- | --- | --- |
| 1. `publication_id` is ordered now | the **new migration's header** — the one whose index is the fact that falsifies *never ordered* | [TASK-0002](TASK-0002-visible-browse-schema-and-indexes.md) |
| 2. `awardee_name` is not what a reader is shown | **`Award.java`'s javadoc**, a Java file with no checksum | this task |

That is V17's own shape: a later migration records what an earlier comment can no longer say.

## Scope

- **`Award.java`'s javadoc**, which today says nothing about `awardeeName` while already stating the
  surrounding rules, gains what V19's column comment gets wrong.

  V19 says: *"The name the resolution published, kept beside the link rather than instead of it: **an
  award no route resolves still names somebody, and that is what a reader is shown.**"*

  [SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) says the opposite in three places:

  - **R25** — where the awardee could not be resolved, "the licitación shows an award and **names
    nobody**";
  - **#20** — the same, as a criterion;
  - **#24** — unqualified: "This family holds **no per-row name at all**, for any party — including a
    consortium the source does not identify, whose published name is held on the operador it is
    catalogued as."

  And **R21** reduces the cases to two — "catalogued and reachable, or not shown at all" — while
  **R20** adds that "nothing here is a route that dead-ends: a party R16 could not resolve is simply
  **not counted** among the awardees the row states."

  **The column stays and its first clause is right.** FEAT-0015's path C re-resolves an awardee by
  matching the published name on every restatement, which is what closes the historical tail when an
  old *adxudicado* procedure finally formalises — so the name genuinely is kept beside the link rather
  than instead of it. What is wrong is only the claim about **display**: it is a **resolution input,
  never a rendering value, and no read selects it**.

  The correction matters because the two readings differ on 36% of award rows and on almost all of the
  pre-2013 tail an initial import spends its time on. Rendering the column would put a name on a row
  with no operador behind it — the row R20 forbids — and would reintroduce the per-row name
  SPEC-0008's first amendment removed by making an unidentified consortium an operador so its name
  could live where every other party's does.

  The javadoc also **points at V19's comment and says it is superseded**, so a reader who finds the
  SQL first is sent to the correction rather than left with it.
- **A third correction, on the same footing.** V19 says of `cpv.description` that nothing populates it
  yet; [TASK-0004](TASK-0004-year-cpv-and-state-facets.md)'s facets now read it. `Cpv.java`'s javadoc
  gains the same treatment — the description is **read and may be absent**, so a facet falls back to
  the code.

**Out of scope:** any edit to V19 or to any other applied migration; any schema change; any change to
what is stored; and correction 1, which is TASK-0002's.

**If the V19 edit is wanted anyway** — commit `6a6b129` did it for V9 and paid openly, recording that
"a database that already applied V9 needs a `flyway repair`" — then it is a decision to take
explicitly, with that cost named in the commit. This task does not take it, because nothing here needs
the expensive route.

## Acceptance criteria

- `Award.java`'s javadoc states that `awardeeName` is a **resolution input** which **no read selects**,
  and names V19's column comment as superseded on the display claim. (SPEC-0008 #24)
- `Cpv.java`'s javadoc states that the description is now read by the facets and may be absent.
- **No applied migration is edited.** Asserted by the diff: `git diff` touches no file under
  `db/migration/` that a previous release shipped. A Flyway `validate` against a database migrated
  before this change succeeds. (This is the criterion the earlier draft of the task would have failed.)
- **The display rule is enforced by a test, not only documented**: an assertion over the statements
  [TASK-0003](TASK-0003-paged-ordered-counted-reads.md) builds proves `awardee_name` appears in none
  of them, so a later author cannot quietly add it back. It lives with the statements rather than
  here, and this task's obligation is that it **exists and names this correction**. (SPEC-0008 #24)
