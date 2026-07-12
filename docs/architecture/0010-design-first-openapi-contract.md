---
status: accepted
date: 2026-07-11
spec: null
supersedes: null
superseded_by: null
---

# 0010. Design the REST API contract upfront as an OpenAPI document

## Status
Accepted

## Context
This project works spec-first: a capability is described, sliced into features, and only
then implemented. The REST endpoints those features expose — all under the reserved `/api/`
prefix ([ADR-0006](0006-reserved-api-url-prefix.md)) — are therefore known and reviewed on
paper well before any controller exists.

There are two ways to keep an authoritative description of that HTTP contract:

- **Design-first** — hand-author an OpenAPI document as the contract, and implement
  controllers to conform to it.
- **Code-first** — write the controllers first and generate the OpenAPI document from their
  annotations (for Micronaut, the `micronaut-openapi` processor).

Code-first cannot exist before the code, so it offers nothing to review, build a UI
against, or agree between the frontend and backend until the endpoints are already written
— which is at odds with designing endpoints upfront. The contract is also cross-cutting:
every current and future REST endpoint, across any spec, shares whatever convention governs
how the contract is defined and where it lives. That makes it a decision to record rather
than a per-feature choice. The administration API (FEAT-0004) and the metrics stream
(FEAT-0005) are the first consumers, described in `docs/api/openapi.yaml`.

## Decision
Design the REST API **contract-first**: the HTTP contract is a hand-authored **OpenAPI 3.1**
document at `docs/api/openapi.yaml`, and it is the **authoritative source of truth** for the
API.

- Endpoints are specified in the OpenAPI document **before** they are implemented, as part
  of the feature's design.
- Controllers are written to **conform to** the document; the document is not generated from
  the code.
- Conformance is **enforced by a contract test in CI**: the running API is validated against
  `docs/api/openapi.yaml` so any drift between the document and the implementation fails the
  build rather than relying on review alone.
- The document covers the JSON REST surface under `/api/**`. Server-rendered flows outside
  `/api/` (the login/logout form of [ADR-0005](0005-session-based-authentication.md)) are
  not REST operations and are represented only as the security scheme.
- The document is versioned in the repository and reviewed like any other design artifact;
  it may be used to generate client types, mock servers, or human-readable API docs.

## Consequences

### Pros
- A concrete, reviewable contract exists before implementation, so features, tasks, and the
  UI can be designed and agreed against it — consistent with the spec-first workflow.
- Frontend and backend can proceed in parallel against the agreed contract instead of one
  waiting on the other.
- A single transport-level source of truth enables generated clients, mock servers, and
  published documentation from one file.
- Drift between the hand-authored document and the controllers is caught automatically: a
  contract test in CI validates the running API against `docs/api/openapi.yaml` and fails the
  build on any mismatch, so conformance no longer rests on review alone.

### Cons
- The document is hand-maintained, so a legitimate contract change means updating the OpenAPI
  document, the controllers, and the contract test's expectations together.
- The response/request shapes are expressed both in the OpenAPI schemas and in the Java
  DTOs, so a contract change must be made in two places.
- We forgo code-first generation (`micronaut-openapi`). If drift becomes costly, revisit
  with a new ADR — for example, generating the document in CI and diffing it against this
  one, or switching to code-first with this document demoted to a generated output.
