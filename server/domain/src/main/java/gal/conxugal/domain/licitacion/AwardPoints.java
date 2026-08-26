package gal.conxugal.domain.licitacion;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The points a procedure competes and is awarded at: its stored lotes by the one form everything
 * compares a lote cell on, and the procedure itself for a row that names no lote.
 *
 * <p>The stored spelling is reduced here rather than trusted to match a published row's, because
 * the record's tables do not spell one lote the same way — {@code 05} against {@code 5} was
 * measured within a single record, and the award, formalisation and NUT tables write {@code _} for
 * the procedure as a whole while the bidder table writes {@code -}. Every table naming an award
 * point joins through this, so a padded lote cannot find its lote in one table and miss it in
 * another.
 */
record AwardPoints(LicitacionId licitacionId, Map<String, LoteId> byKey) {

  AwardPoints {
    byKey = Map.copyOf(byKey);
  }

  static AwardPoints of(LicitacionId licitacionId, Iterable<Lote> lotes) {
    Objects.requireNonNull(licitacionId, "licitacionId must not be null");
    Map<String, LoteId> byKey = new HashMap<>();
    for (Lote lote : lotes) {
      LoteId id =
          Objects.requireNonNull(
              lote.id(), "a stored lote must carry an identity: " + lote.identifier());
      LoteKey.normalise(lote.identifier()).ifPresent(key -> byKey.put(key, id));
    }
    return new AwardPoints(licitacionId, byKey);
  }

  /**
   * The point {@code loteKey} names: a lote, or the procedure as a whole where the row named no
   * lote.
   *
   * <p>A row naming a lote the procedure has not stored fails the procedure rather than being filed
   * somewhere plausible: keying it to the procedure would collide with every other such row on the
   * same natural key, and skipping it would lose a published row silently. The record parse takes
   * the same view of a bidder count that disagrees with the award table's.
   */
  @Nullable LoteId at(@Nullable String loteKey) {
    if (loteKey == null) {
      return null;
    }
    LoteId awardPoint = byKey.get(loteKey);
    if (awardPoint == null) {
      throw new IllegalArgumentException(
          "a published row names lote %s, which procedure %s has not stored"
              .formatted(loteKey, licitacionId.value()));
    }
    return awardPoint;
  }
}
