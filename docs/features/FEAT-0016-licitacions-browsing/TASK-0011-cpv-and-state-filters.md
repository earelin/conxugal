---
feat: FEAT-0016
domain: frontend
adrs: [0003, 0004, 0015, 0018]
status: todo
depends_on: [TASK-0010]
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
  read per year, not per interaction — **and its WireMock mapping**, which no other task claims.

  ❗ **A CPV option may carry no description**, since `cpv.description` is nullable and it is unsettled
  whether the import populates it at all ([TASK-0004](TASK-0004-year-cpv-and-state-facets.md)). The
  control must read as an option list of **codes**, with the description as an adornment where one
  exists — not a list of descriptions that degrades to blanks.
- **The state control works on the code and reads the label.** ❗ Two states may share one label —
  codes 101 and 102 are both *Histórico* — so the option's **value is the code** and the label is only
  what is displayed. A control keyed on the label would merge two states the source distinguishes, and
  a fixture with distinct labels would never reveal it.

  **The vocabulary is the source's own** (R23): nothing here fixes a set of states, and an unseen one
  simply appears when a procedure in it is imported.
- **Both apply to the whole year's selection**, not to the page displayed — which is a property of the
  server read and a rule this task must not undermine by filtering client-side over a page in hand.
- **Applying or clearing either returns the reader to the first page**, through the same
  selection module the year chooser and the sorts write through, so *any change to the selection drops
  the page* stays one rule in one place
  ([TASK-0009](TASK-0009-year-chooser-and-section-state.md) owns it).
- **The options are a function of the year alone**, not of the other filter in effect. The feature
  README records the reading and its residual: with both filters chosen an empty list is reachable,
  and R23's promise strictly holds one filter at a time. The controls must therefore **stay populated
  and stay clearable** when the list is empty — the reader has to be able to undo the choice that
  emptied it.

**Out of scope:** free-text search over contract objects, which SPEC-0008's Scope rules out and for
which **no control appears**; any filter R23 does not name; and the server reads themselves.

## Acceptance criteria

**These are the client's obligations.** *Which* rows a filter returns is proved against PostgreSQL by
[TASK-0003](TASK-0003-paged-ordered-counted-reads.md) and
[TASK-0004](TASK-0004-year-cpv-and-state-facets.md); asserting it again against a stub would only
prove the stub. What can go wrong **here** is what the control sends and what it offers.

- ❗ **The state option's *value* is the code and its *label* is only displayed.** Choosing the entry
  labelled *Histórico* that carries code **102** puts `state=102` on the wire — not `102`'s label, not
  the first entry sharing that label, and not an index into the list. Two entries labelled *Histórico*
  are two distinct choices that send two distinct values.

  This is the one thing this task can get wrong that nothing else would catch: a label-keyed control
  passes every other criterion here, and the defect surfaces only as *the wrong rows*, which a reader
  has no way to attribute. ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #33)
- The **CPV** option's value is the published code, sent unaltered — not the description, and not a
  normalised form. (SPEC-0008 #33)
- **Only the codes and states the filter-options read returned are offered** — the control adds
  nothing, hides nothing, and fixes no vocabulary of its own, so a stub introducing an unseen state
  renders it without a code change. (SPEC-0008 #33)
- A state the read returned with **no label** is offered under its **code** rather than as a blank
  entry. (SPEC-0008 #33)
- **Applying or clearing either filter puts the reader on page 1** — through
  [TASK-0009](TASK-0009-year-chooser-and-section-state.md)'s module, so what is asserted here is that
  the control writes through it rather than around it. Changing the year leaves neither control
  disabled nor holding a stale option list. (SPEC-0008 #34 re-page half)
- Narrowing re-reads with the filter **on the wire**, so the count and the first page are the server's
  answer for the narrowed selection and **nothing is filtered client-side** over a page already in
  hand. Asserted on the request the stub receives. (SPEC-0008 #34)
- With **both** filters chosen and no rows matching, the list is empty, the count reads zero, and both
  controls remain populated and clearable — the reader can always undo the choice that emptied it.
  (SPEC-0008 #33)
- **No free-text search control appears anywhere in the section.**
- Both controls carry accessible names from `strings.licitacions`, and the acceptance spec drives them
  by role and name.
