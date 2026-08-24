---
status: active
---

# SPEC-0003. Administration area

## Summary

The system provides an **administration area** reachable only by an administrator,
where they monitor the running system and manage the user accounts that
[SPEC-0002](SPEC-0002-user-authentication.md) assumes already exist. This spec
establishes the area and contributes two capabilities to it: a **system dashboard** that
reports the server's operational status, and **user administration** to list, create, and
disable accounts. Accounts are never deleted — disabling is the only way to remove access.
The dashboard also offers, for administrators, a **live view of detailed runtime metrics**
for debugging a running instance; these metrics are transient and are not stored by the
system.

The area is **extensible, and this spec does not enumerate it**: later specs add their own
administrative surfaces to the same area and each owns what it adds — importing and
organising the Órgano catalogue
([SPEC-0004](SPEC-0004-import-manage-organos-contratacion.md)), selecting which Órganos are
imported and removing a withdrawn contract
([SPEC-0005](SPEC-0005-import-browse-contratos-menores.md)), and reviewing the outcome of
import runs ([SPEC-0007](SPEC-0007-monitor-import-runs.md)). What every such surface
inherits from here is the access rule of R1; what none of them inherits is R20 — the
prohibition on storing detailed metrics binds this spec's metrics view and nothing else, and
SPEC-0007's run history is durable by design.

