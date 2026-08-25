---
feat: FEAT-0016
domain: backend
adrs: [0002]
status: todo
depends_on: [TASK-0002]
---

# Licitacións join the visible set of Órganos

The **reachability half** of [SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #26 — its second half, *and its licitacións are viewable*, is tasks 7 and 9–11's. An Órgano that
publishes licitacións and **no** contratos menores becomes reachable to a `USER` — from the taxonomy
tree and by name search — on the strength of this family alone.

**This is the case SPEC-0005 R15's family split was written to anticipate, reached for the first
time.** SPEC-0004 R9 defines the visible set as every Órgano with at least one visible contract **in
any family**, with each contract spec defining *visible* for its own; R25 is this spec's definition,
and this task is where it enters that union.

## Scope

- **A second `OrganosWithVisibleContracts` implementation**, for licitacións, answering *which of
  these Órgano ids hold at least one visible licitación* — the same semi-join shape
  `JdbcContratoMenorRepository` already uses, over
  [TASK-0003](TASK-0003-paged-ordered-counted-reads.md)'s visibility rule.

  ❗ **It has no year to scope by**, so it must spell the date test itself: `publication_date IS NOT
  NULL` (or the generated year column, equivalently) **and** `withdrawn = FALSE`. The page and the
  count get the date test free from their year equality; this read does not, and FEAT-0011 records
  the same asymmetry for the same reason.
- **Nothing else in production.** `ListVisibleOrganos` already injects
  `List<OrganosWithVisibleContracts>` and unions the answers, so this is genuinely one bean. **No file
  in `domain/organo` or `domain/contrato` is edited**, and a task author who finds themselves editing
  `ListVisibleOrganos` has misread it.
- **Two test edits, neither optional.** ❗ `JdbcContratoMenorVisibleOrganosIntegrationTest:65` injects
  `OrganosWithVisibleContracts` **singularly**, and `OrganosControllerIntegrationTest:52` `@MockBean`s
  the interface. A second bean makes the first a `NonUniqueBeanException` at context startup and makes
  the second's comment false.

  ❗ **"Qualify the injection point" is not executable as written**, and an earlier draft of this task
  said it was: `JdbcContratoMenorRepository` carries **no `@Named`**, so there is no qualifier to
  select it by — and adding one is a production edit this task forbids itself. The workable fix is to
  inject the **concrete adapter type** at the singular site, and to give the `@MockBean` site either a
  qualifier or a `List`. The production path is unaffected either way, because `ListVisibleOrganos`
  was already injecting the `List`.

**Out of scope:** the tree, the name search and the picker — all
[FEAT-0012](../FEAT-0012-organos-visible-set-and-browse/README.md)'s and unchanged — and the
`licitacions` family entry on the Órgano member read, which is
[TASK-0007](TASK-0007-the-licitacions-read-endpoints.md)'s.

## Acceptance criteria

Integration-tested against PostgreSQL. **This task owns one bean answering one question**, so its
criteria are about that bean's SQL. The **union** is `ListVisibleOrganos`'s and is already proved by
`ListVisibleOrganosTest` over two mocked families; the **tree and the name search** are FEAT-0012's and
are client-side. Asserting either here would test somebody else's code.

- ❗ **The predicate is `publication_date IS NOT NULL AND withdrawn = FALSE` and nothing more.** An
  Órgano holding a licitación with **no award**, one whose award **names nobody**, and one with
  **neither an awarded amount nor a budget** is in the set for each — this is the trap the task exists
  to catch, and `VISIBLE_ORGANOS_SQL` next door carries the two extra conjuncts that would break it.
  Without this criterion the copied-across implementation passes everything else.
  ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #20, #26 reachability half)
- An Órgano holding **only** withdrawn licitacións, and one holding **only** undated ones, are both
  **absent** — indistinguishable from one holding nothing. (SPEC-0008 #36)
- An Órgano holding **no licitacións at all** is absent, and one holding one visible licitación is
  present — over a candidate set that includes both, since the bean answers *which of these*.
  (SPEC-0008 #26 reachability half)
- An Órgano whose licitación **becomes** visible, a later import supplying an interpretable date,
  enters the set on the next read with no administrator action. (SPEC-0008 #36)
- The bean is **discovered as a second `OrganosWithVisibleContracts`**, so `ListVisibleOrganos`
  receives two. That is the whole of this task's contribution to the union, and it is the one union
  fact worth asserting here. (SPEC-0008 #26 reachability half)
- ❗ **Every shipped injection point of `OrganosWithVisibleContracts` still resolves.**
  `JdbcContratoMenorVisibleOrganosIntegrationTest:65` injects it **singularly** and
  `OrganosControllerIntegrationTest:52` `@MockBean`s it with a comment naming *"the adapter bean"* in
  the singular — both break the moment a second exists.

  **The fix is in the tests, not in production.** `JdbcContratoMenorRepository` carries no `@Named`
  and this task must not add one, so the singular site is re-pointed at the concrete adapter type
  rather than the interface, and the `@MockBean` site takes the qualifier or becomes a `List`.
  Whichever, no production file of either family is edited and both suites assert what they asserted
  before.
