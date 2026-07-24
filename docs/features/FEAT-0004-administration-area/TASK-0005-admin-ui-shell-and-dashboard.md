---
feat: FEAT-0004
adrs: [0003, 0004, 0005]
status: done
depends_on: [TASK-0004, TASK-0007]
---

# Admin UI shell + dashboard

Governed by [ADR-0004](../../architecture/0004-ui-stack-vite-mantine.md) (Vite + Mantine SPA). Adds the admin section and the dashboard page consuming [TASK-0004](TASK-0004-system-status-probe-and-endpoint.md)'s status endpoint and [TASK-0007](TASK-0007-current-user-endpoint.md)'s `GET /api/me` for the session role used to gate the nav. Visual target: [`design/dashboard.svg`](design/dashboard.svg).

## Scope
- Admin routes and an **ADMINISTRACIÓN** nav section (Panel, Usuarios), shown only when the session role — read from `GET /api/me` (TASK-0007) — is `ADMIN`.
- Dashboard page fetching `GET /api/admin/system-status` and rendering overall service state and datastore reachability.
- Galician chrome and copy in `strings.ts`, consistent with the existing shell.

## Acceptance criteria
- The admin nav and routes are shown for an `ADMIN` and hidden for a `USER`; this hiding is cosmetic — the server rules remain the real gate (a `USER` reaching an admin route or `/api/admin/*` is still denied). ([SPEC-0003](../../specs/SPEC-0003-administration-area.md) #1)
- The dashboard displays the overall service state and the datastore's reachability from the endpoint. (SPEC-0003 #2)
- A changed dependency state is reflected on the next dashboard view (no client-side caching of a stale healthy status). (SPEC-0003 #3)
- The dashboard renders only what the endpoint returns and surfaces no secret values. (SPEC-0003 #4)
- All added chrome and messages are in Galician. (SPEC-0001 #6)
- Component-tested for the `ADMIN`-visible / `USER`-hidden nav and the dashboard render.
