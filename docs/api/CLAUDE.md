# API contract conventions

`openapi.yaml` in this folder is the authoritative REST contract: every endpoint is
authored here **before** its controller exists, and CI enforces that the implementation
conforms ([ADR-0010](../architecture/0010-design-first-openapi-contract.md)). A path
written here is therefore the decision, not a description of one — get the naming right
in this file and the code follows.

## Resource naming

Every path is mounted under `/api/`
([ADR-0006](../architecture/0006-reserved-api-url-prefix.md)). After that prefix — and
after the `/admin/` segment where present — the resource noun is **plural when the path
addresses the collection, singular when it addresses one element**
([ADR-0016](../architecture/0016-rest-resource-naming.md), the governing record).

| Path addresses | Noun | Example |
| --- | --- | --- |
| the collection — list | plural | `GET /api/organos` |
| the collection — create an element | plural | `POST /api/admin/taxonomy-nodes` |
| the collection — an operation on the whole set | plural | `POST /api/admin/organos/import` |
| one element, by id | singular | `GET /api/organo/{id}` |
| one element's sub-resource | singular | `PUT /api/admin/organo/{id}/taxonomy-node` |
| a singleton — no collection exists | singular | `GET /api/me` |

Applying it:

- **A create takes the plural.** It has no id yet, so it acts on the collection.
- **A sub-resource follows the same rule for its own noun** — `.../{id}/taxonomy-node`
  places the element in one node; a sub-collection would be plural in turn.
- **Multi-word nouns are kebab-case**: `system-status`, `taxonomy-node`.
- **Use the noun the domain already uses.** `organo`/`organos` because the aggregate is
  `OrganoDeContratacion`; `taxonomy-node`/`taxonomy-nodes` because it is `TaxonomyNode`.
  The contract does not translate a term the code has chosen, so the model's language mix
  reaches the URLs deliberately.

Why it is worth the two spellings: a collection and its members occupy **disjoint**
namespaces, so a sibling resource can never collide with a member path. `/api/organos` and
`/api/organos/taxonomy` would collide — the second is not an Órgano id, yet it sits where
`/api/organos/{id}` must go, forcing every client to special-case the literal and
reserving `"taxonomy"` against ever being an id. Under this rule the taxonomy read is
`GET /api/taxonomy-nodes` and the collision cannot arise.

### Before adding a path

Ask what the path addresses, not what the operation does: if the caller must supply an
identifier to name the thing being acted on, the noun is singular. A path that reaches one
element through a plural noun is a defect, and review is what catches it — nothing in
Micronaut enforces it.
