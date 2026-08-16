# WireMock mappings — the frontend's local API

Default stub state for the `/api` contract, served by the `wiremock` service in
[`../docker-compose.yml`](../docker-compose.yml) and consumed by `npm run dev`,
`npm run preview` and the acceptance suite in [`../acceptance`](../acceptance) alike
(**[ADR-0018](../../docs/architecture/0018-frontend-acceptance-tests-against-a-stubbed-api.md)**).

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
| `organos.json` | `GET /api/organos` (the **visible set**, a subset), `GET /api/admin/organos` (the whole catalogue), `GET /api/organos/taxonomia`, the catalogue import and the placement writes |
| `organo.json` | `GET /api/organo/{id}` — one stub per Órgano of the visible set, one for a withheld Órgano holding nothing, plus a catch-all 404 |

`organos.json` serves **two different catalogues on purpose**: the side-panel picker's
`/api/organos` holds 4 of the 8 Órganos `/api/admin/organos` lists, so the administration
area visibly shows what the picker withholds. Two of the taxonomía's terms — *Concellos*
and *Deputacións provinciais* — hold no Órgano of that subset and are therefore pruned
from the picker's tree while still appearing in the management one.

`organo.json` names the **same ids and names** as `organos.json`, so the page the picker
opens agrees with the row it was chosen from. **Every Órgano of the visible set holds
`contratosMenores`** — it has to, since `/api/organos` is defined as the Órganos holding
at least one visible contract, so a visible Órgano answering `families: {}` would be the
stub contradicting its own contract. The *no contracts* page therefore belongs to an
Órgano the picker **withholds**: *Axencia de Turismo de Galicia*, listed by
`/api/admin/organos` alone. That is also the only way a reader reaches that state — by a
retained link, never by anything the UI offers. The catch-all sits at a lower priority so
an unknown id answers the contract's `organo-not-found` problem rather than one of the five.

`metrics.json`'s samples are repeated as `metricsSamples` in
[`../acceptance/support/fixtures.ts`](../acceptance/support/fixtures.ts), which the metrics
specs stream at their own pace and derive every expected figure from.
**Change both files together** — the same rule as `users-enabled.json` below.

`POST` stubs echo the submitted values with response templating (the container runs with
`--global-response-templating`), so a created account shows the email you typed and a
toggled account keeps its identity.

## Known gaps

These are stub limitations, not app bugs — worth knowing before you chase one:

- **Nothing asserts the last-admin guard.** `ana.pereira` is seeded as the only enabled
  admin so the blocked disable control is reachable in dev, and the app returns a 409 if
  it is forced. The acceptance suite covers happy paths only, so no spec proves the guard
  holds — the server-side rule is covered by the backend suite.

- **`users-enabled.json` repeats each account's email, role and dates.** The UI replaces
  a row wholesale with the toggle response, so a stub that disagreed with `users.json`
  would silently rewrite the row. **Change both files together.**
- **A freshly created account cannot be toggled.** `users.json` returns a fixed id for
  every create, and no `/enabled` stub matches it, so disabling a just-created account
  404s. A stub cannot know the email you typed, and inventing one would be worse than the
  404.
- **The metrics stream is finite.** WireMock cannot hold a real SSE connection open, so
  `metrics.json` dribbles a few samples over 30 s and then closes; `EventSource`
  reconnects and replays them. That is enough for the acceptance suite to drive the panel
  — `stubEventStream` in [`../acceptance/support/wiremock.ts`](../acceptance/support/wiremock.ts)
  programs a faster stream per scenario, padding each frame so it lands in exactly one
  dribbled chunk and appending heartbeat comments to hold the connection open past the
  last sample. What a replayed body still cannot prove is the **cadence** and the
  **heartbeat interval** the server actually chooses, or that a `USER` is refused: those
  are the backend suite's, not this one's.

## Changing state temporarily

Prefer programming a stub over editing these files when you only need it for one
scenario — that is what the acceptance suite does, via
[`../acceptance/support/wiremock.ts`](../acceptance/support/wiremock.ts):

```bash
curl -X POST http://localhost:8081/__admin/mappings \
  -d '{"request":{"method":"GET","urlPath":"/api/me"},
       "response":{"status":200,"jsonBody":{"id":"…","email":"…","role":"USER","createdAt":"2026-01-15T09:30:00Z","lastLoginAt":null}}}'

curl -X POST http://localhost:8081/__admin/mappings/reset   # back to these files
```
