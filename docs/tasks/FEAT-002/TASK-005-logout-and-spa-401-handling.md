---
feat: FEAT-002
adrs: [0005]
status: todo
depends_on: [TASK-003]
---

# Logout and SPA 401-handling redirect to login

Governed by [ADR-0005](../../architecture/0005-session-based-authentication.md) (session-based authentication).

## Scope
- `POST /logout` invalidates the session and redirects to `/login`.
- Protected API/XHR routes return 401 (not an HTML redirect) when the session is absent/expired.
- SPA treats a 401 as session-gone and navigates the browser to `/login`.

## Acceptance criteria
- After logout, any protected route is treated as unauthenticated ([SPEC-002](../../specs/SPEC-002-user-authentication.md) #7).
- An expired session mid-use sends the user back to `/login` rather than failing silently.
