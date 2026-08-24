---
feat: FEAT-0015
domain: backend
adrs: [0002, 0023]
status: todo
depends_on: [TASK-0006, TASK-0012]
---

# Consortia and their membership

What is **catalogued** for a UTE.
[TASK-0010](TASK-0010-record-parse-bidders-and-consortium-detection.md) detected it by its markup;
this task decides which operador it becomes, and the whole of **amendment 1** lands here.

R17 as originally written required a UTE to be stored "as an operador, identified by its **own
published fiscal identifier**", noting such an identifier "begins with `U`". Measured over 35
consortium rows, the **bidder row** publishes one for **2** of them. The mechanism is right and
mostly unavailable — so a UTE is **an operador either way**, and the source's reticence costs one
thing only:

| | UTE the source identifies | UTE it does not |
| --- | --- | --- |
| The consortium | an operador holding that identifier | an operador holding **none**, keyed on this bid |
| Across procedures | the **same** operador wherever it appears | a **separate** operador per bid |
| Its members | operadores under R3 | operadores under R3 |
| The membership | operador ↔ operador | operador ↔ operador |
| The award, if it won | held by the UTE operador | held by the UTE operador |
| Members' awarded totals | exclude it | exclude it |

*(2 of 35 and 33 of 35 are **bidder-row** measurements. The formalisation identifies some of the
33 — `U86486669` was observed arriving that way on procedure 16938 — so the identified branch is at
least 2 and nothing measures how many more.)*

## Scope

- **The identifier is resolved before the operador is created**, from the first of the bidder row
  and the formalisation that publishes one. **This task owns that act** —
  [TASK-0012](TASK-0012-resolve-the-awardee.md)'s path A explicitly takes no path for a consortium
  awardee, so there is one owner and no race between them.

  **The ordering is the load-bearing part, not an optimisation.** Creating the bid's operador first
  and identifying it afterwards would mint an identifier-less UTE that the formalisation then has
  to merge into the identified one — a retro-active re-partition of a row already written, which
  [ADR-0023](../../architecture/0023-operadores-as-a-stored-projection.md) rests on never having to
  perform. It is also what makes *identified* a property of the **procedure** rather than of the
  bidder row: get it wrong and the bid points at one operador while the award points at another,
  leaving the identified one with an award and **no members**, which SPEC-0006 #40 forbids.
- **Where an identifier is published, the UTE is an ordinary operador** under SPEC-0006 R3,
  resolved through [TASK-0011](TASK-0011-extract-resolve-operador.md)'s collaborator exactly as a
  single firm is, and found again on the next procedure that names it.
- **Where none is, this task mints the identifier-less operador** R3's second identity admits: one
  row per bid, `fiscal_id` null, the `ute` marker set, holding the consortium's **published name**.
  Nothing is invented — no placeholder becomes an identity — and because such a row is never
  *matched* on anything, it can neither absorb another party's contract nor be re-partitioned
  later.

  **It is minted once per bid and found again by nothing** — which is a problem this task has to
  solve rather than assume, because [TASK-0006](TASK-0006-licitacions-store-the-competition-tables.md)
  deliberately gave it no key to be found by. A re-import of the same procedure must not mint a
  second one, and the row itself offers no way to recognise it: `operador_economico` holds only the
  `ute` marker, and `licitacion_participation`'s key already contains `operador_economico_id`, so
  *the participation of this bid* cannot be looked up without already knowing the operador.
  Matching on the published name is what amendment 1 refuses. **Settling this is in scope here**,
  and the shape that does it — a bid reference on the operador, a uniqueness constraint spanning
  the procedure, or a lookup this task adds — is a migration of its own. Getting it wrong mints a
  second consortium and a second participation per re-import and leaves the previous bid visible.
- **Setting the `ute` marker on an operador already catalogued is this task's too**, and no port
  offers it yet. `OperadorRepository` has `findByFiscalId`, `insert`, `promoteName` and
  `retainName`; none writes the marker, and
  [TASK-0011](TASK-0011-extract-resolve-operador.md)'s `ResolveOperador` — which is what creates an
  operador for every family now — creates one through a constructor that leaves it false. So a UTE holding a real `U…` identifier that a **contrato
  menor** named first — which the mark's own ordering makes likely, contratos menores importing
  before licitacións — is already in the catalogue unmarked, and the criterion below could not be
  met without a way to set it.
