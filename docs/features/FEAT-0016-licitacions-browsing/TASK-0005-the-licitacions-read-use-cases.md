---
feat: FEAT-0016
domain: backend
adrs: [0002]
status: todo
depends_on: [TASK-0003, TASK-0004]
---

# The three read use cases, and the section that may not exist

The domain side of the licitacións section: what a reader may ask for, and what the section says
about itself before a single licitación is fetched.

## Scope

- **`ListLicitacions`** — answers one page of one selection in one ordering. It checks the Órgano
  exists first, raising the **already-published** `OrganoNotFoundException` rather than a new one,
  then delegates. It **applies no default and corrects nothing**: the year is required by the type,
  the sort key and direction arrive decided, and a page beyond the end is answered rather than
  clamped.
- **`OfferLicitacionFilters`** — answers a selection's `LicitacionFilterOptions`. Same Órgano check,
  same refusal to invent anything.
- **`DescribeLicitacionsSection`** — answers `Optional<LicitacionsSection>`: the years, `partial` and
  `updating`.
- **`LicitacionsSection`** — the years, `partial`, `updating` — **refusing an empty year list** in
  its constructor, on `ContratosMenoresSection`'s precedent, so *the section exists* and *it has
  years* cannot disagree.

  It is **duplicated rather than shared** with `ContratosMenoresSection`, which it matches exactly
  today. FEAT-0015 set the precedent when it declined to share `LicitacionImportState` with its
  sibling, on R4's rule that neither family's progress is ever read as the other's — and the two are
  already diverging, since this family's state carries no covered-through instant. **This is the most
  arguable call in the feature**, and a reviewer weighing a shared `ContractFamilySection` should
  meet it here rather than infer it.

### How the two statements are derived

| Statement | Derived from | Note |
| --- | --- | --- |
| the years | [TASK-0004](TASK-0004-year-cpv-and-state-facets.md)'s year facets | **newest first**; empty means no section at all |
| `partial` | `LicitacionImportStateRepository` — `state != COMPLETE` | an Órgano with **no row** is `NEVER_STARTED`, which is not complete, so it reads **partial** |
| `updating` | `OrganoDeContratacion.eligibleForImport()` — active **and** marked | R3 says there is no mark other than SPEC-0005 R4's, so this is the whole of it |

**`partial` and `updating` are two booleans, not one status.** They are orthogonal — an Órgano
unmarked halfway through its initial import is both — and collapsing them would force a lie in
exactly that case.

**Where this differs from contratos menores is where the fact lives, not what it is.**
`ContratosMenoresImportStatus` rides on the `OrganoDeContratacion` aggregate; this family's state is
a table of its own behind its own port, and `licitacion_import_state` carries **no covered-through
instant** — FEAT-0015 left it out because this family's incremental mode is driven by `modificado`
ordering rather than by a window measured from a T₀. That absence changes nothing here: **`partial`
was never derived from a covered-through instant in either family.**

**Three reads, no shared snapshot**, which is the same trade FEAT-0011 recorded and reached the same
conclusion on: the worst outcome is a section reporting itself more cautiously than it needed to.

### What a `USER` may learn about the import, and what they may not

R1 keeps the mark `ADMIN`-only and SPEC-0007 R15 keeps Órgano-side import facts out of shared views,
while R26 obliges **this** section to tell any reader that what it shows is partial and that the
Órgano is no longer being updated. They are reconciled exactly as FEAT-0011 reconciled them, and the
rule is this task's to hold:

- **both flags are produced only on the branch where a section exists** — that is, only for an Órgano
  that already holds a visible licitación. R26's protected question, *is this Órgano imported at
  all*, is about Órganos with **no** section, and those return no flags because they return nothing;
- **neither flag is added to any catalogue read.** Nothing about the import leaks onto the list of
  every Órgano;
- **`updating` says *this data is still being refreshed***, not *an administrator marked this*. That
  the two coincide today is an implementation fact, not a contract.

**Out of scope:** every HTTP shape ([TASK-0007](TASK-0007-the-licitacions-read-endpoints.md)), the
visible set ([TASK-0006](TASK-0006-licitacions-in-the-visible-set.md)), and any change whatever to
the contratos menores section or its port.

## Acceptance criteria

- An Órgano with **no visible licitación** — none stored, all withdrawn, or all undated — yields
  **no section**, and therefore **no `partial` and no `updating`**. All three cases are
  indistinguishable to a reader.
  ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #36, #37)
- An Órgano with visible licitacións and **no** `licitacion_import_state` row reads **`partial`**,
  which is what an Órgano marked before this family existed is. (SPEC-0008 #37)
- One at `INCOMPLETE` reads `partial`; one at `COMPLETE` does not. Its **contratos menores** state is
  not read and does not affect either flag — asserted with the two families in opposite states, which
  is R4's requirement that neither is read as the other's. (SPEC-0008 #5 read half — *neither family's completion is read as the other's*; #37)
- An Órgano that is **unmarked**, and one that is **inactive**, each read `updating` false while
  keeping their years and their section. An Órgano unmarked mid-import reads **both** `partial` and
  not-`updating`. (SPEC-0008 #6 display half, #37)
- `LicitacionsSection` **refuses** an empty year list, so *the section exists* and *it offers years*
  cannot disagree. (SPEC-0008 #32)
- The years answered are newest first, and the **first** is the year the section opens on.
  (SPEC-0008 #32)
- `ListLicitacions` and `OfferLicitacionFilters` both raise `OrganoNotFoundException` for an unknown
  Órgano id — the existing exception, not a new one.
- `ListLicitacions` applies **no** default year, sort or direction, and clamps **no** page. Unit
  tests stub the port and assert the arguments reach it unchanged.
