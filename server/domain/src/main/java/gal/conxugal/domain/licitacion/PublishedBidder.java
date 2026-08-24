package gal.conxugal.domain.licitacion;

import gal.conxugal.domain.operador.FiscalIdentifier;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One row of the record's bidder table, as the source published it. The award point it belongs to
 * is named by {@code loteKey} — a lote where the procedure has them, and {@code null} meaning
 * <em>the procedure as a whole</em>, which is what the bidder table's {@code -} cell says while
 * the award table writes {@code _} for the same thing.
 *
 * <p><strong>A row is one of exactly two things, and which one is decided by the markup.</strong>
 * A consortium nests a second {@code <ul>} inside the name cell, listing each member with its own
 * identifier; a single firm does not. Measured over 613 bidder rows in 250 procedures that test
 * was exact — never firing on a single firm, never missing a consortium — while a name test
 * beginning {@code UTE} misses 7 of 35 and a {@code U}-prefix identifier test misses 33 of 35.
 *
 * <p><strong>Sealed because the two carry different facts, not a different flag.</strong> A single
 * firm's identifier is the firm's; a consortium's is the consortium's own, published on 2 of 35
 * measured rows, and the members carry theirs. A caller that has a {@link SingleFirm} in its hand
 * cannot reach a member list that does not exist, and one that has a {@link Consortium} cannot
 * mistake its identifier for a firm's — which is the mistake this whole design exists to prevent.
 *
 * <p><strong>This says what the source published and decides nothing about the catalogue.</strong>
 * Whether a consortium becomes an operador holding its identifier or one keyed on its bid needs
 * the formalisation as well as this row, and is not settled here; neither is which operador a
 * single firm resolves to.
 */
public sealed interface PublishedBidder {

  /** The award point this row names, reduced by {@link LoteKey}; null means the procedure. */
  @Nullable String loteKey();

  /** The party's name as published, or null where the row published none. */
  @Nullable String name();

  /**
   * A row naming one firm, which is 578 of the 613 measured.
   *
   * <p>The identifier is held exactly as the {@code NIF} cell published it, reduced only by
   * {@link FiscalIdentifier}'s own canonical form. Nothing here judges whether it is usable —
   * resolving it to an operador is a later task's, and a bidder whose identifier turns out to be
   * unusable is still a bidder this row published.
   *
   * @param loteKey the award point this bid was made at
   * @param name the firm's name, as published
   * @param fiscalIdentifier the firm's own identifier, or null where the cell carried none
   */
  record SingleFirm(
      @Nullable String loteKey, @Nullable String name, @Nullable FiscalIdentifier fiscalIdentifier)
      implements PublishedBidder {

    /**
     * The lote cell is reduced here rather than trusted from the caller, on
     * {@link PublishedAward}'s reasoning: a rule that holds only when its caller has already
     * reduced is one that silently mismatches the day a caller has not.
     */
    public SingleFirm {
      loteKey = LoteKey.normalise(loteKey).orElse(null);
      name = PublishedText.orNullWhenBlank(name);
    }
  }

  /**
   * A row naming a consortium and its member firms, which is 35 of the 613 measured.
   *
   * <p><strong>{@code fiscalIdentifier} is the consortium's own, and it is null far more often
   * than not.</strong> Only 2 of the 35 measured rows carried a real {@code U…}; the other 33
   * carried {@code -} or a {@code TEMP-…} placeholder, and <strong>a placeholder is never offered
   * here</strong> — this branch is taken before the {@code NIF} cell is read at all, so the
   * question of whether that cell holds an identifier only arises once the row is known to be a
   * consortium — and once it does arise, a cell that is not shaped like an identifier answers none.
   * Were it otherwise, one operador would end up holding the identifier {@code -} for dozens of
   * unrelated consortia. The formalisation identifies some of the rest, which is a different table
   * and a later task's join.
   *
   * @param loteKey the award point this bid was made at
   * @param name the consortium's published name, which does not always begin {@code UTE} — 7 of
   *     the 35 measured did not, {@code MISTURAS-INGESAN} among them
   * @param fiscalIdentifier the consortium's own identifier where the row published one
   * @param members the member firms the nested list names, in the order it named them
   */
  record Consortium(
      @Nullable String loteKey,
      @Nullable String name,
      @Nullable FiscalIdentifier fiscalIdentifier,
      List<PublishedConsortiumMember> members)
      implements PublishedBidder {

    /** Reduced the same way {@link SingleFirm} reduces, and for the same reason. */
    public Consortium {
      loteKey = LoteKey.normalise(loteKey).orElse(null);
      name = PublishedText.orNullWhenBlank(name);
      members = List.copyOf(members);
    }
  }
}
