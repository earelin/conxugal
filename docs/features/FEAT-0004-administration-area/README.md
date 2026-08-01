---
spec: SPEC-0003
adrs: [0002, 0003, 0004, 0006, 0010, 0018]
status: draft
---

# FEAT-0004. Administration area

## Goal
Build the `ADMIN`-only administration area described by
**[SPEC-0003](../../specs/SPEC-0003-administration-area.md)**: a system dashboard
reporting server status and user administration (list, create, disable/enable — never
delete). It lives inside the server and the React Router
SPA (**[ADR-0003](../../architecture/0003-react-router-ui-served-by-backend.md)**,
**[ADR-0004](../../architecture/0004-ui-stack-vite-mantine.md)**), with the admin REST
endpoints under the reserved `/api/` prefix
(**[ADR-0006](../../architecture/0006-reserved-api-url-prefix.md)**). Access is gated by
the `@Secured(ADMIN)` rules already delivered in
[FEAT-0002](../FEAT-0002-user-authentication/README.md).

## Scope
- **Domain (auth):** extend `User` with an `enabled` state and a creation timestamp; add the account-management
  use cases (list, create, disable, enable) and extend the `UserRepository` port; add a
  `UserFactory` that builds new `User` instances (assigning a UUID identity and stamping
  `createdAt`) and a `PasswordGenerator` domain service that produces a random initial
  password for new accounts; make the existing `Authenticate` use case reject disabled
  accounts.
- **Domain (system status):** a `SystemStatus` model and a port that reports overall
  service state and datastore reachability.
- **Infrastructure:** a migration adding the `enabled` and `created_at` columns; extend
  `JdbcUserRepository`; a driven adapter that assembles system status (datastore probe +
  runtime info) without exposing secrets.
- **Application (driving):** `ADMIN`-only REST endpoints for user administration and for
  system status under `/api/admin/`; a `GET /api/me` endpoint, available to any
  authenticated user (not `ADMIN`-gated), returning the caller's own identity so the
  SPA can gate its admin nav ([SPEC-0002](../../specs/SPEC-0002-user-authentication.md) R14).
- **UI:** an admin section in the SPA — dashboard page, user-list page (surfacing each
  account's last successful login alongside its created date), create-user form, and a
  disable/enable action — with Galician chrome, shown only to administrators.

**Out of scope (future specs/features):** editing an existing account's email or role,
password reset / self-service credential change, self-registration, audit logging of
admin actions, any hard-delete of accounts (explicitly excluded by SPEC-0003 R11), and
detailed live runtime metrics (owned by
[FEAT-0005](../FEAT-0005-admin-realtime-metrics/README.md), streamed over SSE).

## Design

### Hexagonal placement ([ADR-0002](../../architecture/0002-hexagonal-architecture.md))
```mermaid
flowchart LR
    subgraph application["application (driving)"]
        usersApi["/api/admin/users endpoints"]
        statusApi["/api/admin/system-status endpoint"]
    end
    subgraph domain["domain"]
        lifecycle["ListUsers / CreateUser / SetUserEnabled"]
        factory["UserFactory (UUID + createdAt)"]
        pwgen["PasswordGenerator (service)"]
        authuc["Authenticate (rejects disabled)"]
        status["SystemStatus + SystemStatusProbe (port)"]
        repo["UserRepository (extended port)"]
    end
    subgraph infrastructure["infrastructure (driven)"]
        jdbc["JdbcUserRepository (findAll / insert / setEnabled)"]
        probe["datastore + runtime status adapter"]
    end
    application --> domain
    infrastructure --> domain
```

### Account state
- `User` gains an `enabled` boolean and a `createdAt` timestamp. The `UserRepository`
  port grows `findAll()`, an insert for new accounts, and an operation to set an
  account's `enabled` state.
- A `UserFactory` builds every new `User`, assigning the identity (a UUID) and stamping
  `createdAt`; both the id source and the clock are injected, so construction is
  deterministic and unit-testable and the id/timestamp policy lives in one place.
  `CreateUser` obtains the new account from the factory rather than constructing it
  inline (rather than relying on a database default for either value). The migration
  backfills pre-existing rows' `created_at` with a column default (see infrastructure
  task).
- `Authenticate` denies a disabled account **after** the password check so the outcome
  stays indistinct from a wrong-password failure (SPEC-0002 R3): a disabled account is
  never a distinguishable signal.
- `CreateUser` generates a random initial password through the `PasswordGenerator`
  service, enforces email uniqueness (SPEC-0003 R8), and stores only a salted hash via
  the existing `PasswordEncoder` (SPEC-0003 R13). The generated plaintext is returned
  **once** in the creation result so the admin can relay it to the new user; it is never
  persisted, logged, or retrievable afterwards (SPEC-0003 R16).
- `PasswordGenerator` draws from a cryptographically secure RNG (`SecureRandom`) and
  applies the fixed strength policy of SPEC-0003 R15 — at least 16 characters spanning
  uppercase, lowercase, digits, and symbols — so every generated password clears the same
  bar (SPEC-0003 R14). The RNG source is injected so the policy is unit-testable.
- `SetUserEnabled` refuses to disable the last enabled `ADMIN` (SPEC-0003 R12); the
  count is checked in the same transaction as the update.

### Last login in the account list
- The `User` already carries a `lastLoginAt` — the moment of its most recent successful
  login, stamped by the authenticate use case and delivered by
  [FEAT-0002](../FEAT-0002-user-authentication/README.md)
  ([SPEC-0002](../../specs/SPEC-0002-user-authentication.md) R13). This feature only
  **reads and surfaces** it in the user list; it adds no new domain state, column, or
  write path for the value.
- The value is nullable: an account that has never logged in successfully has none. The
  list renders that absence explicitly (Galician *Nunca*) rather than an empty cell, so
  "never logged in" is distinguishable from a missing render.

