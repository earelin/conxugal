---
feat: FEAT-0002
adrs: [0005]
status: todo
depends_on: [TASK-0003]
---

# Run authentication off the event loop

Governed by [ADR-0005](../../architecture/0005-session-based-authentication.md)
(session-based authentication). Limited to how `UserAuthenticationProvider` dispatches
the blocking credential check introduced in
[TASK-0003](TASK-0003-security-config-session-auth.md); no change to the authenticate
use case, the password hashing algorithm, or the session/CSRF/`@Secured` config it
wired.

`UserAuthenticationProvider` implements `HttpRequestAuthenticationProvider` and is
annotated `@Blocking` (`io.micronaut.core.annotation.Blocking`), but that annotation
does not move work off the Netty event loop for this interface. The Argon2id password
verification it drives (`Argon2idPasswordEncoder`, via the domain `Authenticate` use
case) is deliberately slow, so every login request currently blocks an event-loop
thread for the duration of the hash comparison. Micronaut Security's own docs call for
`HttpRequestExecutorAuthenticationProvider` instead: it dispatches `authenticate(...)`
onto an executor (`TaskExecutors.BLOCKING` by default) rather than the reactive
pipeline.

## Scope
- `UserAuthenticationProvider`: implement `HttpRequestExecutorAuthenticationProvider`
  instead of `HttpRequestAuthenticationProvider`; drop the now-ineffective `@Blocking`
  annotation.
- No change to `Authenticate`, `Argon2idPasswordEncoder`, or any domain/infrastructure
  port — this task only changes which thread pool runs the existing check.

## Acceptance criteria
- Login continues to establish a session cookie on valid credentials and reject invalid
  ones with the existing indistinct error, unchanged from
  [SPEC-0002](../../specs/SPEC-0002-user-authentication.md) #2–#3 — characterized by the
  existing `UserAuthenticationProviderTest` and `application` integration login tests,
  which must still pass unmodified in behavior.
- `UserAuthenticationProvider.authenticate(...)` runs on the blocking executor
  (`TaskExecutors.BLOCKING`), not the Netty event-loop thread — verified by a test that
  asserts the executing thread is not an event-loop thread during authentication.
