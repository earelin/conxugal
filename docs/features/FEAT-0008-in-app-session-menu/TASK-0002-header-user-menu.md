---
feat: FEAT-0008
domain: frontend
adrs: [0004, 0015]
status: todo
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

## Scope
- A new `ui/src/app/layout/UserMenu.tsx` — shell chrome, so it belongs to the `app`
  layer rather than a feature slice; `app` importing `shared/entities` is a legal
  downward edge under ADR-0015.
- `ui/src/app/layout/AppLayout.tsx`: the inline user block becomes `UserMenu`. The
  trigger is present at **every** width — only the email and role-label text hides
  below the `sm` breakpoint, leaving an initials-only trigger on a narrow viewport.
  Today the whole block is hidden below `sm`, which is why logout would otherwise be
  unreachable there.
- The trigger is a Mantine `UnstyledButton` carrying an accessible name from
  `strings`, wrapping the existing `initialsOf` avatar plus a chevron affordance.
- The dropdown: an identity header repeating the account email and role label — the
  only place the account is named on a narrow viewport — a divider, and one logout
  item with a leading `IconLogout`. The item is **neutral, not red**: ending a session
  is not destructive, and red is reserved for destructive or required.
- The item calls `useLogout()`, stays disabled while the request is in flight, and
  does not close the menu on click, so a failure message is visible where the user
  just clicked.
- New Galician copy in `ui/src/shared/lib/strings.ts` under a `userMenu` key: the
  trigger's accessible name, the logout label, and the failure message. No user-facing
  literal goes in the component.
- Component tests for opening the menu, the logout path, and the failure path.

## Acceptance criteria
- From any authenticated screen at a typical desktop width, the account the session
  belongs to is identifiable in the header and the logout control is reachable in one
  interaction ([SPEC-0002](../../specs/SPEC-0002-user-authentication.md) #12).
- At a 360 px-wide viewport the trigger is still present and its dropdown names the
  account, so logout stays reachable and nothing overflows horizontally
  (SPEC-0002 #12, [SPEC-0001](../../specs/SPEC-0001-web-ui.md) #6).
- Activating the logout item ends the session and lands on the login page; a protected
  route requested afterwards is treated as unauthenticated (SPEC-0002 #7).
- A logout that fails for a reason other than a lost session leaves the user on the
  current screen with an explanatory Galician message, not a silent no-op and not a
  redirect that would bounce straight back.
- The trigger and the menu item are operable by keyboard alone and carry accessible
  names (SPEC-0001 #5).
- All added copy is Galician and sourced from `strings.ts`; tests assert against
  `strings`, never literals (SPEC-0001 #7).
- The existing header assertions in `AppLayout.test.tsx` — email, role label and
  avatar initials — still pass unchanged.
- Component-tested with a stubbed API, covering the closed and open menu, a successful
  logout and a failed one.
