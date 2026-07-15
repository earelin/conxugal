---
feat: FEAT-0002
adrs: [0002, 0005, 0006]
status: done
depends_on: [TASK-0003, TASK-0004]
---

# Logout and SPA 401-handling redirect to login

Governed by [ADR-0005](../../architecture/0005-session-based-authentication.md) (session-based authentication).

## Scope
- `POST /logout` invalidates the session and redirects to `/login`.
- Protected API/XHR routes return 401 (not an HTML redirect) when the session is absent/expired.
- SPA treats a 401 as session-gone and navigates the browser to `/login`.

## Acceptance criteria
- After logout, any protected route is treated as unauthenticated ([SPEC-0002](../../specs/SPEC-0002-user-authentication.md) #7).
- An expired session mid-use sends the user back to `/login` rather than failing silently.
