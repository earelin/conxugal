---
spec: SPEC-0004
adrs: [0002, 0003, 0004, 0005, 0006, 0008, 0010]
status: draft
---

# FEAT-0007. Órganos taxonomy & classification

## Goal
Let administrators organise the imported catalogue into a **multilevel taxonomy**, and
expose that taxonomy to any authenticated user as a read endpoint, per
**[SPEC-0004](../../specs/SPEC-0004-import-manage-organos-contratacion.md)** (R1 write
operations, the read contract of R9, R14–R18, and the taxonomy-placement part of R8). It
adds the taxonomy of category nodes, the single-node classification of Órganos, the
unclassified set, the `GET /api/organos/taxonomy` read endpoint, and one user interface:
an **admin management** section to build the taxonomy, classify Órganos, and run imports.

No user-facing Órganos browser is built here. The read endpoint's first consumer is the
**Órgano filter of the contratos list**, delivered by the future contract-querying
feature; this feature stops at the contract that filter will call.

It builds directly on **[FEAT-0006](../FEAT-0006-organos-catalogue-import/README.md)**,
which delivers the stored catalogue, the `GET /api/organos` read endpoint, and the import
trigger this feature's admin UI drives. The backend follows the hexagonal split of
**[ADR-0002](../../architecture/0002-hexagonal-architecture.md)** with the taxonomy
aggregate mapped 1:1 to its table
(**[ADR-0008](../../architecture/0008-domain-entities-carry-persistence-mapping-annotations.md)**);
REST lives under the reserved `/api/` prefix
(**[ADR-0006](../../architecture/0006-reserved-api-url-prefix.md)**), authored
contract-first (**[ADR-0010](../../architecture/0010-design-first-openapi-contract.md)**)
and guarded by session security
(**[ADR-0005](../../architecture/0005-session-based-authentication.md)**). The UI is the
React Router SPA served by the backend
(**[ADR-0003](../../architecture/0003-react-router-ui-served-by-backend.md)**) built with
Vite + Mantine (**[ADR-0004](../../architecture/0004-ui-stack-vite-mantine.md)**), in
Galician.

## Scope
- **Domain (taxonomy):** a `TaxonomyNode` aggregate — UUID identity, name, and an optional
  parent node — forming a tree; a `TaxonomyNodeRepository` port (read the tree, insert,
  rename, re-parent, delete).
- **Domain (placement):** extend the `OrganoDeContratacion` aggregate and
  `OrganoRepository` (from FEAT-0006) with an **optional** taxonomy-node placement, plus
  queries to list Órganos by node and to list the **unclassified** ones.
- **Domain (use cases):** taxonomy management (`CreateNode`, `RenameNode`, `MoveNode` with
  the cycle guard, `DeleteNode` with the child/reassignment rules) and classification
  (`AssignOrganoToNode`, `ClearOrganoNode`), plus a read that assembles the tree with the
  Órganos in each node.
- **Infrastructure:** a migration adding the `taxonomy_node` table (self-referencing
  parent) and a nullable `taxonomy_node_id` on the catalogue table; the Micronaut Data JDBC
  `TaxonomyNodeRepository` and the `OrganoRepository` placement operations.
- **Application (driving):**
  - **Read (any authenticated user):** `GET /api/organos/taxonomy` — the tree of nodes
    with the Órganos in each (SPEC-0004 R2, R9); and the placement field added to the
    `GET /api/organos` catalogue response (SPEC-0004 R8).
  - **Manage (`ADMIN` only):** node create/rename/move/delete, Órgano assign/clear, and
    the unclassified listing (SPEC-0004 R1, R14–R18).
- **UI — taxonomy admin (`ADMIN` only):** manage the tree (create, rename, move, delete),
  classify Órganos, work the unclassified set, and trigger an import (reusing FEAT-0006's
  endpoint) with its outcome shown (SPEC-0004 R1, R10 surfacing, R14–R18). This is the
  only UI in this feature.

**Out of scope (owned by other specs/features):**
- The **user-facing taxonomy browsing UI** — SPEC-0004 R9's *presentation* for a `USER`.
  There is no Órganos browser section: the tree a user navigates to pick an Órgano is the
  **Órgano filter of the contratos list**, so it is built by the future contract-querying
  feature, against the endpoint delivered here. No reusable Órgano-selector component is
  built in advance — it has no consumer yet, and its shape is the filter's to decide. The
  R9 *read contract* (`GET /api/organos/taxonomy`, authenticated) is in scope here.
