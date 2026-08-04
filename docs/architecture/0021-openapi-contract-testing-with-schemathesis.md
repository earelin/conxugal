---
status: accepted
date: 2026-08-04
spec: null
supersedes: null
superseded_by: null
---

# 0021. Verify contract conformance with Schemathesis against a running instance

## Status
Accepted

## Context
[ADR-0010](0010-design-first-openapi-contract.md) makes `docs/api/openapi.yaml` the
authoritative description of the REST API and states that "conformance is **enforced by a
contract test in CI**: the running API is validated against `docs/api/openapi.yaml` so any
drift between the document and the implementation fails the build rather than relying on
review alone."

No such test existed. The only automated check was `scripts/openapi-lint.sh`, which lints the
document against a ruleset — it never sends a request to a server, so it can say the contract
is well-formed but nothing about whether the controllers honour it. Meanwhile six task files,
two feature READMEs, `docs/api/CLAUDE.md` and [ADR-0012](0012-rate-limit-http-contract.md) all
cite the CI contract test as a guarantee already in place. Conformance in fact rested on
review, and [ADR-0018](0018-frontend-acceptance-tests-against-a-stubbed-api.md) records the
matching blind spot on the other side: the SPA's WireMock stubs are hand-written, so "a
contract change that the server honours can leave these tests green against a shape the API no
longer returns".

The hand-written suites cannot close this. [ADR-0007](0007-acceptance-testing-module.md)'s
REST-assured scenarios assert what a developer thought to assert about the requests a
developer thought to send; they cover chosen journeys, not the contract's whole surface, and
they are written from the same understanding that produced the controller.

## Decision
Verify conformance with **Schemathesis**, run from a **pinned Docker image** against a running
instance, driven by `scripts/contract-test.sh` and configured by `schemathesis.toml` at the
repository root.

- It reads `docs/api/openapi.yaml`, generates requests for every operation, and asserts the
  live responses conform — status codes, content types, response schemas, declared headers,
  and the absence of 5xx.
- It runs as a step of `server-ci.yml`'s existing `acceptance` job, **after** the
  REST-assured/Playwright suite, reusing the docker-compose instance that job already builds
  and boots. It runs last because it generates data: run earlier, the rows it leaves behind
  would be there for that suite to read, and only the job's `docker compose down -v` teardown
  discards them.
- **A violation is a defect in the implementation**, per ADR-0010. The contract is amended
  only when it states something the domain does not mean.
- Two operations are excluded, for reasons inherent to them rather than to the tool:
  `GET /api/admin/metrics` is an unbounded SSE stream, not a JSON body — the contract records
  the same limitation on the operation itself — and `POST /api/admin/organos/import` reaches
  contratosdegalicia.gal over the real network on every call.
- The **stateful phase is disabled**. The contract declares no OpenAPI links, so that phase
  can only guess how operations chain, and the one relationship it reliably discovers is that
  `GET /api/admin/users` hands it the id of the administrator the run authenticates as, which
  it then feeds to `POST /api/admin/users/{id}/enabled` — locking the rest of the run out.
  Deliberate state transitions belong to ADR-0007's suite.
- `db/migration-local/V12__seed_test_catalogue.sql` seeds a catalogue, a taxonomy and a
  throwaway account at **fixed identifiers**, so generated requests reach the operations'
  real logic instead of stopping at 404. It is a local-environment migration, which is the
  set both a developer's compose stack and CI run.

Schemathesis is a Python tool and this repository has no Python. The Docker image keeps it
that way: the same pinned image runs locally and in CI, and no toolchain is added to
contributor setup beyond the Docker daemon the acceptance suite already needs.

## Consequences

### Pros
- ADR-0010's stated guarantee becomes real, and the claims in the task files and
  `docs/api/CLAUDE.md` become true.
- Coverage follows the contract rather than a developer's imagination: every operation,
  including the error branches nobody writes a scenario for. Its first runs found the taxonomía
  `DELETE` endpoints answering 403 to a valid administrator — a bug the SPA already carried —
  framework-generated problem documents missing the required `title`, an unreadable session
  cookie reported as 400 instead of 401, request bodies coerced rather than refused, a term
  name stripped by a narrower notion of whitespace than the contract describes, and a NUL in a
  name reaching PostgreSQL as a 500. None of it was visible to any existing test.
- It counterweights ADR-0018's "stubs can lie": the stubs remain hand-written, but the real
  API is now measured against the same document they are derived from.
- Failures reproduce locally with the same command CI runs, and generation is deterministic.

### Cons
- **The run mutates the target's data** — it creates accounts and taxonomy terms, and deletes
  them. It is safe only against a disposable instance; pointed at a developer's own database it
  will leave rows behind and disable seeded accounts. Nothing in the script prevents that.
- The image tag is bumped by hand: `.github/dependabot.yml` covers npm, Gradle and GitHub
  Actions, and Dependabot does not read shell scripts.
- `--network host` makes the script Linux-first.
- The excluded operations and the disabled stateful phase are real gaps, and the exclusions
  are configuration a future contributor must remember to revisit.
- Keeping it green cost strictness the framework does not offer. Micronaut Serde coerces
  scalars — `{"enabled": "AAA"}` deserializes to `false` — and cannot tell an absent property
  from a null one, with no configuration for either, so every request body is now read through
  a deserializer of its own. That is real code on a layer that used to be annotations alone,
  and each new request type has to remember it.
- Generation reaches a little further than the seeded fixtures do: the operations behind a
  `{id}` are pointed at fixed rows, but a body referring to another row still carries a
  generated UUID, so some operations mostly exercise their 404 branch. The run reports it as a
  warning rather than passing quietly.
