---
status: accepted
date: 2026-08-23
spec: SPEC-0002
supersedes: null
superseded_by: null
---

# 0024. Passwords are hashed with Argon2id, stored in a self-describing encoded form

## Status
Accepted

Recorded retroactively. The choice was made and shipped while building user authentication;
it was never written down beyond the adapter's Javadoc, and surfaced when that feature's
design document was distilled. The decision and its consequences are as built — only the
record is new.

## Context
[SPEC-0002](../specs/SPEC-0002-user-authentication.md) R11 requires that passwords are never
stored in a form from which the original can be recovered, and R12 that they never appear in
logs, errors or responses.
[ADR-0005](0005-session-based-authentication.md) settled the mechanism around them —
server-side sessions, a server-rendered login form, an indistinct failure — but named the
storage format only as "salted hashes". That leaves the two questions that actually bind us:
which function, and what exactly goes in the `password_hash` column.

They bind us because the column is the migration surface. A password hash cannot be
recomputed from what is stored; the only way to change the function or its cost parameters is
to re-hash each password the next time its owner successfully signs in, which means the
verifier must keep understanding the old form for as long as any account has not signed in.
Whatever `password_hash` holds on the first day is a format we are committed to reading
indefinitely.

Two further forces:

- The `PasswordEncoder` port carries a third method, `matchAgainstDummyHash`, which exists so
  that an unknown email costs the same work as a wrong password (SPEC-0002 R3). Whatever
  function is chosen is therefore run on **every** failed login, not only on the ones that
  reach a real user's hash.
- Under [ADR-0011](0011-blocking-io-virtual-threads.md) every request, login included, runs
  on a virtual thread. A deliberately slow, memory-hard function is exactly the kind of work
  that decision was taken to accommodate — but also the kind that can pin a carrier thread.

## Decision
Passwords are hashed with **Argon2id**, via BouncyCastle's `Argon2BytesGenerator`
(`org.bouncycastle:bcprov-jdk18on`), at 64 MiB of memory, 3 iterations, parallelism 1, a
32-byte hash over a 16-byte `SecureRandom` salt, Argon2 version 1.3 — OWASP's current guidance
for a server-side interactive login. Verification compares with `MessageDigest.isEqual`, in
constant time.

We deliberately do **not** use bcrypt or PBKDF2. Both are memory-cheap, so the cost of a
guessing attack falls with GPU and ASIC hardware in a way a memory-hard function resists;
bcrypt additionally truncates at 72 bytes. Argon2id is the current recommendation for new
systems, and there is no legacy corpus of hashes here to be compatible with.

The stored form is **self-describing**:

```text
memory:iterations:parallelism:base64(salt):base64(hash)
```

The parameters are part of the value rather than compiled into the verifier, so re-tuning the
cost is a change to `encode` alone — every hash already in the table keeps verifying against
the parameters it was created with, and no account is locked out by a tuning change. This is a
project-local encoding, not the PHC/MCF string (`$argon2id$v=19$m=…`) that most Argon2 tooling
reads and writes.

The single implementation is
`server/infrastructure/src/main/java/gal/conxugal/infrastructure/crypto/Argon2idPasswordEncoder.java`,
the driven adapter for the `domain` port — the domain never learns which function is in use.

## Consequences

### Pros
- **Memory-hard by default.** 64 MiB per verification makes parallel offline guessing
  expensive in a way an iteration count alone does not, and that cost lands on every attempt
  including the dummy-hash path a failed login takes.
- **Cost is re-tunable without a migration.** Raising memory or iterations changes new hashes
  only; old ones stay verifiable, so the parameters can follow hardware without a flag day or
  a forced password reset.
- **The choice is confined to one adapter.** `PasswordEncoder` is a domain port
  ([ADR-0002](0002-hexagonal-architecture.md)), so replacing the function touches one class
  and no domain, application or test code that talks in terms of the port.

### Cons
- **The encoded form is ours alone.** No external tool, framework migration or operational
  script can read `password_hash`, because it is not the PHC string the ecosystem expects.
  Anything that ever needs to — an import from, or an export to, another system — has to be
  taught this format or convert on the way through.
- **Changing the *function* still costs a migration**, even though changing its parameters
  does not: the encoding records the cost parameters but not the algorithm, so an eventual
  move off Argon2id needs a discriminator added to the format and a re-hash-on-login path
  that recognises both. This decision defers that work; it does not remove it.
- **Each verification holds 64 MiB.** A burst of concurrent logins is a memory spike, not just
  CPU, and the dummy-hash rule means invalid emails cost the same as valid ones — so an
  unthrottled login endpoint is a cheap way to make the server allocate. SPEC-0002 puts
  brute-force throttling explicitly out of scope, which leaves this unmitigated for now.
- **Virtual-thread pinning is unverified.** [ADR-0011](0011-blocking-io-virtual-threads.md)
  already flags that this encoder needs checking under load to confirm it does not pin its
  carrier thread pathologically; recording this decision does not discharge that.

<!-- distilled-from: FEAT-0002 @ 6d8a9f4 -->