- **The `ute` marker is set in both branches.** It is what R8's list and R21's page distinguish a
  joint venture by, and SPEC-0006 R6 admits it because the source publishes it structurally rather
  than the system deriving it.
- **A UTE created by a bid contributes no rank**, on exactly
  [TASK-0022](TASK-0022-resolve-the-bidders.md)'s rule for a single-firm bidder: it is catalogued
  under the name the bid published, and beyond that does not promote a name, does not enter the
  retained set and does not displace a name a *contract* published. R4 selects from an operador's
  most recently published **contract**, and a losing bid is not one. An unidentified UTE therefore
  keeps the one name its bid gave it, which is the only name it will ever have.
- **Each member firm is an operador**, resolved through TASK-0011's collaborator on its own
  published identifier. All **80** member entries measured carried an ordinary one.
- **The membership relates the two operadores**, so it reads from either end in both branches —
  *who was this consortium made of* and *what has this firm been part of* are one relation. The
  earlier model hung it off the bid, which answered only the second.
- **The award belongs to the consortium's operador alone**, in both branches, so it enters no
  member's totals and **no euro is counted twice** — the property R17 exists to protect.
- R16's unusable-identifier rule holds for a single-firm bidder, an awardee and a UTE **member** —
  all three yield no operador and are recorded as no participant. **A consortium is the exception**,
  and it is not a softening: a party the source names and structures as a bidder **is** a bidder,
  and R16's rule is for a party the source names and cannot identify, which is a different case.

**Out of scope:** the detection itself (TASK-0010), reconciliation of a restated consortium
([TASK-0014](TASK-0014-reconciling-a-restated-procedure.md)), and every display — R21's *opening the
UTE names its members* half is SPEC-0006's own features'.

## Acceptance criteria

- A consortium publishing `U88779475` on its bidder row is catalogued as an operador under that
  identifier, with the `ute` marker set; its members are catalogued; the membership is stored; and
  an award to it is held by that operador.
  ([SPEC-0008](../../specs/SPEC-0008-import-browse-licitacions.md) #21 as amended)
- A consortium publishing `-` is catalogued as an operador holding **no fiscal identifier**, under
  its published name and with the `ute` marker set; its two members are catalogued; the membership
  is stored between them; and an award to it is held by that operador rather than by nobody.
  (SPEC-0008 #20, #21 as amended)
- **Re-importing the same procedure mints no second UTE**: the identifier-less operador its bid
  created is found again, and the procedure holds one consortium, not two. (SPEC-0008 #17)
- **Two procedures each publishing an unidentified consortium under the same name produce two
  operadores**, never one — the system claims no continuity the source did not publish.
  ([SPEC-0006](../../specs/SPEC-0006-operadores-economicos.md) #40 as amended)
- A consortium whose bidder row publishes no identifier but whose **formalisation** does is
  catalogued under that identifier and **no identifier-less row is created for it at all**; its
  participation and its award point at the **same** operador. The failing shape this rules out —
  two operadores for one consortium on one procedure — is asserted against. (SPEC-0006 #40)
- **No award row on a procedure with a consortium points at a member operador**, in either branch —
  so no member's history or total can include an award made to its consortium. *This is the
  **storage precondition** for SPEC-0008 #22's no-double-counting property. #22 itself says
  "stated here, proved in SPEC-0006", and its awarded **total** needs a read nothing in this feature
  builds — so the criterion this satisfies is SPEC-0006 #40, and #22 stays where its own note puts
  it.* (SPEC-0006 #40)
- A UTE **member** whose published identifier is unusable yields no operador and no membership,
  while the consortium, its other members and the licitación are all still stored. (SPEC-0008 #20)
- A UTE whose identifier a **contrato menor** catalogued first — unmarked, since that family knows
  nothing of consortia — is **marked** when a licitación publishes it as one, rather than staying
  an ordinary firm for ever. (SPEC-0006 #40 as amended)
- An identified consortium and an unidentified one produce the **same** membership rows — one
  shape, two branches. (SPEC-0008 #21 as amended)
- Integration-tested against PostgreSQL with a real operadores table, since most of these criteria
  are about what the catalogue holds.
