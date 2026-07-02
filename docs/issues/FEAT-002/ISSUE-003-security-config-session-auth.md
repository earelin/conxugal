---
feat: FEAT-002
adrs: [0004]
status: todo
depends_on: [ISSUE-001, ISSUE-002]
---

# Security config: session auth, AuthenticationProvider and @Secured rules

Governed by ADR-0004 (session-based authentication). Wire Micronaut Security session mode onto the domain.

## Scope
- `micronaut.security.authentication: session` + session cookie + redirect config.
- `AuthenticationProvider` adapting an HTTP login request onto the authenticate use case; map domain `Role` to the framework role.
- `@Secured` rules: SPA/data/analysis = `IS_AUTHENTICATED`, admin routes = `ADMIN`, `/login` = `IS_ANONYMOUS`.
- CSRF protection for the form-login flow.

## Acceptance criteria
- Valid email + password establishes a session cookie (SPEC-001 #2).
- Authenticated USER reaches data/analysis; USER hitting an admin route is forbidden 403 (SPEC-001 #4, #5).
- ADMIN reaches both (SPEC-001 #6).
