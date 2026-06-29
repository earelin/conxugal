# Architecture Decision Records

One ADR records one architecturally significant decision (Nygard format).

## When to raise an ADR

A new module or bounded context, a new boundary or dependency, a datastore or public-contract
change, or a cross-cutting pattern. If implementing work that needs such a decision and no ADR
exists, **STOP and propose one first**.

## Template

```markdown
---
status: accepted          # proposed | accepted | superseded | deprecated
date: YYYY-MM-DD
spec: SPEC-NNN            # what motivated it (optional)
supersedes: null
superseded_by: null
---

# NNNN. Short decision title

## Status
Accepted

## Context
The forces at play and the problem being decided.

## Decision
The choice made, stated plainly.

## Consequences
+ Positive outcomes.
− Costs and tradeoffs accepted.
```

## Rules

- Number sequentially: `0001-…`, `0002-…`.
- ADRs are **immutable once `accepted`**. To revise, create a new ADR and set `supersedes`
  / `superseded_by` on both records. Never rewrite an accepted decision.
- Reference the governing ADR from the feature (`adrs:` frontmatter) and from sub-issue bodies.
