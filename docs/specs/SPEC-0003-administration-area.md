---
status: draft
---

# SPEC-0003. Administration area

## Summary

The system provides an **administration area** reachable only by an administrator,
where they monitor the running system and manage the user accounts that
[SPEC-0002](SPEC-0002-user-authentication.md) assumes already exist. It offers two
capabilities: a **system dashboard** that reports the server's operational status, and
**user administration** to list, create, and disable accounts. Accounts are never
deleted — disabling is the only way to remove access.

Access control for the area itself is established by SPEC-0002 (the admin area is
`ADMIN`-only); this spec describes what the area *contains*.

## Requirements

### Access

- **R1** — The administration area and all its functions are reachable only by users
  with the `ADMIN` role; a `USER` who requests them is denied (consistent with
  SPEC-0002 R6/R7).

### System dashboard

- **R2** — An administrator can view a dashboard reporting the operational status of
  the running system.
- **R3** — The dashboard reports at least the overall service state (up/degraded) and
  the reachability of the critical dependency it relies on (the datastore).
- **R4** — The reported status reflects the system's state at the time the dashboard is
  viewed, not a stale snapshot from an earlier point.
- **R5** — The status information never discloses secrets or credentials (connection
  passwords, tokens, keys).

### User administration

- **R6** — An administrator can view a list of all user accounts, each showing its
  identity (email), role, account state (enabled or disabled), and the date the account
  was created.
- **R7** — An administrator can create a new user account by supplying an email and a
  role; the system generates the initial password (the administrator does not choose
  it). The new account can authenticate immediately per SPEC-0002 with that password.
- **R8** — Email uniquely identifies an account; an attempt to create an account with
  an email that already exists is rejected without altering the existing account.
- **R9** — An administrator can disable an existing account. A disabled account cannot
  authenticate (this refines SPEC-0002 R2: identity and password matching are necessary
  but a disabled account is still denied).
- **R10** — An administrator can re-enable a disabled account, restoring its ability to
  authenticate.
- **R11** — Accounts are never deleted. A disabled account remains listed and can be
  re-enabled; there is no operation that permanently removes an account.
- **R12** — The system prevents an administrator from disabling the last enabled
  administrator, so the administration area can never become unreachable.

### Credentials

- **R13** — A created account's password is stored under SPEC-0002's credential rules
  (never kept or exposed in a recoverable form, never exposed in logs or errors). The
  system-generated initial password is the one exception to non-disclosure: it is shown
  to the creating administrator **exactly once**, in the direct response to the creation
  request, so it can be relayed to the new user.
- **R14** — The system generates the initial password from a cryptographically secure,
  unpredictable source; it is not derived from the email, role, or any other guessable
  value.
- **R15** — Every generated password meets a fixed strength policy: at least 16
  characters, drawn from a mix of uppercase letters, lowercase letters, digits, and
  symbols. The policy is uniform for all generated passwords.
- **R16** — Once the creation response is returned, the generated password is not
  retrievable by any function of the administration area; it never appears in the user
  list, a later read, or any subsequent response. If it is lost before being relayed,
  there is no recovery within this area.

## Acceptance criteria

1. **(R1)** An authenticated `USER` who requests any administration-area screen or
   function is denied; an authenticated `ADMIN` is allowed.
2. **(R2, R3)** Opening the dashboard as an administrator shows the overall service
   state and the datastore's reachability.
3. **(R4)** When a critical dependency becomes unreachable, a dashboard viewed
   afterwards reflects the changed state rather than the previous healthy one.
4. **(R5)** No secret or credential value appears anywhere in the dashboard's reported
   status.
5. **(R6)** The user list shows every account — enabled and disabled — with its email,
   role, state, and creation date.
6. **(R7)** After an administrator creates an account with a valid email and role, the
   system returns a generated initial password, that account appears in the list, and it
   can authenticate per SPEC-0002 with the returned password.
7. **(R8)** Creating an account with an already-existing email is rejected and the
   existing account is unchanged.
8. **(R9)** After an account is disabled, an authentication attempt with its correct
   email and password is denied.
9. **(R10)** After a disabled account is re-enabled, an authentication attempt with its
   correct email and password succeeds.
10. **(R11)** No administration function removes an account from the list; a disabled
    account is still present and can be re-enabled.
11. **(R12)** An attempt to disable the only remaining enabled administrator is rejected
    and that account stays enabled.
12. **(R13)** A created account's password is readable in its original form only in the
    direct response to its creation request, and at no other point (not in the user list,
    a later read, logs, or errors).
13. **(R14, R15)** A generated initial password is at least 16 characters and includes
    uppercase letters, lowercase letters, digits, and symbols; two accounts created in
    succession receive different, unpredictable passwords.
14. **(R16)** After the creation response is returned, no administration function
    surfaces the generated password again.
