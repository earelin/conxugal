package gal.conxugal.domain.licitacion;

import java.util.List;
import java.util.Objects;

/**
 * What cataloguing one procedure's consortia left behind: which operador each became, and the bids
 * written for them.
 *
 * <p>The bids are here because the bidder table is reconciled as <strong>one</strong> table.
 * {@link StoreLicitacionConsortia} writes the consortium rows and {@link StoreLicitacionBidders}
 * the single-firm ones, but a bid the record no longer publishes has to be withdrawn against every
 * bid it still does — so whoever withdraws needs both halves, and only these two classes know the
 * operador each row resolved to. Handing the bids back is what lets that reconciliation happen once
 * rather than twice against half the evidence.
 *
 * <p>The memberships are <em>not</em> here, and that asymmetry is the point: they are reconciled by
 * the class that wrote them, which is the only one that knows a procedure's whole published
 * membership. Nothing above it has to be told.
 */
public record StoredConsortia(ConsortiumOperadores operadores, List<Participation> bids) {

  public StoredConsortia {
    Objects.requireNonNull(operadores, "operadores must not be null");
    bids = List.copyOf(bids);
  }

  /** A procedure that published no consortium: nothing catalogued and no bid of this kind. */
  public static StoredConsortia none() {
    return new StoredConsortia(ConsortiumOperadores.none(), List.of());
  }
}
