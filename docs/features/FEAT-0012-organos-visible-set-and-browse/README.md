---
spec: SPEC-0004
adrs: [0002, 0003, 0004, 0005, 0006, 0008, 0010, 0012, 0015, 0016, 0018, 0020, 0021, 0023]
status: draft
---

# FEAT-0012. Órganos: the visible set, and a `USER`'s routes into it

## Goal
Give a `USER` the two ways into an Órgano that
**[SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md)** now allows them —
the **read-only taxonomy tree** (R9) and a **name search** (R19) — over the **visible set** R9
scopes them to, and scope the catalogue at the endpoint that serves it rather than in the browser
that draws it.

It delivers **R9's browse tree** whole, **R19** whole, the **narrowing of `GET /api/organos`** R1
and R2 now require, and the move of the administration area onto the read that still carries the
whole catalogue.

**It closes the oldest open criterion in the repo.** SPEC-0004 **#9** — *a user can browse the
taxonomy tree and select an Órgano from it* —
[FEAT-0007](../FEAT-0007-organos-taxonomia-classification/README.md) built the data for and
declined to render, recording that it belonged to "the contract-querying feature". SPEC-0004
recorded the deferral beside the criterion. This is that feature, and #9's deferral note can be
retired with it.

**Why it is its own feature rather than part of
[FEAT-0011](../FEAT-0011-contratos-menores-browsing/README.md).** Both were drafted together and
split on review. Everything here traces to **SPEC-0004** — its criteria #9 and #19–#26 — while
FEAT-0011 traces to SPEC-0005; `spec:` is singular in every feature in this repo, so a task author
following `feat:` → `spec:` from a name-search task would otherwise land in a spec with no
criterion to satisfy. The work is different in kind too: this feature **changes a shipped,
contract-tested endpoint and a shipped admin surface**, where FEAT-0011 adds new reads over new
tables.

The two meet at exactly one point, and it runs one way: **the tree and the search open an Órgano's
contracts page**, which FEAT-0011 builds. That is SPEC-0005 R14's requirement, satisfied from this
side, so this feature's browse task waits on FEAT-0011's contracts page and nothing else crosses.

The design sits in the hexagonal server of
**[ADR-0002](../../architecture/0002-hexagonal-architecture.md)**, with the catalogue read scoped
by the decision recorded in
**[ADR-0023](../../architecture/0023-visible-set-as-a-contract-side-query.md)**. REST lives under
the reserved `/api/` prefix
(**[ADR-0006](../../architecture/0006-reserved-api-url-prefix.md)**), named per
**[ADR-0020](../../architecture/0020-actions-as-verbs-in-rest-paths.md)** and the
**[ADR-0016](../../architecture/0016-rest-resource-naming.md)** it supersedes, authored
contract-first (**[ADR-0010](../../architecture/0010-design-first-openapi-contract.md)**), verified
against the running instance by
**[ADR-0021](../../architecture/0021-openapi-contract-testing-with-schemathesis.md)**, guarded by
session security (**[ADR-0005](../../architecture/0005-session-based-authentication.md)**) and
carrying the rate-limit contract of
**[ADR-0012](../../architecture/0012-rate-limit-http-contract.md)**. The UI is the React Router SPA
(**[ADR-0003](../../architecture/0003-react-router-ui-served-by-backend.md)**) built with Vite +
Mantine (**[ADR-0004](../../architecture/0004-ui-stack-vite-mantine.md)**) in the feature-based
layout of
**[ADR-0015](../../architecture/0015-frontend-feature-based-shared-core-modularization.md)**, and
its journeys are proved against a stubbed API per
**[ADR-0018](../../architecture/0018-frontend-acceptance-tests-against-a-stubbed-api.md)**.

