---
feat: FEAT-0004
domain: backend
adrs: [0010, 0021]
status: done
depends_on: [TASK-0003]
---

# The shape an email must have to create an account

`POST /api/admin/users` accepts addresses that are not addresses. The contract test
([ADR-0021](../../architecture/0021-openapi-contract-testing-with-schemathesis.md)) found it:

```text
POST /api/admin/users — API accepted schema-violating request
Invalid component: in body - violates `format` at /properties/email
[201] Created: {"email":"û@N", …}
```

The defect is not a missing check. It is that **neither side of the contract states a rule**, so
each brought its own and they disagreed:

- `CreateUserRequest.email` declares `format: email` and nothing else. In OpenAPI 3.1 / JSON Schema
  2020-12 `format` is an **annotation**, not an assertion — it constrains nothing. The document
  therefore never said what an email is, and the contract test supplied its own, stricter answer.
- The endpoint validates with `@Email`, which Micronaut implements as a Hibernate Validator port.
  Its local-part class admits the whole non-ASCII range `U+0080`–`U+FFFF`, and its domain pattern
  admits a single dot-less label. By that definition `û@N` is a valid address, and `201` was the
  correct answer to the only rule the code knew.

This task writes one machine-checkable rule into the contract and implements exactly that regex at
the edge, so the two cannot drift again.

**No spec change.** [SPEC-0003](../../specs/SPEC-0003-administration-area.md) R7 says "an email"
and stays at that level; what counts as one is design detail and belongs here and in the contract.

**No ADR.** [ADR-0010](../../architecture/0010-design-first-openapi-contract.md) already makes the
document authoritative and accepts that a contract change is made in two places, and ADR-0021
already makes a contract-test violation a defect. Preferring `pattern` over `format` is a
consequence of ADR-0010, not a new decision — it belongs as a sentence in
[`docs/api/CLAUDE.md`](../../api/CLAUDE.md), where a contract author will meet it.

## Scope

- **The rule**, identical in ECMA-262, Python `re` and `java.util.regex`:

  ```text
  ^(?=[^@]{1,64}@)[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,63}$
  ```

  RFC 5322 dot-atom on the left, RFC 1035 labels on the right, a dotted domain, a letters-only
  top-level domain, and RFC 5321's lengths — 254 for the whole address, 64 for the local part, 63
  for each label. It is deliberately narrower than RFC 5322 and than `format: email`. Three
  properties are load-bearing and must survive future edits:
  - **Every character rule is a literal ASCII range** — no `\d`, `\w`, `\s` — so the regex
    dialects involved cannot read it differently. This is why the field needs none of the "the
    server reads this differently" caveat `CreateTermoRequest.name` has to carry. The one
    lookahead is a length bound, not a character rule.
  - **Strictly inside what `format: email` admits.** The contract test validates with
    `jsonschema_rs`, whose email format is a full RFC check — it enforces the 64- and 63-character
    limits. A pattern that allowed more would contradict the `format` beside it, and the negative
    phase would generate a value it calls format-violating that the pattern permits. This is not
    hypothetical: the first attempt at this fix omitted the length limits and the gate refused a
    174-character address with a 107-character local part. The containment is worth re-proving by
    differential fuzzing whenever either half changes.
  - **A superset of the local-part alphabet the generator draws from**, so filtering positive
    examples through the pattern rejects almost nothing — measured at 95% surviving. Narrowing the
    local part to the set the UI's `z.email()` uses would drop that far enough to risk
    hypothesis's `filter_too_much` health check.
- **`docs/api/openapi.yaml`, `CreateUserRequest.email` only** — add the `pattern` and
  `maxLength: 254`, and **keep `format: email`**. The format keyword is not decoration here: it is
  what keeps the contract test generating positive examples from its email strategy rather than
  from the pattern, whose `$` means something different in Python than it does in Java. Removing it
  as redundant would make the suite send addresses ending in a line break and expect them accepted.
