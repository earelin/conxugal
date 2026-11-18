# API contract conventions

`openapi.yaml` in this folder is the authoritative REST contract: every endpoint is
authored here **before** its controller exists, and CI enforces that the implementation
conforms ([ADR-0010](../architecture/0010-design-first-openapi-contract.md)). A path
written here is therefore the decision, not a description of one — get the naming right
in this file and the code follows.

## Stating a rule the server enforces

`format` is an annotation, not a constraint. A JSON Schema reader is free to ignore
`format: email` or `format: uri` entirely, and readers that do honour one disagree about
what it admits — so a field carrying only a `format` has stated nothing the server can be
held to, and the contract test will pick whichever reading the implementation does not
share. **A rule the server enforces is written as `pattern`, `maxLength`, `minimum` or an
`enum`**, and the implementation mirrors those characters exactly; a `format` alongside
them is documentation, and worth keeping for the better examples it gives a generator.

Write a `pattern` from literal character ranges where you can. `\s`, `\d` and `\w` are
read differently by ECMA-262 than by the server, so a pattern using them needs its
divergence spelled out in the field's `description` — see `CreateTermoRequest.name` for one
that does, and `CreateUserRequest.email` for one that avoids the need.

### A `pattern` is also read by the contract test's generator

Adding one changes how [ADR-0021](../architecture/0021-openapi-contract-testing-with-schemathesis.md)'s
suite generates data for that field, which is where three non-obvious traps live. All three
were paid for once on `CreateUserRequest.email`; re-check them whenever a `pattern` or the
`format` beside it changes.

- **Keep the `pattern` strictly inside what the `format` admits.** The suite validates with
  `jsonschema_rs`, whose email format is a full RFC check enforcing the 64- and 63-character
  limits. A pattern allowing more contradicts the format beside it, and the negative phase
  then generates a value it calls format-violating that the pattern permits. The first
  attempt at `CreateUserRequest.email` omitted the length bounds and the gate refused a
  174-character address. Worth re-proving by differential fuzzing when either half moves.
- **Keep the `pattern` a superset of the alphabet the generator draws from.** Positive
  examples are filtered through it, and too narrow a pattern discards too many — measured at
  95% surviving for `CreateUserRequest.email`. Narrowing its local part to the set the web
  client's `z.email()` accepts would drop that far enough to trip hypothesis's
  `filter_too_much` health check, which surfaces as a nightly failure with no explanation
  attached. This is the trap sitting in front of the obvious remedy for QA finding
  [L-7](../qa/2026-08-05-ui-qa-review.md) — read it before aligning the two rules.
- **Do not delete a `format` as redundant once a `pattern` covers it.** The `format` is what
  keeps the generator drawing from its own strategy rather than from the pattern, whose `$`
  means something different in Python than it does in Java. Removing `format: email` would
  make the suite send addresses ending in a line break and expect them accepted.

## Resource naming

Every path is mounted under `/api/`
([ADR-0006](../architecture/0006-reserved-api-url-prefix.md)). After that prefix — and
after the `/admin/` segment where present — the resource noun is **plural when the path
addresses the collection, singular when it addresses one element**
([ADR-0016](../architecture/0016-rest-resource-naming.md), the governing record).

| Path addresses | Noun | Example |
| --- | --- | --- |
| the collection — list | plural | `GET /api/organos` |
| the collection — create an element | plural | `POST /api/admin/organos/taxonomia/termos` |
| the collection — an operation on the whole set | plural | `POST /api/admin/organos/import` |
| the collection — a sub-resource of the whole set | plural | `GET /api/organos/taxonomia` |
| one element, by id | singular | `GET /api/organo/{id}` |
| one element's sub-resource | singular | `PUT /api/admin/organo/{id}/termo` |
| a singleton — no collection exists | singular | `GET /api/me` |
| an **action**, not a thing — last segment only | verb | `POST /api/admin/organos/import` |

Applying it:

- **A create takes the plural.** It has no id yet, so it acts on the collection.
- **A sub-resource follows the same rule for its own noun** — `.../{id}/termo`
  places the element in one term; a sub-collection is plural in turn, which is why the
  taxonomy's terms are `/api/organos/taxonomia/termos`.
- **A verb is allowed as the last segment, and only there**, when the request asks for
  something to *happen* rather than reading or writing state
  ([ADR-0020](../architecture/0020-actions-as-verbs-in-rest-paths.md)). The test is whether a
  noun would be a lie: `enabled` is a flag a user has and `importable` is a property an Órgano
  has, so both stay nouns even though writing them has effects; an import run addresses no
  such state, so `.../import` is a verb. Keep them rare — a path full of verbs is RPC
  wearing HTTP's clothes. A verb after a member path is fine too:
  `POST /api/admin/organo/{id}/contratos-menores/import`.
- **Multi-word nouns are kebab-case**: `system-status`.
- **Use the noun the domain already uses.** `organo`/`organos` because the aggregate is
  `OrganoDeContratacion`; `termo`/`termos` because it is `Termo`.
  The contract does not translate a term the code has chosen, so the model's language mix
  reaches the URLs deliberately.

Why it is worth the two spellings: a collection and its members occupy **disjoint**
namespaces, so a sibling resource can never collide with a member path. Without the rule,
`/api/organos/taxonomia` would be ambiguous — `taxonomia` is not an Órgano id, yet it sits
where `/api/organos/{id}` would go, forcing every client to special-case the literal and
reserving `"taxonomia"` against ever being an id. With members at `/api/organo/{id}`, the
plural namespace holds no ids at all, so `GET /api/organos/taxonomia` is unambiguous and
reads as what it is: a sub-resource of the whole collection.

### Before adding a path

Ask what the path addresses, not what the operation does — except for the terminal-verb case
above, which is the one place the operation decides. If the caller must supply an
identifier to name the thing being acted on, the noun is singular. A path that reaches one
element through a plural noun is a defect, and review is what catches it — nothing in
Micronaut enforces it.

<!-- distilled-from: FEAT-0004 @ 7402d8a -->