Access control for the area itself is established by SPEC-0002 (the admin area is
`ADMIN`-only); this spec describes what it *contributes* to the area.

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
  identity (email), role, account state (enabled or disabled), the date the account was
  created, and the date of its most recent successful login (the value recorded per
  SPEC-0002 R13, empty until the account's first successful login).
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

### Detailed metrics

- **R17** — An administrator can open a view of detailed runtime metrics of the running
  instance (for example memory, threads, uptime, and request and datastore-pool
  counters). This is richer than the coarse operational status of R2/R3 and is intended
  for debugging.
- **R18** — The detailed metrics update **live** as the instance's state changes, without
  the viewer having to manually refresh.
- **R19** — The detailed metrics are available only to administrators; a `USER` or an
  unauthenticated visitor cannot obtain them (consistent with R1).
- **R20** — The system does not store detailed metrics: they reflect the instance's
  current state, the backend keeps no history of them, and there is no function that
  returns past metric values. Any retained history exists only in the viewing client, for
  that admin's debugging, and is discarded when they leave or reload the view.
- **R21** — The detailed metrics never disclose secrets or credentials (the same rule as
  R5).

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
   role, state, creation date, and most-recent-login date (empty for an account that has
   never logged in successfully).
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
15. **(R17, R18)** An administrator opening the detailed-metrics view sees runtime metrics
    that update live as the instance changes, without a manual refresh.
16. **(R19)** A `USER` or unauthenticated visitor that requests the detailed metrics is
    denied.
17. **(R20)** No detailed metric value is persisted by the backend: there is no function
    or store that returns a past metric value, and history held in the client is cleared
    on reload.
18. **(R21)** No secret or credential value appears anywhere in the detailed metrics.

## Implemented by

- **FEAT-0004** — Administration area (retired 2026-08-24, commit `7402d8a`)
  - Decisions: no ADR of its own. It is governed by
    [ADR-0002](../architecture/0002-hexagonal-architecture.md) (the module split its use
    cases, ports and adapters sit in),
    [ADR-0006](../architecture/0006-reserved-api-url-prefix.md) (the `/api/` prefix its
    endpoints are mounted under),
    [ADR-0008](../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md)
    (`enabled` and `createdAt` mapped on the domain `User`, and the database's
    `DEFAULT uuidv7()` assigning identity on insert),
    [ADR-0010](../architecture/0010-design-first-openapi-contract.md) (the contract is
    authoritative, which is why the endpoint shapes are not restated in prose),
    [ADR-0004](../architecture/0004-ui-stack-vite-mantine.md) and
    [ADR-0018](../architecture/0018-frontend-acceptance-tests-against-a-stubbed-api.md) (the
    admin screens and how they are covered). R17–R21 are not this feature's: they are
    [ADR-0009](../architecture/0009-sse-admin-realtime-metrics.md)'s and FEAT-0005's, and
    this feature's status snapshot deliberately stayed coarse rather than growing into them.
  - Contract: [`docs/api/openapi.yaml`](../api/openapi.yaml) — `GET`/`POST /api/admin/users`,
    `POST /api/admin/users/{id}/enabled`, `GET /api/admin/system-status` and `GET /api/me`,
    with `CreateUserRequest.email` carrying the `pattern` that is the enforced rule and
    `CreatedUser.initialPassword` the generated-password strength policy. The `description`
    on that email field records why the response schemas deliberately carry no such pattern
    (the seeded dot-less accounts stay readable), which is the open half of QA finding
    [L-7](../qa/2026-08-05-ui-qa-review.md). The rule it generalises to —
    *a rule the server enforces is a `pattern`, never a `format`* — is in
    [`docs/api/CLAUDE.md`](../api/CLAUDE.md).
  - System: [`server/CLAUDE.md`](../../server/CLAUDE.md) § Authentication and authorization
    — the account lifecycle as built (create, enable/disable, and nothing else), why a lost
    initial password strands its email address permanently, and why the disabled-account
    check must stay *after* the password check;
    [`ui/CLAUDE.md`](../../ui/CLAUDE.md) — `AdminRoute` as both role guard and chunk-warming
    seam, and the WireMock-stubbed local API the admin screens run against.
  - Design: [administration-area mockups](../design/administration-area/README.md) — the
    two screens and the create dialog at full-screen scale; the rules they render are stated
    in the [`frontend-design` skill](../../.claude/skills/frontend-design/SKILL.md).
  - Behaviour: `AdminApiAccessControlTest` and every controller test's
    `user_role_is_forbidden`/`unauthenticated_caller_is_unauthorized` pair cover AC1, with
    `admin-access.spec.ts` covering the nav's affordance-only half;
    `SystemStatusControllerIntegrationTest#admin_sees_up_status_when_the_datastore_is_reachable`
    and `#admin_sees_degraded_status_when_the_datastore_is_unreachable` cover AC2 in both
    of its states; AC3 is the *freshness* claim and needs a test that asks twice —
    `AdminSystemStatusTest#admin_reads_system_status_reporting_the_datastore_is_reachable`
    calls the endpoint a second time and requires `checkedAt` to have moved on, and
    `admin-dashboard.spec.ts`'s second scenario navigates away and back rather than
    reloading, so a cached snapshot would fail it; that same `AdminSystemStatusTest` covers
    AC4 by asserting the payload carries no `jdbc:`, `postgres`, `password` or the local
    datastore credential;
    `AdminUserAdministrationTest#admin_lists_accounts_including_the_new_one_that_never_logged_in`
    and `admin-users.spec.ts` cover AC5 — the Java one also asserting the *admin's own*
    last-login is present, so an empty value means "never" rather than a field that stopped
    rendering; `AdminUserAdministrationTest#admin_creates_account_and_the_new_user_signs_in_with_the_generated_password`
    covers AC6, AC12 and AC14 end to end; `UsersControllerIntegrationTest#create_with_existing_email_is_conflict`
    covers AC7's refusal and `CreateUserTest#refuses_to_create_an_account_with_an_already_used_email`
    the half it cannot — that the existing account is left untouched, which a test mocking
    the use case can never show; `AdminUserAdministrationTest#admin_disables_account_denying_sign_in_and_re_enables_it`
    covers AC8, AC9 and AC10 — it re-lists the account while disabled and finds it still
    there, which is the half AC10 turns on; `admin-users.spec.ts` covers AC10 from the UI in
    its first scenario (a disabled account listed, offering *Activar*) and its third (a row
    that survives a disable/re-enable round trip);
    `SetUserEnabledTest#refuses_to_disable_the_only_remaining_enabled_admin`
    and `UsersControllerIntegrationTest#disabling_last_admin_is_conflict` cover AC11; and
    `PasswordGeneratorTest`'s three cases cover AC13
  - **R17–R21 are closed by [FEAT-0005](../features/FEAT-0005-admin-realtime-metrics/README.md)**,
    not by this feature

<!-- distilled-from: FEAT-0004 @ 7402d8a -->