> **One prerequisite decision is recorded, not taken here.**
> **[ADR-0023](../../architecture/0023-visible-set-as-a-contract-side-query.md)** decides how the
> visible set is derived across the domain boundary — SPEC-0004's *Decisions left open* names it
> ADR-grade and declines to settle it. It is `proposed`; **task 1 must not be picked up until it is
> accepted**, because building one answer is how that decision gets taken by accident. Tasks 2–4 do
> not depend on it.

## Scope
- **Application (driving):** the **narrowing of the shipped `GET /api/organos`** to the visible set,
  with its OpenAPI description rewritten and its integration test reshaped.
- **UI — the browse section (`/organos`, any authenticated user):** the read-only taxonomy tree over
  the narrowed catalogue and FEAT-0007's taxonomy read, holding unclassified Órganos at its root and
  pruning branches left empty; the name search beside it; and a nav entry of its own. No management
  control of any kind, and **no catalogue list**.
- **UI — the administration area:** FEAT-0007's taxonomy tree, classification worklist and Órgano
  table moved onto `GET /api/admin/organos`, so the management surfaces keep the whole catalogue
  after the narrowing.

**Out of scope (owned elsewhere):**
- **Everything about contracts.** The Órgano contracts page, the family split, the contratos
  menores section and its paging are
  [FEAT-0011](../FEAT-0011-contratos-menores-browsing/README.md)'s. This feature renders no contract
  and reads no contract table — the visible-set predicate is answered behind ADR-0023's port, not by
  this feature knowing what a contract is.
- **The taxonomy and the catalogue themselves** — [FEAT-0006](../FEAT-0006-organos-catalogue-import/README.md)
  imports the catalogue and [FEAT-0007](../FEAT-0007-organos-taxonomia-classification/README.md)
  owns the terms, the placements, the two reads and every management control. This feature adds one
  `USER` surface over them and moves the admin one onto a different read; it changes no taxonomy
  rule.
- **The `ADMIN` half of R19.** R19 says an administrator searching the administration area searches
  the whole catalogue. The admin section has no search today, and adding one is not what this
  feature is for; SPEC-0004 #26's second sentence therefore stays open, and is recorded below rather
  than quietly assumed.
- **A server-side search.** R19 is answered client-side over a list already held — see *The search
  costs a component, not a contract*.

## Design

### Hexagonal placement ([ADR-0002](../../architecture/0002-hexagonal-architecture.md))
```mermaid
flowchart LR
    subgraph application["application (driving)"]
        organosApi["GET /api/organos (narrowed)"]
        adminApi["GET /api/admin/organos (unchanged)"]
        browseUi["browse: tree + name search"]
        adminUi["administration area (moved read)"]
    end
    subgraph domain["domain"]
        listUc["ListOrganos + visible-set scoping"]
        visiblePort["has-visible-contracts port (ADR-0023)"]
        organoRepo["OrganoRepository"]
    end
    subgraph infrastructure["infrastructure (driven)"]
        contratoAdapter["contrato-side implementation"]
    end
    application --> domain
    infrastructure --> domain
    listUc --> visiblePort
```

### The scoping is the endpoint's, not the client's
SPEC-0004 R9 keeps every Órgano outside the **visible set** off the browse surfaces *and* out of
what is served to them. The scoping is therefore **by path**, and it needs no new endpoint:

| Path | Role | Returns | Built for |
| --- | --- | --- | --- |
| `GET /api/organos` | authenticated | **the visible set only** | the browse tree and search |
| `GET /api/admin/organos` | `ADMIN` | the whole catalogue | the administration area |

- **`GET /api/organos` is narrowed, not duplicated.** It stops returning the whole catalogue and
  returns the visible set instead. Its shape, its ordering under the Galician collation and its
  `termoId` are untouched, so the tree is built exactly as before — from a shorter list.
- **`GET /api/admin/organos` already exists**, built by
  [FEAT-0009](../FEAT-0009-contratos-menores-initial-import/README.md), `ADMIN`-gated, and already
  carrying the placement alongside the import mark — its contract says in as many words that it
  exists so "the administration UI swaps one read for the other rather than issuing both". This
  feature is what makes that swap necessary, and the endpoint that receives it is already shipped.
