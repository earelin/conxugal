---
feat: FEAT-002
adrs: [0004]
status: todo
depends_on: [ISSUE-003]
---

# Server-rendered login + forbidden pages and login form

Governed by ADR-0004 (session-based authentication). The login UI is server-rendered, outside the SPA.

## Scope
- `GET /login` server-rendered page with an email + password form, reachable anonymously.
- Generic failure message on `/login?error` that does not reveal which field was wrong.
- `forbidden` page for logged-in users hitting a higher-role route.
- login-success loads the SPA at `/`; an already-authenticated visitor of `/login` is sent to `/`.

## Acceptance criteria
- Unauthenticated navigation to a protected page redirects to `/login` (SPEC-001 #1).
- The SPA bundle loads only after a session exists.
- A failed login shows one generic error (SPEC-001 #3).
