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
- **A warning fails the build too.** Schemathesis exits zero on a run that only warns, and its
  warnings are not advisory: each says the generated requests stopped short of an operation's
  real logic, which is the difference between an operation that conformed and one that was
  never properly reached. `scripts/contract-test.sh` fails on any that remain, so the fixtures
  have to be good enough that none does. The one warning left off is *validation mismatch*,
  which reports the share of generated bodies an operation refused: behind real validation and
  a uniqueness constraint that share is high by construction and moves with whatever rows
  earlier phases created, naming different operations on consecutive runs of one suite. Its
  genuine form — an API refusing a body the contract calls valid — is the
  `positive_data_acceptance` check's to raise, and that stays on.
- **The instance under test has no real downstream.** `server/docker-compose.yml` runs a
  WireMock standing in for contratosdegalicia.gal, serving the front page whose static HTML
  embeds the Órganos list in the ISO-8859-1 the source really uses, and the application is
  pointed at it. This is [ADR-0007](0007-acceptance-testing-module.md)'s rule — external
  dependencies replaced by mocks — applied to the same instance both suites drive. The import
  is therefore an operation like any other here: it is generated against, it reconciles a real
  catalogue into a real database, and the site is never called.
- **One operation is excluded**, for a reason inherent to it rather than to the tool:
  `GET /api/admin/metrics` is an unbounded SSE stream, not a JSON body, and the contract
  records the same limitation on the operation itself.
- **An operation the run never reaches fails the build.** How much of the contract is covered
  is configuration — a `schemathesis.toml` filter, a disabled phase, an operation the contract
  grew after either was last read — and configuration that quietly narrows the run makes the
  suite prove less while it stays green. So the run writes a JUnit report naming every
  operation it reached, and `scripts/contract-test.sh` holds the contract's own list of
  operations against it: anything declared but neither reached nor named in the script's
  `EXEMPT_OPERATIONS` fails, as does an exemption for an operation the contract no longer
  declares. The excluded SSE stream above is the one entry, and it carries its reason.
- The **stateful phase is disabled**. The contract declares no OpenAPI links, so that phase
  can only guess how operations chain, and the one relationship it reliably discovers is that
  `GET /api/admin/users` hands it the id of the administrator the run authenticates as, which
  it then feeds to `POST /api/admin/users/{id}/enabled` — locking the rest of the run out.
  Deliberate state transitions belong to ADR-0007's suite.
- `db/migration-local/R__seed_test_catalogue.sql` seeds a catalogue, a taxonomy and a
  throwaway account at **fixed identifiers**, so generated requests reach the operations'
  real logic instead of stopping at 404. It is a local-environment migration, which is the
  set both a developer's compose stack and CI run. It is **repeatable, and carries a
  `${flyway:timestamp}` placeholder so it runs on every start**: the run deletes and renames
  these rows, and restoring them at start-up is what lets a second run locally begin from the
  same place as the first, without a wipe. Being repeatable also keeps the fixtures out of the
  versioned sequence the two migration folders share.

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
  will leave rows behind and disable seeded accounts. Nothing in the script prevents that. The
  seed restores the rows it *owns* on the next start, but nothing collects the ones the run
  invents.
- The image tag is bumped by hand: `.github/dependabot.yml` covers npm, Gradle and GitHub
  Actions, and Dependabot does not read shell scripts.
- `--network host` makes the script Linux-first.
- The excluded operation and the disabled stateful phase are real gaps. The exclusion is at
  least no longer one a future contributor must remember to revisit unprompted — it is written
  down as an exemption the coverage gate checks, and a second one cannot be added by narrowing
  a filter, only by stating it. The disabled phase is not covered by that: coverage is counted
  per operation, and every operation is reached by the phases that remain.
- The stub is hand-written, so it can drift from what contratosdegalicia.gal actually serves —
  the same weakness [ADR-0018](0018-frontend-acceptance-tests-against-a-stubbed-api.md) records
  of the SPA's stubs, and one this document cannot close for a source that publishes no
  contract. What it does buy is that the shape is stated in one place, next to the adapter's
  own account of it, rather than assumed. `FEAT-0009`'s `design/source-contract.md` measures
  the real site, and is what the fixture should be checked against when it is revised.
- Keeping it green cost strictness the framework does not offer. Micronaut Serde coerces
  scalars — `{"enabled": "AAA"}` deserializes to `false` — and cannot tell an absent property
  from a null one, with no configuration for either, so every request body is now read through
  a deserializer of its own. That is real code on a layer that used to be annotations alone,
  and each new request type has to remember it.
- A request body naming another row is the one place no fixture reaches. Path parameters are
  pointed at seeded rows and a delete draws from a pool of disposable ones, but an override
  cannot reach inside a body, so the terms a move names are generated and mostly do not exist.
  The contract declares 404 for exactly that, so the answers conform; what suffers is how far
  in the generation gets. Making the ids the contract's own examples carry into real rows
  closes it for the operations whose examples name one — the rest is left, with the warning
  turned off against those operations rather than tolerated in silence.
