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
 *
 * <p><strong>The pair separates two contracts of a family that publishes one contract per
 * publication, and not every family is one.</strong> Where a family makes several contracts per
 * publication — awards made per lote — two of them carry the same date and the same source
 * identifier, and this pair cannot tell them apart. {@link #outranks} answers false in both
 * directions for such a pair, so neither displaces the other and the name already on display
 * stays; the consequence is accepted deliberately and the operadores specification records why.
 */
@Embeddable
public record NomeRank(@Nullable LocalDate date, long sourceId)
    implements Comparable<NomeRank> {

  private static final Comparator<NomeRank> ORDER =
      Comparator.comparing(
              NomeRank::date, Comparator.nullsFirst(Comparator.<LocalDate>naturalOrder()))
          .thenComparingLong(NomeRank::sourceId);

  private static final NomeRank UNRANKED = new NomeRank(null, Long.MIN_VALUE);

  /**
   * The rank an operador is catalogued at by a publication that ranks nothing — a losing bid, or a
   * procedure whose publication identifier is not a number. It is behind every rank a real
   * publication can carry: undated, so every dated rank beats it, and at the least source
   * identifier, so every undated one does too.
   *
   * <p>It exists because an operador row has to hold <em>some</em> rank while the publication that
   * created it supplies none, and the alternative — passing that publication's own rank — would let
   * a bid decide the displayed name, which is what {@link ResolveOperador} refuses. The first
   * contract to name such an operador therefore outranks it and supplies the name it displays.
   */
  public static NomeRank unranked() {
    return UNRANKED;
  }

  /**
   * Whether this is {@link #unranked()} — the rank of a publication that ranked nothing, read back
   * off a row as a <em>fact about it</em>: the name standing at it is one no publication ranked, so
   * the promotion that displaces it files nothing.
   *
   * <p>Nothing but {@link #unranked()} produces this value, which is what lets it carry that
   * meaning without a column of its own. The question belongs here rather than at the caller, where
   * comparing against the sentinel would leave the meaning of the value away from the type that
   * defines it.
   */
  public boolean ranksNothing() {
    return UNRANKED.equals(this);
  }

  /**
   * R4's order, and the only order this pair has — which is what makes it the natural one: the
   * pair exists so that a name can be ranked, and it is ranked no other way.
   *
   * <p><b>Ascending, so the greater rank is the winner.</b> An undated rank sorts <em>first</em>
   * because <em>ranked last</em> means it loses to every dated one, however high its source
   * identifier and however late it arrives — so the name to display is the {@code max} of a
   * collection, never the first element of a sorted one. {@link #outranks} says that without
   * having to be read in the right direction.
   *
   * <p>Consistent with {@code equals}: two ranks compare equal exactly when they carry the same
   * date and the same source identifier, so a sorted set holds what the record's own equality
   * says it does.
   */
  @Override
  public int compareTo(NomeRank other) {
    return ORDER.compare(this, other);
  }

  /**
   * Whether this rank supplies the displayed name in place of {@code incumbent}: it is the more
   * recently published of the two, settled on the higher source identifier when both share a
   * publication date, and behind every dated rank when it has no date of its own.
   *
   * <p>Taking both values is what makes the choice <b>total</b> — an operador all of whose
   * contracts are undated still has exactly one name — and <b>deterministic wherever the pair
   * separates the two contracts</b>: two imports over such data reach the same answer whatever
   * order the contracts arrive in. Two contracts of one publication share both values, so this
   * answers false both ways for them and the name already displayed stays; that case is settled
   * by arrival rather than by the values, and is accepted rather than closed.
   *
   * <p>A strict win: a rank does not outrank itself, so re-reading a contract already held
   * changes nothing and a replayed batch cannot make a name flap.
   *
   * <p>This orders <b>names only</b>. The fiscal identifier is canonical and reached from every
   * contract identically, so there is no spelling to choose between and nothing about it to rank.
   */
  public boolean outranks(NomeRank incumbent) {
    return compareTo(incumbent) > 0;
  }
}
