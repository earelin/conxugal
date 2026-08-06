package gal.conxugal.domain.operador;

import io.micronaut.data.annotation.Embeddable;
import java.time.LocalDate;
import java.util.Comparator;
import org.jspecify.annotations.Nullable;

/**
 * Which contract a published name was taken from: its publication date and its source
 * identifier. A {@code null date} is a contract whose publication date could not be interpreted.
 *
 * <p>Both values are carried, never the date alone: the name an operador is displayed under is
 * chosen by publication date and settled on the higher source identifier when two contracts
 * share one, so a name holding only a date could not be ordered against a name sharing it, and
 * two names seen only on undated contracts could not be ordered at all. The principal name and
 * the retained ones carry this same pair, and {@link #outranks} is the one comparison that orders
 * both — so the name an operador displays and the names it has retained beside it cannot disagree
 * about which should be showing.
 */
@Embeddable
public record NomeRank(@Nullable LocalDate date, long sourceId) {

  /**
   * Ascending, so the greater rank is the one that wins. A null date sorts first because
   * <em>ranked last</em> means an undated contract loses to every dated one, however high its
   * source identifier and however late it arrives.
   */
  private static final Comparator<NomeRank> ORDER =
      Comparator.comparing(
              NomeRank::date, Comparator.nullsFirst(Comparator.<LocalDate>naturalOrder()))
          .thenComparingLong(NomeRank::sourceId);

  /**
   * Whether this rank supplies the displayed name in place of {@code incumbent}: it is the more
   * recently published of the two, settled on the higher source identifier when both share a
   * publication date, and behind every dated rank when it has no date of its own.
   *
   * <p>Taking both values is what makes the choice <b>total</b> — an operador all of whose
   * contracts are undated still has exactly one name — and <b>deterministic</b>: two imports over
   * the same data reach the same answer whatever order the contracts arrive in.
   *
   * <p>A strict win: a rank does not outrank itself, so re-reading a contract already held
   * changes nothing and a replayed batch cannot make a name flap.
   *
   * <p>This orders <b>names only</b>. The fiscal identifier is canonical and reached from every
   * contract identically, so there is no spelling to choose between and nothing about it to rank.
   */
  public boolean outranks(NomeRank incumbent) {
    return ORDER.compare(this, incumbent) > 0;
  }
}
