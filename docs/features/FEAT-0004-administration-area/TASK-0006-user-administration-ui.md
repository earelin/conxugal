---
feat: FEAT-0004
adrs: [0003, 0004]
status: todo
depends_on: [TASK-0003, TASK-0005]
---

# User-administration UI

Governed by [ADR-0004](../../architecture/0004-ui-stack-vite-mantine.md) (Vite + Mantine SPA). The Users page inside the admin shell from [TASK-0005](TASK-0005-admin-ui-shell-and-dashboard.md), consuming [TASK-0003](TASK-0003-user-administration-rest-endpoints.md)'s endpoints. Visual target: [`design/users-list.svg`](design/users-list.svg) and [`design/create-user.svg`](design/create-user.svg).

## Scope
- User-list page: every account with email, role, state, created date, and last login date (disabled accounts shown, not hidden). The last-login cell renders *Nunca* when the account has never logged in successfully (null value).
- Create-user form asking only for email and role (no password field) that posts to the create endpoint; on success it reveals the server-generated initial password **once**, with a copy affordance and a warning that it will not be shown again.
- Disable/enable action per row, calling the enabled endpoint and reflecting the new state.
- Galician chrome and copy in `strings.ts`.

## Acceptance criteria
- The list shows every account — enabled and disabled — with its email, role, state, created date, and last login date; an account with no successful login yet shows *Nunca*. ([SPEC-0003](../../specs/SPEC-0003-administration-area.md) #5; [SPEC-0002](../../specs/SPEC-0002-user-authentication.md) #10)
- Submitting a valid create form (email and role only) adds the account to the list and reveals the returned generated password once. (SPEC-0003 #6)
- A duplicate-email create is surfaced as a form error without altering the existing account. (SPEC-0003 #7)
- Disabling then re-enabling a row updates its displayed state; a disabled account stays listed and can be re-enabled. (SPEC-0003 #8, #9, #10)
- The action to disable the only remaining enabled `ADMIN` is prevented (disabled control / surfaced refusal). (SPEC-0003 #11)
- The form has no password field; the generated password is shown only once on creation and never surfaced again in the list or a later view. (SPEC-0003 #12, #14)
- All added chrome and messages are in Galician. (SPEC-0001 #6)
- Component-tested for the list render (including an account that shows *Nunca* for last login), create submission, duplicate-email error, and the enable/disable action.
