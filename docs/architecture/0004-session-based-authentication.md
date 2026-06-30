---
status: accepted
date: 2026-06-30
spec: SPEC-001
supersedes: null
superseded_by: null
---

# 0004. Session-based authentication with a server-rendered login

## Status
Accepted

## Context
SPEC-001 requires authenticated, role-aware access (`USER`, `ADMIN`) via email +
password, with unauthenticated visitors and expired sessions kept out of the data
and admin functionality. The server is the single deployable unit and single
origin for the React Router SPA and the REST API (ADR-0003), built on Micronaut
(ADR-0001) in a hexagonal structure (ADR-0002).

This forces a cross-cutting choice that every protected route depends on: how a
caller proves identity, where that identity lives between requests, and how the
login UI is delivered. The main options:

- **Token (e.g. JWT) in the SPA** — the SPA owns a bearer token. Stateless, but
  the token must be stored in the browser (XSS/storage exposure), and the login
  UI ships inside the very bundle we want to gate.
- **Server-side session + cookie** — the server holds session state and tracks it
  with an `HttpOnly` cookie; the browser sends it automatically. Stateful, but the
  credential never lives in JS-reachable storage and the gate sits at the server
  edge.

Because there is a single origin, a cookie is same-site by construction and needs
no CORS handling. Delivering the login screen as the SPA's own route would mean
shipping the bundle to anonymous visitors and authenticating from inside it; a
server-rendered login page keeps the unauthenticated surface tiny and lets the
SPA load only once a session exists.

## Decision
Authenticate with **server-side sessions tracked by an `HttpOnly` session
cookie**, using **Micronaut Security in `session` authentication mode**.

- The **login UI is a server-rendered static page**, served by the backend
  **outside the SPA bundle**. The SPA is loaded only after a session is
  established.
- **Passwords are stored as salted hashes**; the plaintext is never stored,
  logged, or returned, and verification compares hashes only.
- **Authorization uses roles** (`USER`, `ADMIN`) enforced at the server edge via
  `@Secured` rules. `ADMIN` is a strict superset of `USER`.
- **Unauthenticated access is handled by request type**: page navigations are
  **redirected to the login page**; API/XHR calls receive **401** (no HTML
  redirect), which the SPA acts on by navigating to login. A logged-in user
  hitting a higher-role route is **forbidden (403)**, distinct from
  unauthenticated.
- The credential check lives in the **domain** (ADR-0002): the authenticate use
  case returns success-with-role or an **indistinct failure**, so "unknown email"
  and "wrong password" are never separable (SPEC-001 criterion 3). Micronaut
  Security's `AuthenticationProvider` is a driving adapter in the **application**
  module; the user store and password hashing are driven adapters in
  **infrastructure**.

## Consequences
+ The credential never lives in JS-reachable browser storage; the `HttpOnly`
  cookie is sent automatically and the anonymous attack surface is just the login
  page.
+ A single enforcement point at the server edge guards the SPA, the API and the
  admin area uniformly, with role rules expressed declaratively (`@Secured`).
+ Single origin means no CORS and no token-refresh machinery.
+ The authentication mechanism stays out of the domain, which only sees the
  use-case ports — keeping ADR-0002's boundaries intact.
− Server-side session state is stateful: it must be stored and, for horizontal
  scaling, shared (e.g. a session store) rather than held in one instance's
  memory. Revisit with a new ADR if multi-instance scaling demands it.
− State-changing form POSTs (login, logout) require **CSRF protection**, which
  cookie-based auth makes necessary.
− The SPA must handle **401** on API calls itself (redirect to login); a plain
  redirect is not enough for XHR.
− Two render paths coexist: server-rendered views for login/forbidden and the SPA
  for everything else.
