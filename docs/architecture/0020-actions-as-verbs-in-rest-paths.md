---
status: accepted
date: 2026-08-03
spec: SPEC-0005
supersedes: 0016
superseded_by: null
---

# 0020. A REST path's last segment may be a verb when it names an action

## Status
Accepted. Supersedes [ADR-0016](0016-rest-resource-naming.md), whose plural/singular rule is
carried forward unchanged; this record adds one exception to it.

## Context
[ADR-0016](0016-rest-resource-naming.md) settled how resources are named: **plural when the path
addresses the collection, singular when it addresses one element**, with the noun taken from the
domain. It is written as a rule about *what a path addresses*, and it says nothing about requests
that ask for something to **happen**.

[FEAT-0009](../features/FEAT-0009-contratos-menores-initial-import/README.md) needs two such
requests — trigger the initial import of every marked Órgano, and trigger one Órgano's. Neither
reads or writes a piece of state that a noun could name. The catalogue import shipped before this
question was asked and already answers at `POST /api/admin/organos/import`
(`docs/api/openapi.yaml`), so the codebase has one instance of the pattern and no record
sanctioning it.

Forcing a noun produces one of two bad outcomes. Inventing a resource that exists only to be
posted to — `.../import-request`, `.../import-job` — puts a thing in the URL that the domain does
not have and that nothing ever reads. Overloading an existing noun — `PUT .../organos/state` —
hides an action behind a write and makes the path lie about what the request does.

The pressure is narrow and worth bounding rather than generalising. Most requests really do
address state, including some whose effects are large: setting an Órgano's `importable` property
starts an import, and it is still a property being written.

## Decision
Every rule of ADR-0016 stands. **The last segment of a path may be a verb when it names an action
rather than a thing** — and only the last segment, so everything before it is still a resource
path obeying the plural/singular rule:

- `POST /api/admin/organos/import` (already shipped)
- `POST /api/admin/organo/{id}/contratos-menores/import`

**The test is whether a noun would be a lie.** `enabled` is a flag the user has, so
`POST /api/admin/user/{id}/enabled` stays a noun. `importable` is a property the Órgano has, so
`PUT /api/admin/organo/{id}/importable` stays one too — and the fact that setting it also starts
an import does not make the path an action, because what is being written is the property. An
import **run** has no such state to address: nothing on the Órgano is being set, so `import` it
is.

**Verbs are the exception and stay rare.** A path full of them is RPC with HTTP verbs painted on,
which is what ADR-0016 exists to avoid. When a request both changes state and causes an effect,
the state is what the path names.

## Consequences

### Pros
- The one shipped non-conforming endpoint becomes conforming, so the codebase and its records
  agree without an endpoint having to be renamed.
- Triggers stop competing for a noun, which removes the incentive to invent resources that exist
  only to be posted to.
- The boundary is stated as a test rather than a list, so a new case is answerable without
  amending this record.

### Cons
- **It is a judgement call, not a mechanical rule.** *Is a noun a lie here?* is answerable in
  review but not by a linter, so the boundary between an action and a state will be argued
  occasionally — and a contributor who reaches for a verb first will find ADR-0016's rules pulling
  the other way.
- The exception is stated as *what the operation does*, which is the question ADR-0016 tells a
  reader not to ask. The two records have to be read together, which is why this one supersedes
  rather than sits beside.
- Nothing in Micronaut enforces it; like ADR-0016 and ADR-0006 it rests on review discipline.
