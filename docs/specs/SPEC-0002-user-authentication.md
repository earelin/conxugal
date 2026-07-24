---
status: draft
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