### System status
- A `SystemStatusProbe` port returns overall service state plus datastore reachability,
  assembled fresh per request (SPEC-0003 R4). The adapter runs a lightweight datastore
  connectivity check and reports coarse runtime info only — **never** connection strings,
  passwords, or other secrets (SPEC-0003 R5).
- This endpoint stays a **coarse snapshot**. Detailed, live runtime metrics for debugging
  (SPEC-0003 R17–R21) are a separate concern, delivered over SSE by
  [FEAT-0005](../FEAT-0005-admin-realtime-metrics/README.md)
  ([ADR-0009](../../architecture/0009-sse-admin-realtime-metrics.md)), not by expanding
  this snapshot.

### API surface ([ADR-0006](../../architecture/0006-reserved-api-url-prefix.md))
- `GET  /api/admin/users` — list accounts (email, role, enabled, created date, last
  login date — the last-login value is null until the account's first successful login).
- `POST /api/admin/users` — create account (email, role); the server generates the
  initial password and returns it once in the creation response.
- `POST /api/admin/users/{id}/enabled` — set enabled true/false.
- `GET  /api/admin/system-status` — current system status.
- All carry `@Secured("ADMIN")`; a `USER` gets 403 (SPEC-0003 R1).
- `GET  /api/me` — the caller's own account (id, email, role, created date, last
  login). Carries `@Secured(IS_AUTHENTICATED)`, not `ADMIN`: any authenticated
  `USER` or `ADMIN` gets 200 (SPEC-0002 R14).
- The full request/response contract for these endpoints is defined in the
  [OpenAPI document](../../api/openapi.yaml).

### UI ([ADR-0004](../../architecture/0004-ui-stack-vite-mantine.md))
- A new admin section (routes + nav entry) shown only when the session role is `ADMIN`;
  the server rules remain the real gate. Pages: **Dashboard** (status), **Users** (list showing
  email, role, state, created date, and last login date + create form + disable/enable). The create form
  asks only for email and role (no password
  field); on success it shows the server-generated password once, with a copy affordance
  and a warning that it will not be shown again. Chrome and messages in Galician
  (consistent with SPEC-0001 R6).

## Sequencing (tasks, one small change each)
1. **Account-lifecycle domain** — add `enabled` and `createdAt` to `User`; add `ListUsers`,
   `CreateUser`, `SetUserEnabled` use cases and extend `UserRepository`; add the
   `UserFactory` (assigns UUID + `createdAt` from injected id source and clock) and the
   `PasswordGenerator` service, and have `CreateUser` build the account via the factory
   and generate and return the initial password once; make `Authenticate` reject disabled
   accounts. *(SPEC-0003 #6–#9, #11, #12; SPEC-0002 #3)*
2. **User-store infrastructure** — migration adding the `enabled` column (default true) and
   the `created_at` column (default current timestamp, to backfill existing rows); extend
   `JdbcUserRepository` with `findAll`, insert, and set-enabled. *(SPEC-0003 #5–#9)*
3. **User-administration REST endpoints** — `@Secured(ADMIN)` endpoints for list, create,
   and enable/disable under `/api/admin/users`; the create response carries the generated
   password once. *(SPEC-0003 #1, #5–#8, #11, #12)*
4. **System-status probe + endpoint** — `SystemStatus` model, `SystemStatusProbe` port,
   datastore/runtime adapter, and `GET /api/admin/system-status`. *(SPEC-0003 #1–#4)*
5. **Current-user endpoint** ([TASK-0007](TASK-0007-current-user-endpoint.md)) —
   `FindCurrentUser` use case and `GET /api/me`, available to any authenticated user
   (not `ADMIN`-gated); needed by the admin UI shell to gate the nav client-side.
   *(SPEC-0002 #11)*
6. **Admin UI shell + dashboard** — admin section, admin-only nav gating (read from
   `GET /api/me`), and the dashboard page consuming system status. *(SPEC-0003 #1, #2)*
7. **User-administration UI** — user list (email, role, state, created date, last login
   date), create-user form (email + role only), the one-time generated-password reveal
   after creation, and the disable/enable action. *(SPEC-0003 #5–#7, #9, #10; SPEC-0002 #10)*
8. **Frontend acceptance tests** ([TASK-0008](TASK-0008-frontend-acceptance-tests.md)) —
   black-box Playwright coverage of the admin screens, driving the built SPA with the API
   replaced by WireMock ([ADR-0018](../../architecture/0018-frontend-acceptance-tests-against-a-stubbed-api.md));
   the same stub also gives `npm run dev` a local API. *(SPEC-0003 #1, #2, #5, #6, #10, #12)*

## Edge cases
- **Disabled account is indistinct at login** — a disabled account produces the same
  generic failure as a wrong password (SPEC-0002 #3), not a distinct "account disabled"
  message.
- **Duplicate email on create** — rejected with the existing account left unchanged
  (SPEC-0003 #7); the check and insert are atomic to avoid a race creating two accounts.
- **Last-admin lockout** — disabling the only enabled `ADMIN` is refused (SPEC-0003 #11);
  covers an admin trying to disable their own account when no other admin is enabled.
- **No secret leakage in status** — the status payload is asserted to contain no
  credential/connection-secret values (SPEC-0003 #4).
- **Generated password shown once** — the plaintext is returned only in the create
  response and never stored or exposed again (not in the list, logs, or any later
  read). If the admin loses it before relaying it, there is no recovery path in this
  feature — credential reset is out of scope, so the account must be recreated.
- **UI is not the gate** — hiding the admin nav from a `USER` is cosmetic; a `USER`
  hitting an admin route or `/api/admin/*` directly is still denied by the server
  (SPEC-0003 #1).
