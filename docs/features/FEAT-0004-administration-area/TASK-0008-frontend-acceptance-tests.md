---
feat: FEAT-0004
adrs: [0018]
status: done
depends_on: [TASK-0005, TASK-0006]
---

# Frontend acceptance tests for the administration area

Governed by [ADR-0018](../../architecture/0018-frontend-acceptance-tests-against-a-stubbed-api.md). Black-box Playwright coverage of the admin screens delivered by [TASK-0005](TASK-0005-admin-ui-shell-and-dashboard.md) and [TASK-0006](TASK-0006-user-administration-ui.md), driving the built SPA with the API replaced by WireMock — no server and no database.

## Scope
- A WireMock service in `ui/docker-compose.yml` with versioned default mappings in `ui/wiremock/mappings/`, covering `GET /api/me`, `GET /api/admin/system-status`, `GET /api/admin/users`, `POST /api/admin/users`, `POST /api/admin/users/{id}/enabled` and the `GET /api/admin/metrics` SSE stream. Payloads derived from the examples in [`docs/api/openapi.yaml`](../../api/openapi.yaml).
- Vite `server.proxy` and `preview.proxy` routing `/api`, `/login` and `/logout` to that service (`UI_API_TARGET`, default `http://localhost:8081`), so `npm run dev` and `npm run preview` render the administration area without a backend.
- A Node `@playwright/test` suite in `ui/e2e` driving `vite preview`, with a `/__admin` helper to program per-scenario stubs and reset to the on-disk defaults between tests.
- High-value happy-path journeys only: dashboard status, user list, create-user with the one-time password reveal, disable/re-enable, and admin-nav gating.
- Vitest is scoped to `src/**` so it no longer tries to collect the Playwright specs.

**Out of scope:** a CI job for the suite (`ui-ci.yml` stays lint/test/build), error and edge-path scenarios beyond the ones above, and any assertion about server-side authorization — the real gate is the server's and is covered by the backend suite.

## Acceptance criteria
- Running `docker compose up -d` and `npm run dev` from `ui/` renders the administration area end to end with no backend or database running. ([ADR-0018](../../architecture/0018-frontend-acceptance-tests-against-a-stubbed-api.md))
- Opening the dashboard as an administrator shows the overall service state, the datastore's reachability, and the note that status never includes credentials. ([SPEC-0003](../../specs/SPEC-0003-administration-area.md) #2, #4)
- The user list shows every account — enabled and disabled — with its email, role, state, created date and last login, and an account that has never logged in renders *Nunca*. (SPEC-0003 #5, #10)
- Creating an account through the form reveals the generated initial password once, sends `{email, role}` to the create endpoint, and adds the account to the list. (SPEC-0003 #6, #12)
- Disabling a listed account and re-enabling it updates its displayed state in place, and the account stays listed throughout. (SPEC-0003 #8, #9, #10)
- A non-administrator session sees no administration section in the navigation and gets the not-found page at an admin route. (SPEC-0003 #1)
- Journeys are driven only through the rendered page — accessible roles, labels and the Galician copy of `strings.ts` — never through application internals, and each scenario is reproducible in isolation.
- `npm run lint`, `npm run test` and `npm run build` stay green, with Vitest collecting only `src/**`.
