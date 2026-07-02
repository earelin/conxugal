---
feat: FEAT-002
adrs: [0005]
status: todo
depends_on: [TASK-001, TASK-002]
---

# Security config: session auth, AuthenticationProvider and @Secured rules

Governed by [ADR-0005](../../architecture/0005-session-based-authentication.md) (session-based authentication). Wire Micronaut Security session mode onto the domain.

## Scope
- `micronaut.security.authentication: session` + session cookie + redirect config.
- `AuthenticationProvider` adapting an HTTP login request onto the authenticate use case; map domain `Role` to the framework role.
- `@Secured` rules: SPA/data/analysis = `IS_AUTHENTICATED`, admin routes = `ADMIN`, `/login` = `IS_ANONYMOUS`.
- CSRF protection for the form-login flow.

## Acceptance criteria
- Valid email + password establishes a session cookie ([SPEC-002](../../specs/SPEC-002-user-authentication.md) #2).
- Authenticated USER reaches data/analysis; USER hitting an admin route is forbidden 403 ([SPEC-002](../../specs/SPEC-002-user-authentication.md) #4, #5).
- ADMIN reaches both ([SPEC-002](../../specs/SPEC-002-user-authentication.md) #6).