- **Nothing intersects anything client-side**, so there is no id-list endpoint, no two-read join,
  no filter step in the builder, and no window in which two reads disagree. A surface asks the
  endpoint that means what it wants.
- **The visible set is family-neutral by construction**, so a licitación-only Órgano appears the day
  that family lands: the predicate is *has a visible contract* rather than *has a visible contrato
  menor*. Today only `contrato_menor` can satisfy it, and this feature does not know that — it asks
  ADR-0023's port.

**What this costs is a change to shipped, contract-tested behaviour, and it is not small.**
`GET /api/organos` is `@Secured(IS_AUTHENTICATED)`, returns all 429 rows, has an integration test
named `user_reads_every_organo_with_its_name_state_and_placement`, and `openapi.yaml` still states
*"reading the catalogue is not an administration capability"* — the exact rule SPEC-0004 R1 now
revokes. Task 1 carries the narrowing, the contract rewrite and the test.

**Task 2 must land with or before it.** FEAT-0007's admin section reads `GET /api/organos` for its
taxonomy tree, its Órgano table and its unclassified worklist. Narrowed first, those surfaces
silently lose every Órgano without contracts — most of the catalogue, and precisely the ones an
administrator opens the section to file. Nothing errors, which is what makes it worth stating as an
ordering constraint rather than leaving to whoever picks the tasks up.

### The tree is a `USER`'s only view of the catalogue, so it must be exhaustive within the set
- **The read-only taxonomy tree** (R9), assembled in the browser from the narrowed
  `GET /api/organos` and FEAT-0007's `GET /api/organos/taxonomia` by the **same pure builder the
  admin section already uses**. It offers a `USER` no control at all — no create, rename, move,
  delete or reassign — which is #9's second clause and the reason it is a *view* rather than the
  admin tree with its buttons hidden.
- **Unclassified Órganos render at the root, beside the root terms.** SPEC-0004 R18 leaves every
  newly imported Órgano unclassified, so a tree of classified Órganos only would leave an Órgano
  holding a million contracts reachable from nowhere — which SPEC-0005 R14 forbids and #19 tests.
  The builder already computes that bucket; what changes is that it renders **at the root** rather
  than as the admin section's separate worklist.