- The **contract-query screens** themselves — a future contract-browsing spec/feature.
- **Importing and reconciling** the catalogue and the import endpoint/scheduler — owned by
  [FEAT-0006](../FEAT-0006-organos-catalogue-import/README.md). This feature only *drives*
  the existing import endpoint from the admin UI and *reads* the catalogue it maintains.
- **Multiple placements** for one Órgano — SPEC-0004 R17 fixes single placement; not a
  configurable option here.

## Design

### Hexagonal placement ([ADR-0002](../../architecture/0002-hexagonal-architecture.md))
```mermaid
flowchart LR
    subgraph application["application (driving)"]
        treeApi["GET /api/organos/taxonomy (authenticated)"]
        manageApi["/api/admin/taxonomy/** + classify (ADMIN)"]
    end
    subgraph domain["domain"]
        node["TaxonomyNode"]
        nodeRepo["TaxonomyNodeRepository (port)"]
        organo["OrganoDeContratacion (+ placement)"]
        organoRepo["OrganoRepository (+ placement ops)"]
        manageUC["CreateNode / RenameNode / MoveNode / DeleteNode"]
        classifyUC["AssignOrganoToNode / ClearOrganoNode"]
        treeUC["GetTaxonomyTree"]
    end
    subgraph infrastructure["infrastructure (driven)"]
        jdbcNode["JdbcTaxonomyNodeRepository"]
        jdbcOrgano["OrganoRepository placement ops"]
    end
    application --> domain
    infrastructure --> domain
```

### Taxonomy as a tree
- A `TaxonomyNode` has a UUID identity, a name, and an **optional parent** — a null parent
  is a root node, so the taxonomy has many roots and any depth (SPEC-0004 R14). The
  self-referencing `parent_id` keeps it a single-table mapping (ADR-0008).
- `MoveNode` enforces the tree invariant: a node cannot be re-parented to **itself or any
  of its descendants**, which would create a cycle; the move is rejected and nothing
  changes (SPEC-0004 R15). Every node has at most one parent by construction.
- `DeleteNode` applies the R16 rules: it is **rejected while the node has child nodes**
  (the admin must move or remove them first); deleting an otherwise-empty node **returns
  any Órganos assigned directly to it to the unclassified set**. Deleting a node never
  deletes an Órgano.

### Placement and classification
- Placement is an **optional** `taxonomy_node_id` on the catalogue row — exactly one node
  or none (SPEC-0004 R17). Because it lives on the Órgano row that FEAT-0006 reconciles
  **in place**, an import never disturbs it (SPEC-0004 R5, R6).
- `AssignOrganoToNode` sets the placement (replacing any current one — never additive);
  `ClearOrganoNode` removes it. An Órgano with no placement is **unclassified**; every
  newly imported Órgano starts there, and the unclassified set is a first-class listing so
  admins can find and file them (SPEC-0004 R18).
- When the target node is deleted, the reassignment rule above returns its Órganos to
  unclassified rather than orphaning them against a missing node.

### API surface ([ADR-0006](../../architecture/0006-reserved-api-url-prefix.md), [ADR-0010](../../architecture/0010-design-first-openapi-contract.md))
- `GET /api/organos/taxonomy` — `@Secured(IS_AUTHENTICATED)`: the tree of nodes, each node
  carrying its child nodes **and the Órganos placed in it** (id, name, active state), so a
  single call fills a filter tree (SPEC-0004 R2, R9). The catalogue is therefore
  serialisable from two endpoints; that is accepted deliberately — the filter's one-request
  shape wins over avoiding the overlap with `GET /api/organos`, which stays the flat
  catalogue view including unclassified Órganos.
- `GET /api/organos` — the FEAT-0006 catalogue list, its response **extended here** to
  carry each Órgano's placement (node, or unclassified) (SPEC-0004 R8).
- `/api/admin/taxonomy/**` and the Órgano-classification operations — `@Secured("ADMIN")`:
  create/rename/move/delete nodes, assign/clear an Órgano's node, and list the unclassified
  set (SPEC-0004 R1, R14–R18). A `USER` gets 403.
