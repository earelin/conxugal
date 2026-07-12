---
feat: FEAT-0002
adrs: [0002, 0005, 0008]
status: todo
depends_on: [TASK-0001, TASK-0002]
---

# Record the most recent successful login

Governed by [ADR-0005](../../architecture/0005-session-based-authentication.md)
(session-based authentication). Extends the authenticate use case
([TASK-0001](TASK-0001-auth-domain-user-role-authenticate.md)) and the PostgreSQL user
store ([TASK-0002](TASK-0002-auth-infrastructure-postgres-user-store.md)) so a
successful login is stamped on the user; no change to the session/CSRF/`@Secured`
wiring or the password-hashing algorithm.

## Scope
- Domain: add `lastLoginAt` to `User`; on a successful authenticate, stamp it with the
  current instant read from an injectable clock and persist via the `UserRepository`
  port. A failed authenticate writes nothing.
- Infrastructure: a nullable `last_login_at` column mapped directly on the persisted
  `User` per [ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md)
  (no shadow entity); the `UserRepository` adapter persists the updated value.
- Null until the first successful login.

## Acceptance criteria
- A successful login sets the user's `lastLoginAt` to the login instant; a later
  successful login replaces it ([SPEC-0002](../../specs/SPEC-0002-user-authentication.md) #10).
- A rejected login (unknown email or wrong password) leaves `lastLoginAt` unchanged, and
  the failure stays indistinct — the write happens only on the success path
  ([SPEC-0002](../../specs/SPEC-0002-user-authentication.md) #3, #10).
- A user with no successful login yet has no recorded value (null).
- Persisted through the real `UserRepository` (integration-tested against PostgreSQL);
  the stamping decision is unit-tested with a fixed clock and no database.
