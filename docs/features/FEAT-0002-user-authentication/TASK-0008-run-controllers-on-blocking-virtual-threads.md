---
feat: FEAT-0002
domain: backend
adrs: [0005, 0011]
status: done
depends_on: [TASK-0003]
---

# Run all requests on the blocking executor (virtual threads), not the event loop

Governed by [ADR-0011](../../architecture/0011-blocking-io-virtual-threads.md) (blocking
I/O over virtual threads). Replaces the never-implemented
`TASK-0006-run-authentication-off-the-event-loop.md`: instead of dispatching
`UserAuthenticationProvider` off the event loop on its own, the whole server defaults to
the blocking executor via a global config switch, so the event loop never runs
`authenticate(...)` (or any other blocking call) in the first place. No change to the
authenticate use case, the password hashing algorithm, or the session/CSRF/`@Secured`
config wired in [TASK-0003](TASK-0003-security-config-session-auth.md).

## Scope
- `application/src/main/resources/application.yml`: set
  `micronaut.server.thread-selection: BLOCKING` and
  `micronaut.executors.blocking.virtual: true`. This is a single, global config change —
  no `@ExecuteOn` annotation on individual `@Controller`/`@Filter` classes is needed, and
  it covers `LoginController` and `ForbiddenController` (the only two `@Controller`
  classes in the codebase today) plus any future one.
- `UserAuthenticationProvider` keeps its existing `HttpRequestAuthenticationProvider`
  implementation — no need for `HttpRequestExecutorAuthenticationProvider`, since the
  request thread calling into it is already off the event loop under this config.
- No other domain/infrastructure port changes.

## Acceptance criteria
- Login continues to establish a session cookie on valid credentials and reject invalid
  ones with the existing indistinct error, unchanged from
  [SPEC-0002](../../specs/SPEC-0002-user-authentication.md) #2–#3 — characterized by the
  existing `UserAuthenticationProviderTest` and `application` integration login tests,
  which must still pass unmodified in behavior.
- `LoginController` and `ForbiddenController` handlers run on a virtual thread, not a
  Netty event-loop thread — verified by a test that asserts the executing thread is
  virtual (`Thread.isVirtual()`).
- Any future `@Controller` added to `application` inherits this default automatically,
  since the switch is global config rather than a per-class annotation.
