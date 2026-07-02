---
status: draft
---

# SPEC-002 User authentication

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

- A user authenticates by providing an email address and a password.
- The system grants access only when the email identifies a known user and the
  supplied password matches that user's password.
- Authentication fails when the email is unknown or the password does not match.
  A failed attempt does not reveal which of the two was wrong.
- An unauthenticated visitor cannot reach data or admin functionality.

### Authorization

- The data views and analysis features are available to any authenticated user
  (USER or ADMIN).
- The admin area is available only to users with the ADMIN role.
- A USER attempting to reach the admin area is denied access.

### Sessions

- After a successful login the user remains authenticated across subsequent
  requests without re-entering credentials each time.
- A user can end their authenticated session (log out), after which access
  requires logging in again.

### Credentials

- Passwords are never stored or displayed in a form from which the original
  password can be recovered.
- Passwords are never exposed in logs, error messages, or responses.

## Acceptance criteria

1. A visitor with no active session who requests a data or admin page is sent to
   the login process instead of seeing the content.
2. Submitting a correct email + password pair for an existing user starts an
   authenticated session.
3. Submitting an unknown email, or a known email with a wrong password, is
   rejected with a single generic error that does not disclose which field was
   incorrect.
4. An authenticated USER can open the data and analysis views.
5. An authenticated USER who requests the admin area is denied.
6. An authenticated ADMIN can open both the data/analysis views and the admin
   area.
7. After logging out, a previously authenticated user requesting a protected
   page is treated as unauthenticated (criterion 1 applies again).
8. At no point is a stored or transmitted password readable in its original
   form.
