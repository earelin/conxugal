---
feat: FEAT-0002
adrs: [0002, 0005]
status: done
depends_on: [TASK-0003]
---

# Server-rendered login + forbidden pages and login form

Governed by [ADR-0005](../../architecture/0005-session-based-authentication.md) (session-based authentication). The login UI is server-rendered, outside the SPA.

## Scope
- `GET /login` server-rendered page with an email + password form, reachable anonymously.
- Generic failure message on `/login?error` that does not reveal which field was wrong.
- `forbidden` page for logged-in users hitting a higher-role route.
- login-success loads the SPA at `/`; an already-authenticated visitor of `/login` is sent to `/`.

## Acceptance criteria
- Unauthenticated navigation to a protected page redirects to `/login` ([SPEC-0002](../../specs/SPEC-0002-user-authentication.md) #1).
- The SPA bundle loads only after a session exists.
- A failed login shows one generic error ([SPEC-0002](../../specs/SPEC-0002-user-authentication.md) #3).
