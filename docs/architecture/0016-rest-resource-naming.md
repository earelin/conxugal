---
status: superseded
date: 2026-07-26
spec: null
supersedes: null
superseded_by: 0020
---

# 0016. Plural resource paths for collections, singular for a single element

## Status
Superseded by [ADR-0020](0020-actions-as-verbs-in-rest-paths.md), which keeps every
rule below and adds one exception: the last path segment may be a verb when it names an action
rather than a thing.

## Context
[ADR-0006](0006-reserved-api-url-prefix.md) reserves `/api/` for every REST endpoint but
says nothing about what comes after the prefix. Each endpoint author has therefore picked
a path shape unaided, and the API has already drifted: `/api/admin/users` lists a
collection while `/api/admin/users/{id}/enabled` addresses **one** user through that same
plural noun.

The drift became a design question rather than a cosmetic one while designing
[FEAT-0007](../features/FEAT-0007-organos-taxonomia-classification/README.md). It splits
the Órganos read into two flat lists, which creates a genuine `/api/organos` collection
for the first time. With no naming rule, its sibling read was drafted as
`GET /api/organos/taxonomia` — a *different* resource sitting exactly where that
collection's member path, `/api/organos/{id}`, would have to go. A reader of the contract,
a generated client, and any future single-Órgano read would all have to special-case one
literal segment, and the id `"taxonomia"` would become permanently unusable. The path was
not wrong by inattention; there was simply no rule to be wrong about.

The rule below **resolves that path rather than forbidding it**: once members live at the
singular `/api/organo/{id}`, the plural `/api/organos` namespace holds no ids at all, so a
sub-resource of the collection cannot collide with anything. FEAT-0007 therefore keeps
`GET /api/organos/taxonomia` — the taxonomy genuinely belongs to the whole collection of
Órganos, not to one — and it is unambiguous for exactly the reason this record exists. The
collision was never about that path; it was about a namespace doing two jobs.

This binds every current and future endpoint, across every spec, and it is expensive to
revise once contracts are published and consumed — the same properties that earned
[ADR-0006](0006-reserved-api-url-prefix.md) and
[ADR-0012](0012-rate-limit-http-contract.md) their own records. So it is decided here
rather than left as prose in whichever feature happens to notice it next.

## Decision
After the `/api/` prefix (and the `/admin/` segment where present), a resource is named
**plural when the path addresses the collection, and singular when it addresses one
element**.

- **Collection paths take the plural noun** — listing (`GET /api/organos`), creating a new
  element (`POST /api/admin/organos/taxonomia/termos`), and any operation acting on the set as a
  whole (`POST /api/admin/organos/import`). A create has no id yet, so it acts on the
  collection and takes the plural.
- **Single-element paths take the singular noun and carry the identifier** —
  `GET /api/organo/{id}`, `PATCH /api/admin/organos/taxonomia/termo/{id}`,
  `DELETE /api/admin/organos/taxonomia/termo/{id}`.
- **Sub-resources of one element** hang off the singular path and follow the same rule for
  their own noun — `PUT /api/admin/organo/{id}/termo` (one term),
  `POST /api/admin/user/{id}/enabled` (one flag).
- **A resource that is not a collection** — a singleton such as `/api/me`,
  `/api/admin/system-status` — takes the singular and needs no identifier.
- **A sub-collection of the collection** hangs off the plural path and takes the plural in
  turn — `GET /api/organos/taxonomia` (the taxonomy of all Órganos),
  `POST /api/admin/organos/taxonomia/termos`. This is safe precisely because the plural
  namespace carries no identifiers.
- **Multi-word nouns are kebab-case**, as they already are (`system-status`).
- The domain noun is used **as the domain names it**: `organo` / `organos` where the
  aggregate is `OrganoDeContratacion`, `termo` / `termos` where it is
  `Termo`. The API does not translate a term the code has already chosen, so the
  language mix in the model is reproduced in the URLs rather than papered over.

One shipped endpoint predates this rule and violates it:
**`POST /api/admin/users/{id}/enabled`** should be `/api/admin/user/{id}/enabled`. It is
recorded here as the single documented deviation, not a precedent. Correcting it is
**unowned follow-up work** — no task under
[FEAT-0004](../features/FEAT-0004-administration-area/README.md) currently carries it, and
it is a breaking change to a shipped endpoint, so it needs a task of its own before it
lands. Every other existing path already conforms.

Like ADR-0006 this is an enforceable convention upheld by review: a path that addresses
one element through a plural noun is a defect. It is restated in
[`docs/api/CLAUDE.md`](../api/CLAUDE.md) — next to `openapi.yaml`, where paths are actually
chosen — for day-to-day visibility, but this ADR is the governing record.

## Consequences

### Pros
- A collection and its members occupy **disjoint namespaces**, so a sibling resource can
  never collide with a member path, and no identifier value has to be reserved against a
  literal segment. `GET /api/organos/taxonomia` becomes expressible rather than forbidden.
- Endpoint authors have one rule to apply instead of a per-endpoint judgement call, and
  reviewers have something concrete to check.
- A reader can tell from the path alone whether a response is a list or a single entity,
  before opening the contract.
- The rule is mechanical enough to check in review of `docs/api/openapi.yaml`, which
  ADR-0010 already makes the authoritative, contract-first artifact.

### Cons
- It departs from the common REST convention of a single plural noun for both
  (`/users` and `/users/{id}`), so contributors arriving from other codebases will expect
  the plural and must be pointed at this record.
- Two spellings of every resource noun exist, and a typo between them produces a 404 that
  looks like a missing endpoint rather than a misspelling.
- One shipped endpoint is knowingly non-conforming until its follow-up task lands, so the
  API is briefly inconsistent in exactly the way this ADR exists to prevent.
- Nothing in Micronaut enforces it; like ADR-0006 it rests on review discipline.
- Reversing it later would be a breaking change to every client and would need a new ADR
  superseding this one.
