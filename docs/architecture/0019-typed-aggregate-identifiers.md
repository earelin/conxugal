---
status: accepted
date: 2026-08-02
spec: SPEC-0005
supersedes: null
superseded_by: null
---

# 0019. Aggregate identifiers are typed wrappers around UUID

## Status
Accepted

## Context
Every aggregate in the system is identified by a `UUID`
([ADR-0008](0008-domain-entities-carry-persistence-mapping-annotations.md) maps each one 1:1 to
its table), and every one of those identifiers has the same Java type. Nothing stops a `UUID`
naming a `Termo` being passed where one naming an `OrganoDeContratacion` is expected: the
compiler sees two `UUID`s, the database sees two valid keys, and the failure surfaces as a
missing row or — worse, where both rows exist — as a write against the wrong entity.

The pressure becomes concrete in
[FEAT-0009](../features/FEAT-0009-contratos-menores-initial-import/README.md). It adds two new
aggregates whose identifiers travel together through the same methods: a `ContratoMenor` carries
the UUID of the Órgano that awarded it, an import run carries its own id *and* the ids of every
Órgano it covers, and the walk that stores contracts threads all three through use cases,
repositories and a run record at once. Those are the conditions under which a same-typed
identifier mix-up stops being hypothetical.

Three properties of what exists constrain the answer. Ids are **database-assigned** —
`@Id @GeneratedValue @Nullable UUID id`, null until insert — a convention every shipped
aggregate follows and every repository test relies on. The REST contract is **authored first**
([ADR-0010](0010-design-first-openapi-contract.md)), and an id on the wire is a plain UUID
string that no internal type change may disturb. And Micronaut Data reaches non-native types
through an `AttributeConverter` bean, which the entity names on the mapped property — a
mechanism the codebase does not use yet.

## Decision
An aggregate's identifier is a **record wrapping a `UUID`**, one type per aggregate, declared
in the domain beside the aggregate it identifies — `ContratoMenorId`, `ImportRunId`, and so on
for every aggregate that follows. The wrapper carries no behaviour beyond holding and rejecting
a null value; it exists to make the type system refuse a mismatch.

**The database keeps assigning the value.** The identifier property stays
`@Id @GeneratedValue @Nullable`, an aggregate is still constructed with a null id and receives
one on insert, and an `AttributeConverter` maps the wrapper to the `uuid` column in both
directions. Nothing about the schema changes: the column type, the keys and the foreign keys
are exactly what they were.

**Typed identifiers stop at the REST boundary.** Request and response records in the
application layer carry a plain `UUID`, and a controller wraps or unwraps at the edge. No
identifier wrapper is serialised, so `openapi.yaml` is unaffected and no client can tell the
difference. This also keeps the decision out of reach of what Micronaut Serde does or does not
do with single-value records.

**Adoption is by aggregate, not all at once.** A **new** aggregate takes a typed identifier from
birth. A **shipped** one — `User`, `OrganoDeContratacion`, `Termo` — converts when a feature
next has reason to touch its identity, and until then keeps its raw `UUID`. FEAT-0009 therefore
types its own two aggregates and **refers to an Órgano by a raw `UUID`**, because converting the
catalogue is not that feature's work.

## Consequences

### Pros
- A method taking `ContratoMenorId` cannot be handed an Órgano's identifier, and the mistake is
  a compile error rather than a missing row — the whole point, and it binds hardest exactly
  where FEAT-0009 threads three identifiers through one walk.
- Signatures document themselves: `findById(ImportRunId)` says what it wants without a parameter
  name or a comment doing the work.
- Nothing on the wire or in the schema changes, so no contract, migration or client is touched
  by adopting it.
- The convention that ids are database-assigned and null until insert survives intact, so no
  shipped aggregate, repository or test has to change to accommodate the pattern.

### Cons
- **It rests on Micronaut Data populating a converted generated id**, which is not demonstrated
  in its documentation. If `@GeneratedValue` turns out not to return the generated key through
  an `AttributeConverter`, the fallback is application-assigned identifiers — a different
  decision, needing a record that supersedes this one. The first task adopting the pattern
  proves it before the rest is built on it.
- **A mixed idiom exists on purpose, and will for a while.** New aggregates are typed, shipped
  ones are not, so a reader meets both — and a `ContratoMenor` naming its Órgano with a bare
  `UUID` is precisely the confusion this record exists to prevent, accepted here because typing
  the catalogue is another feature's work.
- Every aggregate now needs a wrapper and a converter — two small types instead of none, and a
  new one each time an aggregate is added.
- Unwrapping at the REST boundary is manual, so a controller that forgets is a compile error in
  the good case and an ugly `{"value": "..."}` in the bad one, if a wrapper ever reaches a
  response record by accident.
- `equals` on the wrapper is the record's, so identity comparison stays value-based — but a
  `UUID` and its wrapper are never equal, which will surprise anyone comparing across the
  boundary in a test.
