---
feat: FEAT-0008
domain: frontend
adrs: [0005, 0015]
status: todo
depends_on: []
---

# Session logout action in the SPA

Governed by [ADR-0005](../../architecture/0005-session-based-authentication.md)
(session-based authentication) and
[ADR-0015](../../architecture/0015-frontend-feature-based-shared-core-modularization.md)
(feature slices with a shared core). Adds the client-side call that ends the session
and the navigation that follows it, alongside the existing session read in
`shared/entities/currentUser.ts`. It adds **no chrome** — the control that triggers it
arrives in [TASK-0002](TASK-0002-header-user-menu.md) — and it changes **nothing on the
backend**: `POST /logout` and the 401-on-XHR behaviour are already delivered by
[FEAT-0002](../FEAT-0002-user-authentication/TASK-0005-logout-and-spa-401-handling.md).

## Scope
- A `logout()` call in `ui/src/shared/entities/currentUser.ts` — the module ADR-0015
  names as the cross-feature session read — posting to `/logout` with a JSON content
  type and `redirect: 'manual'`, treating every status below 400 as success so an
  opaque redirect, the server's `303` and a followed `200` are all accepted.
- A `useLogout()` mutation hook that navigates the browser to `/login` with
  `window.location.replace` once the call succeeds, matching the existing session-loss
  navigation in `shared/lib/queryClient.ts`.
- Unit tests with `nock`, following `shared/lib/httpClient.test.ts`, asserting the
  navigation with the `vi.stubGlobal('location', …)` pattern already used by
  `shared/lib/queryClient.test.ts`.

Two constraints the implementation must respect, both explained in the feature's
Design section:
- The call **must not** go through `apiFetch`: it sets `Accept: application/json` and
  throws on a non-ok response, and following the `303` lands on the HTML-only
  `/login`, which answers 406 — turning a successful logout into an error.
- The request **must** carry a JSON content type. A form-shaped post is gated by the
  server's CSRF filter, and the SPA has no CSRF-token seam.

## Acceptance criteria
- A successful logout call ends the session and sends the browser to `/login`; a
  protected route requested afterwards is treated as unauthenticated
  ([SPEC-0002](../../specs/SPEC-0002-user-authentication.md) #7).
- The call succeeds without a CSRF token, because it posts a JSON content type rather
  than a form-shaped body.
- A failure other than 401 does not navigate, leaves the user on the current screen,
  and surfaces the error to the caller so chrome can report it.
- A 401 is left to the shared session-loss handler in `shared/lib/queryClient.ts`, so
  a logout against an already-dead session redirects exactly once.
- Unit-tested with a stubbed HTTP layer covering the success navigation, the
  non-401 failure path, and that no CSRF token is sent.
