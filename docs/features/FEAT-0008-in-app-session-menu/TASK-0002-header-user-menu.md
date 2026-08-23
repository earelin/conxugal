---
feat: FEAT-0008
domain: frontend
adrs: [0004, 0015]
status: done
depends_on: [TASK-0001]
---

# Header user menu with a logout item

Governed by [ADR-0004](../../architecture/0004-ui-stack-vite-mantine.md) (Vite +
Mantine SPA) and
[ADR-0015](../../architecture/0015-frontend-feature-based-shared-core-modularization.md)
(the app shell lives in the `app` layer). Turns the header's existing user block into a
dropdown whose single item ends the session, consuming the action from
[TASK-0001](TASK-0001-session-logout-action.md). It adds no route, no screen and no new
API call of its own.

The mockups in [`design/`](design/README.md) are the target: they own the exact Galician
copy, the trigger and item states (rest, hover/focus, open, in flight, failed, below
`sm`) and the keyboard path. Build against them rather than re-deciding any of it.

## Scope
- A new `ui/src/app/layout/UserMenu.tsx` — shell chrome, so it belongs to the `app`
  layer rather than a feature slice; `app` importing `shared/entities` is a legal
  downward edge under ADR-0015.
- `ui/src/app/layout/AppLayout.tsx`: the inline user block becomes `UserMenu`. The
  trigger is present at **every** width — only the email and role-label text hides
  below the `sm` breakpoint, leaving the avatar alone (initials only, no chevron) on a
  narrow viewport. Today the whole block is hidden below `sm`, which is why logout
  would otherwise be unreachable there.
- The trigger is a Mantine `UnstyledButton` carrying an accessible name from
  `strings`, wrapping the existing `initialsOf` avatar plus a chevron affordance.
- The dropdown: an identity header repeating the account email and role label — the
  only place the account is named on a narrow viewport — a divider, and one logout
  item with a leading `IconLogout`. The item is **neutral, not red**: ending a session
  is not destructive, and red is reserved for destructive or required.
- The item calls `useLogout()`, and drives its states from that hook: disabled with a
  trailing `Loader` while `isPending`, and a red alert carrying the failure message
  under the item while `isError`. It does not close the menu on click, so the message is
  visible where the user just clicked.
- New Galician copy in `ui/src/shared/lib/strings.ts` under a `userMenu` key: the
  trigger's accessible name, the logout label, and the failure message. No user-facing
  literal goes in the component.
- Component tests for opening the menu, the logout path, and the failure path.

## Acceptance criteria
- From any authenticated screen at a typical desktop width, the account the session
  belongs to is identifiable in the header and the logout control is reachable in one
  interaction ([SPEC-0002](../../specs/SPEC-0002-user-authentication.md) #12).
- The trigger does not inherit the `sm` breakpoint: only the email/role text carries
  the responsive-visibility class, so the trigger survives below `sm` and logout stays
  reachable at a 360 px-wide viewport with nothing overflowing horizontally
  (SPEC-0002 #12, [SPEC-0001](../../specs/SPEC-0001-web-ui.md) R5/AC6). jsdom cannot
  evaluate media queries — `src/test/setup.ts` stubs `matchMedia` to `matches: false`
  unconditionally, so both variants render identically — so the component test asserts
  this **structurally**, on which element carries the breakpoint; the visual half is
  checked against `design/user-menu-narrow.svg`, drawn at exactly 360 px.
- Activating the logout item invokes the TASK-0001 action once and, on success, triggers
  its navigation (asserted with a stubbed action; the session actually ending is
  already-delivered logout behaviour and is not re-verified here).
- The item is disabled and shows a `Loader` while the request is in flight, so repeat
  clicks cannot fire two logouts.
- A logout that fails for a reason other than a lost session leaves the user on the
  current screen with the Galician failure message shown in the **still-open** dropdown
  — not a silent no-op and not a redirect that would bounce straight back.
- The trigger and the menu item are operable by keyboard alone and carry accessible
  names (SPEC-0001 R4/AC5), following the path recorded in `design/user-menu-states.svg`.
- All added copy is Galician, matches the wording in `design/README.md` and is sourced
  from `strings.ts`; tests assert against `strings`, never literals (SPEC-0001 R6/AC7).
- The existing header assertions in `AppLayout.test.tsx` — email, role label and
  avatar initials — still pass unchanged.
- Component-tested with a stubbed API, covering the closed and open menu, a successful
  logout and a failed one.