- **Empty branches are pruned, recursively.** A term whose whole subtree holds no Órgano of the
  visible set is omitted (#22) — a *recursive* condition, not a per-term one, since a parent whose
  own Órganos are all absent still shows when a descendant has one. A single-level check would prune
  exactly the intermediate terms a deep taxonomy is made of.

  The prune belongs to the **browse** section's call, **not inside the shared builder**: the admin
  section renders terms that are legitimately empty, and a builder that dropped them would delete
  newly created terms from the management tree the moment they were made.

### The search costs a component, not a contract
R19 is answered **client-side, over the list the tree is already built from**. The catalogue is a
few hundred rows and the browse section holds it, so the search is a filter over data in memory: no
request per keystroke, no debounce against the server, no query endpoint, and **no second definition
of what matches** that could disagree with the tree.

That agreement is a requirement, not a convenience — #26 requires the search to offer exactly what
the tree shows, in both directions, and sharing one list is what makes it true by construction
rather than by two implementations staying in step.

- **Matching is partial, case- and accent-insensitive** (R19), which is not free in a browser: a
  naïve `toLowerCase().includes()` fails `avila` → `Ávila`, and this catalogue is full of accents.
  The comparison normalises both sides — decomposing and stripping diacritics — in a pure function
  unit-tested from both sides, beside the tree builder.
- **A blank input offers nothing** (#24). This is the boundary that keeps R19 from reinstating the
  `USER` catalogue list R2 removes, and it is a rule about the component, not about the data.
- **Each entry states whether the Órgano is inactive** (#23), since Órganos differing only by a
  trailing qualifier are ordinary here.

Were the catalogue ever to outgrow being held client-side, the search would need a server-side read
— a decision for whichever feature meets that limit, against a measurement, not one to pre-empt at
a few hundred rows. SPEC-0004 R20 takes no latency budget for either surface for the same reason.

### UI ([ADR-0003](../../architecture/0003-react-router-ui-served-by-backend.md), [ADR-0004](../../architecture/0004-ui-stack-vite-mantine.md), [ADR-0015](../../architecture/0015-frontend-feature-based-shared-core-modularization.md))

One new route, authenticated, in Galician (SPEC-0001 AC7):

| Route | Slice | What it is |
| --- | --- | --- |
| `/organos` | `features/organos` | the browse section: read-only tree, unclassified at its root, plus the name search |

**The browse section joins the existing `features/organos` slice**, and that is a constraint rather
than a preference: `eslint-plugin-boundaries` forbids one slice importing another, and the browse
section needs the *same* pure tree builder (`taxonomiaTree.ts`) and the *same* endpoints the admin
surface already uses. Promoting the builder to `shared/` to justify a second Órgano slice would move
code with no second owner.

**Two things must be kept apart inside that one slice**, because the surfaces genuinely differ now:

- the **admin** surface reads `GET /api/admin/organos` and prunes nothing;
- the **browse** surface reads the narrowed `GET /api/organos` and prunes recursively.

`ui/src/features/organos/` is today 39 files of `ADMIN` management — modals, pickers, import
controls. [ADR-0015](../../architecture/0015-frontend-feature-based-shared-core-modularization.md)
records that a static barrel import "pulls that whole slice into the eager chunk", and the
repository has already code-split the UI by route once for exactly this reason. The browse route
must not drag the admin components into its chunk; task 3 says how.

**A `USER`-visible nav entry** joins the ungrouped primary section of `ui/src/app/nav.ts` — the
first one that is not Home or About. The admin `/administracion/organos` entry stays where it is:
they are two surfaces over one catalogue, and merging them would put management controls in front
of a `USER` (#9).

**The two entries cannot share a label**, and one is taken: `strings.nav.organos` is already
`'Órganos'` for the admin entry, and an `ADMIN` sees both at once. The browse entry needs its own
label naming what it is *for* — the reader goes there to reach contracts, not to administer a
catalogue — and the admin entry keeps the name it ships with, since renaming a shipped surface to
make room is a cost paid by the wrong side.

All copy belongs in `ui/src/shared/lib/strings.ts` under the slice's namespace, which is the
pattern that module already uses, not in a strings module of its own.

## Sequencing (tasks, one small change each)
Each task names what it depends on; nothing depends on a task numbered after it.

1. **Narrow `GET /api/organos` to the visible set** *(backend, OpenAPI-first)*: the read returns
   only Órganos with at least one visible contract, in any family, through
   [ADR-0023](../../architecture/0023-visible-set-as-a-contract-side-query.md)'s port; its shape,
   its Galician-collated ordering and its `termoId` unchanged. Rewrites the operation description,
   which today asserts the rule SPEC-0004 R1 revoked, and reshapes the integration test named for
   the old behaviour. **Blocked on ADR-0023.** *(SPEC-0004 #20, #21; SPEC-0005 #48)*
2. **Move the administration area onto `GET /api/admin/organos`** *(frontend)*: FEAT-0007's taxonomy
   tree, classification worklist and Órgano table read the admin catalogue instead of the narrowed
   one, so the management surfaces keep the whole catalogue. **Lands with or before task 1.**
   *(SPEC-0004 #19 management half, #22 management half; guards #3, #8, #18)*
3. **Órganos browse section** *(frontend)*: the `/organos` route and its own nav entry and label,
   the read-only tree over the narrowed catalogue and FEAT-0007's taxonomy read, **unclassified
   Órganos at its root**, **empty branches pruned recursively**, each Órgano opening its contracts,
   and the loading/empty/failed-fetch states — with the route code-split so it pulls none of the
   admin slice's components. No management control, no list. *Depends on tasks 1–2, and on
   [FEAT-0011](../FEAT-0011-contratos-menores-browsing/README.md)'s contracts page, which the tree
   links to.* *(SPEC-0004 #9, #19, #22 browse half, and the deferred half of #2; SPEC-0005 #19, #20)*
4. **Órgano name search** *(frontend)*: the typeahead over the same list the tree is built from —
   matching as the user types, partial, case- and accent-insensitively; each entry stating whether
   the Órgano is inactive; a no-matches result distinguishable from a control not yet typed in; and
   **nothing offered for a blank input**. Choosing an entry opens the same Órgano the tree would.
   *Depends on task 3.* *(SPEC-0004 #23, #24, #25, #26 browse half)*

**Criteria this feature deliberately leaves incomplete:**

- **#26's second sentence** — *in the administration area both cover the whole catalogue* — needs an
  admin search, which does not exist and which this feature does not add. The browse half is task
  4's; the admin half waits for a feature that gives the administration area a search, and is
  recorded here so it is open **owned** rather than unowned.
- **#21's leaving half** is provable only once a contract can be made invisible: SPEC-0005 R13's
  removal belongs to the curation feature. An Órgano **entering** the visible set on its first
  visible contract is provable here; **leaving** it on its last is not.
- Every criterion about **importing, the taxonomy and classification** — #1, #3–#8 and #10–#18 —
  belongs to FEAT-0006 and FEAT-0007. This feature adds no management control and imports nothing.

## Edge cases
- **An unclassified Órgano in the visible set** — rendered at the **root** of the browse tree,
  findable by name, and it opens its contracts like any other. This is the ordinary state of every
  newly imported Órgano, not an exception, and with no `USER` catalogue list to fall back on it is
  the whole reason R9 places it there. *(SPEC-0004 #19)*
- **An Órgano outside the visible set** — most of the catalogue — is absent from the browse tree and
  search **and from what they are served**, while the administration area shows it throughout.
  *(SPEC-0004 #20)*
- **A term whose Órganos are all outside the set but whose descendant has one** — still shown. The
  prune is recursive; a per-term check would delete exactly the intermediate levels of a deep
  taxonomy. *(SPEC-0004 #22)*
- **A term that is legitimately empty in the administration area** — an administrator has just
  created it — is **still shown there**, which is why the prune lives in the browse call and not in
  the shared builder. *(SPEC-0004 #14)*
- **The admin section reading the narrowed catalogue** — the failure task 2 exists to prevent. If
  task 1 lands first, the management tree and the unclassified worklist silently lose every Órgano
  without contracts. Nothing errors, which is what makes it worth naming. *(SPEC-0004 #8, #18)*
- **An inactive Órgano that still holds visible contracts** — in the browse tree like any other, in
  its term if it has one and at the root if not, marked inactive in the search. *Inactive* is a fact
  about the source list, not about whether there is anything there. *(SPEC-0004 #19, #23)*
- **A blank search box, and a search matching nothing** — the first offers nothing, the second says
  so. They must not render alike, and neither may fall back to listing the catalogue.
  *(SPEC-0004 #24)*
- **A name differing only by accent or case** — `avila` finds `Ávila`. A plain lowercase match would
  fail exactly the users who know the name. *(SPEC-0004 #25)*
- **The catalogue read and the taxonomy read disagreeing** — two requests, so an admin's edit can
  land between them. FEAT-0007 already decided this: an unresolvable `termoId` renders as
  unclassified rather than dropped or crashed on. With the catalogue narrowed the same rule holds,
  and no Órgano disappears, because the catalogue read is the one that lists them.
- **An empty visible set** — nothing imported yet — renders an empty browse section, which must be
  distinguishable from a **failed** read. FEAT-0007 recorded the same hazard for its two reads, and
  the same answer applies: a failed fetch shows an error with a retry, never an empty result.
