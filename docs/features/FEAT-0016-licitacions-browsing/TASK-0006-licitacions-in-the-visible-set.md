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
- **One test edit, which is not optional.** ❗
  `JdbcContratoMenorVisibleOrganosIntegrationTest` injects `OrganosWithVisibleContracts`
  **singularly**; a second bean makes that a `NonUniqueBeanException` and the test fails at context
  startup rather than on an assertion. The injection point is qualified to the contratos menores
  implementation. The production path is unaffected, because it was already injecting the `List`.

**Out of scope:** the tree, the name search and the picker — all
[FEAT-0012](../FEAT-0012-organos-visible-set-and-browse/README.md)'s and unchanged — and the
`licitacions` family entry on the Órgano member read, which is
[TASK-0007](TASK-0007-the-licitacions-read-endpoints.md)'s.

## Acceptance criteria

Integration-tested against PostgreSQL, over a catalogue holding each case:

- An Órgano with **visible licitacións and no contratos menores** is in the visible set, and is
  reachable from the taxonomy tree and by name search. (SPEC-0008 #26 reachability half)
- An Órgano with **visible contratos menores and no licitacións** is still in it, unchanged — the
  union adds and never narrows. (SPEC-0008 #26 reachability half)
- An Órgano with **both** appears **once**. (SPEC-0008 #26 reachability half)
- An Órgano holding **only** licitacións that are withdrawn, or **only** ones with no interpretable
  publication date, is **not** in the set — indistinguishable from one holding nothing at all.
  (SPEC-0008 #36, #37)
- An Órgano whose licitacións become visible — a later import supplying an interpretable date —
  enters the set on the next read with no administrator action. (SPEC-0008 #36)
- The existing contratos menores visible-set tests **still assert exactly what they asserted before**
  — their **one** permitted change being the qualifier on
  `JdbcContratoMenorVisibleOrganosIntegrationTest`'s injection point, without which the context does
  not start. No production file of that family is edited.
