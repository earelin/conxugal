package gal.conxugal.domain.operador;

import java.time.LocalDate;
import java.util.Comparator;

/**
 * Which contract supplies the name an operador is displayed under: the most recently published
 * one, ties settled on the higher source identifier, and a contract whose publication date could
 * not be interpreted ranked behind every dated one.
 *
 * <p>The two values are compared in order, and taking both is what makes the choice
 * <b>total</b> — an operador all of whose contracts are undated still has exactly one name — and
 * <b>deterministic</b>: two imports over the same data reach the same answer whatever order the
 * contracts arrive in.
 *
 * <p>It ranks <b>names only</b>. The fiscal identifier is canonical and reached from every
 * contract identically, so there is no spelling to choose between and nothing about it to rank.
 *
 * <p>This is the single ordering over ranks, so the name an operador displays and the names it
 * has retained beside it cannot disagree about which should be showing.
 */
public final class NomeRanking {

  /**
   * Ascending, so the greater rank is the one that wins. A null date sorts first because
   * <em>ranked last</em> means an undated contract loses to every dated one, however high its
   * source identifier and however late it arrives.
   */
  private static final Comparator<NomeRank> ORDER =
      Comparator.comparing(
              NomeRank::date, Comparator.nullsFirst(Comparator.<LocalDate>naturalOrder()))
          .thenComparingLong(NomeRank::sourceId);

  private NomeRanking() {}

  /**
   * Whether {@code candidate} supplies the displayed name in place of {@code incumbent}. A strict
   * win: a rank does not outrank itself, so re-reading a contract already held changes nothing
   * and a replayed batch cannot make a name flap.
   */
  public static boolean outranks(NomeRank candidate, NomeRank incumbent) {
    return ORDER.compare(candidate, incumbent) > 0;
  }
}
