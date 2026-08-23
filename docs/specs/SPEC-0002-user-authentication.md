---
status: active
---

# SPEC-0002. User authentication

## Summary

The system restricts access to authenticated users and distinguishes what each
user may do according to their assigned role. Users prove their identity with an
email address and a password.

## Roles

Every user has exactly one role.

| Role  | Capabilities                                              |
| ----- | -------------------------------------------------------- |
| USER  | View and analyze the contract data.                      |
| ADMIN | Everything a USER can do, plus access to the admin area. |

ADMIN is a strict superset of USER: any capability granted to USER is also
granted to ADMIN.

## Requirements

### Authentication

- **R1** — A user authenticates by providing an email address and a password.
- **R2** — The system grants access only when the email identifies a known user
  and the supplied password matches that user's password.
- **R3** — Authentication fails when the email is unknown or the password does
  not match. A failed attempt does not reveal which of the two was wrong.
- **R4** — An unauthenticated visitor cannot reach data or admin functionality.

### Authorization

- **R5** — The data views and analysis features are available to any
  authenticated user (USER or ADMIN).
- **R6** — The admin area is available only to users with the ADMIN role.
- **R7** — A USER attempting to reach the admin area is denied access.

### Sessions

- **R8** — After a successful login the user remains authenticated across
  subsequent requests without re-entering credentials each time.
- **R9** — A user can end their authenticated session (log out), after which
  access requires logging in again.
- **R10** — An authenticated session expires after a period of inactivity, after
  which access requires logging in again. The concrete inactivity window is a
  feature-level decision.
- **R15** — While authenticated, the interface continuously shows which account the
  session belongs to, and offers a discoverable control to end that session from any
  screen, at both desktop and narrow viewport widths. R9 says a user *can* log out;
  this says the interface must *offer* it, so that inactivity expiry (R10) or
  discarding credentials is not the only way out.

### Credentials

- **R11** — Passwords are never stored or displayed in a form from which the
  original password can be recovered.
- **R12** — Passwords are never exposed in logs, error messages, or responses.

### Login record

- **R13** — On each successful authentication the system records the moment it
  occurred as that user's most recent login, replacing any previously recorded
  value. A failed attempt leaves the recorded value unchanged. Before a user's
  first successful login there is no recorded value.

### Self-lookup

- **R14** — An authenticated user can retrieve their own account identity, role,
  creation date, and last login (id, email, role, createdAt, lastLoginAt). The
  enabled state is not included: a live authenticated session already implies
  the account is enabled, and the password hash is never exposed (R11, R12).

## Scope

- **Account provisioning is out of scope of this spec.** How users and their
  roles are created, edited, or removed is specified separately; this spec
  assumes known users already exist.
- **Throttling of repeated failed attempts (brute-force protection) is out of
  scope of this spec** and may be added by a later spec without changing the
  requirements above.

## Acceptance criteria

1. **(R4)** A visitor with no active session who requests a data or admin page is
   sent to the login process instead of seeing the content.
2. **(R1, R2)** Submitting a correct email + password pair for an existing user
   starts an authenticated session.
3. **(R3)** Submitting an unknown email, or a known email with a wrong password,
   is rejected with a single generic error that does not disclose which field was
   incorrect.
4. **(R5)** An authenticated USER can open the data and analysis views.
5. **(R7)** An authenticated USER who requests the admin area is denied.
6. **(R6)** An authenticated ADMIN can open both the data/analysis views and the
   admin area.
7. **(R9)** After logging out, a previously authenticated user requesting a
   protected page is treated as unauthenticated (criterion 1 applies again).
8. **(R10)** After a session has been idle beyond the inactivity window, a
   previously authenticated user requesting a protected page is treated as
   unauthenticated (criterion 1 applies again).
9. **(R11, R12)** At no point is a stored or transmitted password readable in its
   original form.
10. **(R13)** After a user logs in successfully, the user's most-recent-login value
    reflects that login's time; a later successful login replaces it, and a rejected
    attempt leaves it unchanged.
11. **(R14)** An authenticated user requesting their own account data receives
    their id, email, role, creation date, and last login (no enabled state, no
    password); an unauthenticated caller is denied (criterion 1 applies).
12. **(R15)** From any authenticated screen, at a typical desktop browser width and
    at a 360 px-wide viewport, the account the session belongs to is identifiable and
    a control that ends the session is reachable within one interaction; using that
    control satisfies criterion 7.

## Implemented by

- **FEAT-0002** — User authentication (retired 2026-08-23, commit `6d8a9f4`)
  - Decisions: [ADR-0005](../architecture/0005-session-based-authentication.md)
    (server-side sessions over JWT, server-rendered login outside the SPA, indistinct
    failure, 401-not-redirect),
    [ADR-0008](../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md)
    (`lastLoginAt` mapped on the domain `User`, no shadow entity),
    [ADR-0011](../architecture/0011-blocking-io-virtual-threads.md) (login runs on a
    virtual thread, not the event loop),
    [ADR-0024](../architecture/0024-argon2id-password-hashing.md) (Argon2id and its
    self-describing stored form)
  - System: [`server/CLAUDE.md`](../../server/CLAUDE.md) — the session model as
    configured, the 30-minute idle window, the Accept-header 401/303 split, the
    three-branch indistinct-failure contract and the server-rendered views;
    [`ui/CLAUDE.md`](../../ui/CLAUDE.md) — how the SPA detects a gone session and
    redirects once
  - Behaviour: `AcceptHeaderRejectionTest` covers AC1 — a browser navigation without a
    session gets `303` to `/login`, an XHR gets `401`, which is the split the SPA depends
    on; `SessionAuthenticationTest` covers AC2 and AC4–AC6, plus the `401` half of AC1
    with redirects disabled;
    `AuthenticateTest`, `UserAuthenticationProviderTest` and
    `LoginPageTest#failed_login_query_param_shows_single_generic_error` cover AC3 —
    including `compares_the_password_against_the_dummy_hash_when_the_email_is_unknown`,
    which pins the no-short-circuit rule; `LogoutTest` covers AC7;
    `IdleSessionTimeoutTest` covers AC8;
    `JdbcUserRepositoryIntegrationTest#never_stores_the_password_as_plaintext` and
    `UserTest#toString_redacts_password_hash` cover AC9; `AuthenticateTest` and
    `JdbcUserRepositoryIntegrationTest` cover AC10
  - FEAT-0002 closed neither **R14** nor **R15**: the self-lookup endpoint
    (`GET /api/me`, AC11) was delivered by
    [FEAT-0004](../features/FEAT-0004-administration-area/README.md), and the in-app
    account menu that ends the session (AC12) by
    [FEAT-0008](../features/FEAT-0008-in-app-session-menu/README.md)

<!-- distilled-from: FEAT-0002 @ 6d8a9f4 -->
