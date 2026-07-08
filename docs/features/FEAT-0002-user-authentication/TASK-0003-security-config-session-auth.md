---
feat: FEAT-0002
adrs: [0002, 0005]
status: done
depends_on: [TASK-0001, TASK-0002]
---

# Security config: session auth, AuthenticationProvider and @Secured rules

Governed by [ADR-0005](../../architecture/0005-session-based-authentication.md) (session-based authentication). Wire Micronaut Security session mode onto the domain.

## Scope
- `micronaut.security.authentication: session` + session cookie + redirect config.
- Session **idle timeout of 30 minutes** so an inactive session expires and requires re-login.
- `AuthenticationProvider` adapting an HTTP login request onto the authenticate use case; map domain `Role` to the framework role.
- `@Secured` rules: SPA/data/analysis = `IS_AUTHENTICATED`, admin routes = `ADMIN`, `/login` = `IS_ANONYMOUS`.
- CSRF protection for the form-login flow.

## Acceptance criteria
- Valid email + password establishes a session cookie ([SPEC-0002](../../specs/SPEC-0002-user-authentication.md) #2).
- Authenticated USER reaches data/analysis; USER hitting an admin route is forbidden 403 ([SPEC-0002](../../specs/SPEC-0002-user-authentication.md) #4, #5).
- ADMIN reaches both ([SPEC-0002](../../specs/SPEC-0002-user-authentication.md) #6).
- The session is configured to expire after 30 minutes of inactivity, after which a
  protected request is treated as unauthenticated ([SPEC-0002](../../specs/SPEC-0002-user-authentication.md) #8).