- All contracts are authored in [`docs/api/openapi.yaml`](../../api/openapi.yaml) before the
  controllers, and CI enforces conformance (ADR-0010).

### UI ([ADR-0003](../../architecture/0003-react-router-ui-served-by-backend.md), [ADR-0004](../../architecture/0004-ui-stack-vite-mantine.md))
- **Taxonomy admin** — the only UI here, an `ADMIN`-only section: the tree with create/rename/move/delete
  controls, an assign-to-node action and the unclassified worklist for classifying Órganos,
  and an **import** button that calls FEAT-0006's `POST /api/admin/organos/import` and shows
  the returned outcome (added/refreshed/deactivated, or "already running"). Chrome and
  messages in Galician (SPEC-0001 R6). Admin-only nav gating is cosmetic; `/api/admin/**`
  stays server-gated.
- **No `USER` section** — a `USER` gains no new route or nav entry from this feature. They
  reach the taxonomy only through `GET /api/organos/taxonomy`, and will see it rendered
  when the contratos-list filter arrives.

## Sequencing (tasks, one small change each)
1. **Taxonomy node domain model + repository port** — the `TaxonomyNode` aggregate (UUID,
   name, optional parent) and the `TaxonomyNodeRepository` port (read tree, insert, rename,
   re-parent, delete). *(SPEC-0004 #14, #15)*
2. **Taxonomy store infrastructure** — a migration adding the `taxonomy_node` table
   (self-referencing parent) and a nullable `taxonomy_node_id` on the catalogue table; the
   JDBC `TaxonomyNodeRepository` and the `OrganoRepository` placement operations
   (set/clear node, list by node, list unclassified). *(SPEC-0004 #14, #17, #18)*
3. **Taxonomy management use cases** — `CreateNode`, `RenameNode`, `MoveNode` (cycle
   guard), `DeleteNode` (reject with children; return directly-assigned Órganos to
   unclassified). *(SPEC-0004 #14, #15, #16)*
4. **Órgano classification use cases** — `AssignOrganoToNode` (single placement, replaces
   any current), `ClearOrganoNode`, and the unclassified listing. *(SPEC-0004 #17, #18)*
5. **Taxonomy & classification REST endpoints** — OpenAPI-first: read tree
   (`GET /api/organos/taxonomy`, authenticated, nodes with their Órganos embedded), the
   placement field on `GET /api/organos`, and the `ADMIN` management + classify +
   unclassified endpoints under `/api/admin`. *(SPEC-0004 #1, #2, #8, #9, #14–#18)*
6. **Taxonomy admin UI** — the `ADMIN` section: manage the tree, classify Órganos, work the
   unclassified set, and trigger an import with its outcome. *(SPEC-0004 #1, #10, #14–#18)*

## Edge cases
- **Cycle on move** — re-parenting a node under itself or a descendant is rejected and the
  taxonomy is unchanged (SPEC-0004 #15).
- **Delete a non-empty node** — deletion is rejected while the node has child nodes;
  deleting a node with directly-assigned Órganos returns those Órganos to unclassified and
  deletes no Órgano (SPEC-0004 #16).
- **Import preserves placement** — because placement is a column on the in-place-updated
  catalogue row (FEAT-0006), an Órgano keeps its node across re-imports, and an Órgano gone
  inactive keeps its placement (SPEC-0004 #5, #6).
- **Reassignment is a move, not a copy** — assigning an already-classified Órgano to a new
  node leaves it in only the new node; it is never in two at once (SPEC-0004 #17).
- **Newly imported Órgano is unclassified** — it appears in the unclassified worklist until
  an admin files it (SPEC-0004 #18).
- **Read is read-only for a USER** — every mutation endpoint is `ADMIN`-gated at the
  server, so a `USER` calling the API directly cannot change the taxonomy or a placement;
  they can only `GET /api/organos/taxonomy`. With no `USER` UI here, the server gate is the
  whole story (SPEC-0004 #1, #9).
- **Empty taxonomy** — until an admin creates the first node, `GET /api/organos/taxonomy`
  returns an empty tree and every Órgano is unclassified. The endpoint ships before its
  consumer, so it must be well-defined in that state rather than assumed non-empty.
- **Concurrent edits to the tree** — two admins moving/deleting overlapping nodes must not
  corrupt the tree or strand a placement against a just-deleted node; node moves/deletes and
  the reassignment they trigger are applied atomically.