- **The response schemas keep `format: email` and gain nothing.** `User.email`, `CurrentUser.email`
  and `CreatedUser.email` are validated against real rows, and the seeded accounts — `root@local`,
  `demo@local`, `contract-test@local` — have dot-less domains. A pattern there would make every
  read of the user list violate the contract on a freshly seeded instance. Reseeding instead is not
  a smaller change: `root@local` comes from a **versioned, already-applied** migration, so it
  cannot be edited without breaking Flyway validation on every deployed database. The asymmetry the
  contract is left stating is true — such accounts exist and are readable, and the API will not
  mint another one — and the schema description says so rather than leaving it to be read as an
  oversight.
- **`CreateUserRequest.java`** — `@Email` is **replaced** by `@Pattern` against a constant holding
  the same characters as the contract, plus `@Size(max = 254)`. Not stacked: the pattern is
  strictly stricter, so `@Email` could refuse nothing it accepts, and leaving in the annotation
  that certified `û@N` would invite a reader to think it still does some of the work. `@NotBlank`
  stays for consistency with the other validated request records, not for null-safety —
  `StrictBody` has already refused an absent or null value before validation runs.
- **The constant stays on the record.** It has one use site in one module, so `commons` is the
  wrong home twice over: [ADR-0013](../../architecture/0013-shared-commons-module.md) scopes that
  module to code shared across modules, and `Text`'s own contract is limits "beyond what any one
  field means" — an email pattern is the definition of one field.
- **The length bound is part of this task, not a separate one.** The field has no bound today while
  the column is `VARCHAR(255)`, so an over-long address passes validation and fails at the insert —
  a `500` where the contract promises a `400`. It is the same field and the same defect class, and
  it is what the nightly fuzz run would report next. `254` is the RFC 5321 addressable maximum; the
  column is left alone, the request being stricter than storage being the right direction.

**No `EmailAddress` value object.** `CreateUser.create` only hands the string to `findByEmail` and
the repository — there is no behaviour to attract onto a type. The rule is a transport-edge rule,
and moving it into the domain would create a second definition of a valid email, which is this
defect relocated rather than fixed. It would also break reading: `User.email` is loaded from rows
the pattern rejects, so a type enforcing it in its constructor could not load the seeded
administrator.

**No UI change.** `CreateUserModal.tsx` is already stricter than the new rule for exotic local
parts, which is a client-side courtesy rather than a contract violation.

## Acceptance criteria

- Creating an account with `û@N` is refused with a `400` problem document and the use case is never
  reached; the contract test's negative-data check passes against the operation.
  ([SPEC-0003](../../specs/SPEC-0003-administration-area.md) #6)
- A non-ASCII local part, a dot-less domain, a one-character top-level domain, a leading or doubled
  dot in the local part, a domain label ending in a hyphen, a local part over 64 characters, a
  domain label over 63, and an address ending in a line break are each refused the same way.
- An address sitting exactly on each length limit is accepted, so the bounds are the RFC's and not
  one character off it.
- An address longer than the column holds is refused with a `400` rather than failing at the insert
  as a `500`.
- Ordinary and awkward-but-legal addresses are still accepted — a dotted local part, a `+` tag, a
  multi-label domain, and a local part of RFC atext specials — and the created account can
  authenticate with the returned password. (SPEC-0003 #6)
- The contract's `pattern` and the constant compiled into the request record are the same
  characters; the response schemas carry no pattern and the seeded accounts stay readable.
- `scripts/openapi-lint.sh` and `scripts/docs-lint.sh` pass, and `scripts/contract-test.sh` passes
  both deterministically and under a randomly seeded run of at least 500 examples — the second run
  being what exercises the generator against the new pattern.

## Notes

QA finding **L-7** ([2026-08-05 UI review](../../qa/2026-08-05-ui-qa-review.md)) records the same
tension from the other side: the UI refuses `demo@local` while the API accepts it. This task
resolves the **disagreement** by moving the API to the UI's rule. It does not close the finding —
the system still seeds accounts of a shape it will not create — which is a seed-data question, and
a versioned-migration one, for its own task.
