---
feat: FEAT-0008
domain: frontend
adrs: [0005, 0015]
status: done
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
backend**: `POST /logout` and the 401-on-XHR behaviour are already delivered for
[SPEC-0002](../../specs/SPEC-0002-user-authentication.md), and documented in
[`server/CLAUDE.md`](../../../server/CLAUDE.md).

## Scope
- A `logout()` call in `ui/src/shared/entities/currentUser.ts` — the module ADR-0015
  names as the cross-feature session read; the single write that *ends* the session
  belongs beside it, since it is session lifecycle rather than any feature's data.
  It posts an empty JSON body (`{}`) to `/logout` with a JSON content type and
  `redirect: 'manual'`, treating every status below 400 as success so an opaque
  redirect, the server's `303` and a followed `200` are all accepted.
- A `useLogout()` mutation hook that navigates the browser to `/login` with
  `window.location.replace` once the call succeeds, matching the existing session-loss
  navigation in `shared/lib/queryClient.ts`. It returns the React Query mutation result,
  so TASK-0002 consumes `mutate`, `isPending` and `isError` and nothing else.
- Unit tests with `nock`, following `shared/lib/httpClient.test.ts`, asserting the
  navigation with the `vi.stubGlobal('location', …)` pattern already used by
  `shared/lib/queryClient.test.ts`.

Five constraints the implementation must respect, all explained in the feature's
Design section:
- The call **must not** go through `apiFetch`: it sets `Accept: application/json` and
  throws on a non-ok response, and following the `303` lands on the HTML-only
  `/login`, which answers 406 — turning a successful logout into an error.
- The request **must** carry a JSON content type. A form-shaped post is gated by the
  server's CSRF filter, and the SPA has no CSRF-token seam.
- The request **must not** set `Accept`. Micronaut answers a dead session according to
  it — `text/html` yields a `303`, which the below-400 rule would read as success, and
  the 401 path would never run.
- A status of 400 or above **must** reject with `HttpError` from
  `shared/lib/httpClient.ts`, carrying the status: that type is what the shared
  session-loss handler keys on.
- The React Query cache **must not** be cleared before navigating. The page unloads
  anyway, and clearing it refetches active queries against a session that no longer
  exists — a 401 and a second redirect racing the first.

## Acceptance criteria
- A logout call that the server accepts sends the browser to `/login` with a full-page
  navigation — the "using that control" half of
  [SPEC-0002](../../specs/SPEC-0002-user-authentication.md) #12. Each of the three
  accepted shapes (opaque redirect, `303`, `200`) navigates.
- The request posts a JSON content type with no `Accept` header and no CSRF token, and
  is not routed through `apiFetch`.
- A failure of 400 or above rejects with `HttpError` carrying that status; a non-401
  failure does not navigate, leaves the user on the current screen, and surfaces the
  error through the mutation so chrome can report it.
- A 401 is left to the shared session-loss handler in `shared/lib/queryClient.ts` — the
  action adds no redirect of its own — and the React Query cache is not cleared on the
  success path.
- `useLogout()` exposes `mutate`, `isPending` and `isError` for the chrome in
  [TASK-0002](TASK-0002-header-user-menu.md) to drive its in-flight and failed states.
- Unit-tested with a stubbed HTTP layer covering the success navigation, the request
  shape (JSON content type, no `Accept`, no CSRF token), and the non-401 failure path
  including the `HttpError` status.
