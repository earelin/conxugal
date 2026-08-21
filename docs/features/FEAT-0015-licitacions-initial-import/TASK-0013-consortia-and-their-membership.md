---
feat: FEAT-0015
domain: backend
adrs: [0002, 0023]
status: todo
depends_on: [TASK-0006, TASK-0012]
---

# Consortia and their membership

What is **stored** for a UTE, in both branches.
[TASK-0010](TASK-0010-record-parse-bidders-and-consortium-detection.md) detected it by its markup;
this task decides what becomes of it, and the whole of **amendment 1** lands here.

R17 as originally written required a UTE to be stored "as an operador, identified by its **own
published fiscal identifier**", noting such an identifier "begins with `U`". Measured over 35
consortium rows, the **bidder row** publishes one for **2** of them. The mechanism is right and
mostly unavailable — so a UTE is recorded **whether or not it is identified**, and the two branches
store the same shape.

| | UTE the source identifies | UTE it does not |
| --- | --- | --- |
| The consortium | an operador under R3 | recorded on the participation, with its **published name** |
| Its members | operadores under R3 | operadores under R3 |
| The membership | stored | stored |
| The award, if it won | held by the UTE operador | names the consortium, holds no operador |
| Members' awarded totals | exclude it | exclude it |

*(2 of 35 and 33 of 35 are **bidder-row** measurements. The formalisation identifies some of the
33 — `U86486669` was observed arriving that way on procedure 16938 — so the identified branch is at
least 2 and nothing measures how many more.)*

## Scope

- **A consortium is catalogued as an operador where *either* the bidder row *or* the formalisation
  publishes an identifier for it**, taken from the first of the two that has one. **This task owns
  that act in both cases** — [TASK-0012](TASK-0012-resolve-the-awardee.md)'s path A explicitly takes
  no path for a consortium awardee, so there is one owner and no race between them.

  It is the case that makes *identified* a property of the **procedure** rather than of the bidder
  row, and getting it wrong is not cosmetic: the participation would hold an uncatalogued consortium
  while the award held a catalogued one, and the operador would then have an award and **no
  members** — which SPEC-0006 #40 forbids.
- **Identifying a consortium clears its published name in the same statement that sets the
  operador**, which is what TASK-0006's `CHECK` requires and what keeps a participation from holding
  both.
- **Where neither publishes one, the consortium is recorded on its participation** under its
  published name, with the consortium marker set and no operador. It is *not* catalogued: SPEC-0006
  R3 has no identity to catalogue it under, and R5 rightly forbids inventing one.
- **Each member firm is an operador either way**, resolved through
  [TASK-0011](TASK-0011-extract-resolve-operador.md)'s collaborator on its own published identifier.
  All **80** member entries measured carried an ordinary one.
- **The membership is stored in both cases, hung off the participation.** One shape for a fact the
  source publishes one way. A member's history reaches its consortia through its memberships; an
  identified UTE reaches its members through its participations. **Only one of those directions
  survives for an uncatalogued consortium** — *consortium → its members* has no catalogue entry to
  open and is answerable only on the licitación's own page under R21, a later feature's surface.
- **The award belongs to the consortium alone.** Where the UTE is an operador the award is held by
  it; where it is not, the award names the consortium and holds **no operador**, so it enters no
  member's totals. Either way **no euro is counted twice**, which is the property R17 exists to
  protect.
- R16's unusable-identifier rule holds for a single-firm bidder, an awardee and a UTE **member** —
  all three yield no operador and are recorded as no participant. **A consortium is the exception**,
  and it is not a softening: a party the source names and structures as a bidder **is** a bidder,
  and R16's rule is for a party the source names and cannot identify, which is a different case.

**Out of scope:** the detection itself (TASK-0010), reconciliation of a restated consortium
([TASK-0014](TASK-0014-reconciling-a-restated-procedure.md)), and every display — R21's *opening the
UTE names its members* half is SPEC-0006's own features'.

## Acceptance criteria

- A consortium publishing `U88779475` on its bidder row is catalogued as an operador, its members
  are catalogued, the membership is stored, and an award to it is held by that operador.
  ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #21 as amended)
- A consortium publishing `-` is **not** catalogued; its participation holds its published name and
  the consortium marker, its two members **are** catalogued, the membership is stored against that
  participation, and an award to it holds **no operador**. (SPEC-0008 #20, #21 as amended)
- A consortium whose bidder row publishes no identifier but whose **formalisation** does **is**
  catalogued, its participation's `consortium_name` is cleared in the same statement that sets its
  operador, and its participation and its award point at the **same** operador. The failing shape
  this rules out — a catalogued award beside an uncatalogued participation — is asserted against.
  ([SPEC-0006](../../specs/SPEC-0006-operadores-economicos.md) #40)
- **No award row on a procedure with a consortium points at a member operador**, in either branch —
  so no member's history or total can include an award made to its consortium. *This is the
  **storage precondition** for SPEC-0008 #22's no-double-counting property. #22 itself says
  "stated here, proved in SPEC-0006", and its awarded **total** needs a read nothing in this feature
  builds — so the criterion this satisfies is SPEC-0006 #40, and #22 stays where its own note puts
  it.* (SPEC-0006 #40)
- A UTE **member** whose published identifier is unusable yields no operador and no membership,
  while the consortium, its other members and the licitación are all still stored. (SPEC-0008 #20)
- A consortium recorded on a participation and one catalogued as an operador produce the **same**
  membership rows — one shape, two branches. (SPEC-0008 #21 as amended)
- Integration-tested against PostgreSQL with a real operadores table, since four of these criteria
  are about what the catalogue holds.
