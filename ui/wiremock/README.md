# WireMock mappings — the frontend's local API

Default stub state for the `/api` contract, served by the `wiremock` service in
[`../docker-compose.yml`](../docker-compose.yml) and consumed by `npm run dev`,
`npm run preview` and the acceptance suite in [`../e2e`](../e2e) alike
(**[ADR-0017](../../docs/architecture/0017-frontend-acceptance-tests-against-a-stubbed-api.md)**).

Payloads mirror the examples in
[`docs/api/openapi.yaml`](../../docs/api/openapi.yaml) — keep them in step when the
contract changes.

## The seeded accounts

The list is shaped so every state the users page renders is reachable without touching
a stub:

| Account | Role | State | Last login | Why it's here |
| --- | --- | --- | --- | --- |
| `ana.pereira@conxugal.gal` | `ADMIN` | enabled | 10 Jul 2026 | the **only** enabled admin, so its disable control is blocked |
| `brais.otero@conxugal.gal` | `USER` | enabled | 8 Jul 2026 | the account journeys disable and re-enable |
| `diego.senra@conxugal.gal` | `USER` | enabled | never | renders *Nunca* |
| `helena.mar@conxugal.gal` | `USER` | **disabled** | 2 Mar 2026 | a disabled account stays listed |

## Files

| File | Stubs |
| --- | --- |
| `me.json` | `GET /api/me` — an `ADMIN` session, so the admin nav is reachable in dev |
| `system-status.json` | `GET /api/admin/system-status` — service up, datastore reachable |
| `users.json` | `GET /api/admin/users`, `POST /api/admin/users` |
| `users-enabled.json` | `POST /api/admin/users/{id}/enabled`, one stub per account |
| `session.json` | `POST /logout`, `GET /login` — both are proxied, so both need an answer |
| `metrics.json` | `GET /api/admin/metrics` — a few canned SSE samples |

`POST` stubs echo the submitted values with response templating (the container runs with
`--global-response-templating`), so a created account shows the email you typed and a
toggled account keeps its identity.

## Known gaps

These are stub limitations, not app bugs — worth knowing before you chase one:

- **`users-enabled.json` repeats each account's email, role and dates.** The UI replaces
  a row wholesale with the toggle response, so a stub that disagreed with `users.json`
  would silently rewrite the row. **Change both files together.**
- **A freshly created account cannot be toggled.** `users.json` returns a fixed id for
  every create, and no `/enabled` stub matches it, so disabling a just-created account
  404s. A stub cannot know the email you typed, and inventing one would be worse than the
  404.
- **The metrics stream is finite.** WireMock cannot hold a real SSE connection open, so
  `metrics.json` dribbles a few samples over 30 s and then closes; `EventSource`
  reconnects and replays them. Good enough to render the panel, not a faithful stand-in
  for the live stream — which is why the acceptance suite asserts nothing about it.

## Changing state temporarily

Prefer programming a stub over editing these files when you only need it for one
scenario — that is what the acceptance suite does, via
[`../e2e/support/wiremock.ts`](../e2e/support/wiremock.ts):

```bash
curl -X POST http://localhost:8081/__admin/mappings \
  -d '{"request":{"method":"GET","urlPath":"/api/me"},
       "response":{"status":200,"jsonBody":{"id":"…","email":"…","role":"USER","createdAt":"2026-01-15T09:30:00Z","lastLoginAt":null}}}'

curl -X POST http://localhost:8081/__admin/mappings/reset   # back to these files
```
