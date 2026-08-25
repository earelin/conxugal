---
feat: FEAT-0016
domain: frontend
adrs: [0004, 0015]
status: todo
depends_on: [TASK-0011]
---

# The CPV and state filters

R23's two narrowings, rendered — and the promise attached to them: only codes and states the year's
selection actually contains are offered, so **choosing one can never be the reason a list is empty**.

**The CPV filter closes a promise this family inherited.**
[SPEC-0005](../../specs/SPEC-0005-import-browse-contratos-menores.md) offers none, because the source
publishes no CPV for contratos menores, and defers CPV-based querying explicitly to SPEC-0008. This is
where that lands.

## Scope

- **Two `Select` controls**, both optional and both clearable, reading their options from the
  filter-options endpoint [TASK-0007](TASK-0007-the-licitacions-read-endpoints.md) publishes — one
  read per year, not per interaction.
- **The state control works on the code and reads the label.** ❗ Two states may share one label —
  codes 101 and 102 are both *Histórico* — so the option's **value is the code** and the label is only
  what is displayed. A control keyed on the label would merge two states the source distinguishes, and
  a fixture with distinct labels would never reveal it.

  **The vocabulary is the source's own** (R23): nothing here fixes a set of states, and an unseen one
  simply appears when a procedure in it is imported.
- **Both apply to the whole year's selection**, not to the page displayed — which is a property of the
  server read and a rule this task must not undermine by filtering client-side over a page in hand.
- **Applying or clearing either returns the reader to the first page**, through the same
  selection helper the year chooser and the sorts write through, so *any change to the selection drops
  the page* stays one rule in one place
  ([TASK-0013](TASK-0013-sorting-and-paging-over-the-selection.md)).
- **The options are a function of the year alone**, not of the other filter in effect. The feature
  README records the reading and its residual: with both filters chosen an empty list is reachable,
  and R23's promise strictly holds one filter at a time. The controls must therefore **stay populated
  and stay clearable** when the list is empty — the reader has to be able to undo the choice that
  emptied it.

**Out of scope:** free-text search over contract objects, which SPEC-0008's Scope rules out and for
which **no control appears**; any filter R23 does not name; and the server reads themselves.

## Acceptance criteria

- Filtering a year by a **CPV code** returns exactly the licitacións of that year carrying it —
  including one that carries it on **any** of its lotes, and one that carries it against the
  **procedure as a whole while having lotes**. A procedure carrying the code on three lotes appears
  **once**.
  ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #10, #33)
- Filtering by **state** returns exactly the licitacións in it, and filtering by code **101** does not
  return one in code **102** although both are labelled *Histórico*. (SPEC-0008 #33)
- **Only codes and states the year's selection actually contains are offered**, and choosing any
  single offered value yields a non-empty list — the property R23 attaches to the rule, asserted
  rather than assumed. (SPEC-0008 #33)
- The states offered are **the source's own**, with no set fixed in the client: a stub introducing an
  unseen state renders it without a code change. (SPEC-0008 #33)
- Narrowing applies to the **whole** year's selection: the count shown after filtering is the filtered
  year's count, and the first page holds the filtered year's first row rather than a subset of the
  page previously displayed. (SPEC-0008 #34)
- **Applying or clearing either filter returns the reader to the first page**, and changing the year
  clears neither control's availability nor leaves a stale option list. (SPEC-0008 #34)
- With **both** filters chosen and no rows matching, the list is empty, the count reads zero, and both
  controls remain populated and clearable — the reader can always undo the choice that emptied it.
  (SPEC-0008 #33)
- **No free-text search control appears anywhere in the section.**
- Both controls carry accessible names from `strings.licitacions`, and the acceptance spec drives them
  by role and name.
