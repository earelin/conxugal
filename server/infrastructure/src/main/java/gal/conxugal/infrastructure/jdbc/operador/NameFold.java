package gal.conxugal.infrastructure.jdbc.operador;

import gal.conxugal.domain.operador.MatchableName;

/**
 * The fold {@link MatchableName} defines, expressed in SQL. It has to answer exactly what that type
 * answers — a key computed there is compared against a name folded here — which is why it is those
 * three steps rather than a table of accented characters: lower-case, decompose, and keep the
 * unaccented ASCII letters and digits. {@code normalize(…, NFD)} splits an accented letter into its
 * base and a combining mark, and the replacement drops the mark along with every comma, full stop
 * and space.
 *
 * <p>It lives on its own because two packages compare on it — the catalogue's own name lookup, and
 * the licitacións store asking which operador a procedure's identifier-less consortium was
 * catalogued as. Three copies of one expression is how one of them eventually learns a step the
 * others do not.
 *
 * <p>No index serves it and none is added on speculation. The catalogue match is the last route an
 * award tries and reaches roughly a third of them; if the scan ever shows in an import's timings,
 * an expression index over this exact expression is what it costs.
 */
public final class NameFold {

  private NameFold() {
  }

  /** The fold applied to one column, for splicing into a statement. */
  public static String of(String column) {
    return "regexp_replace(normalize(lower(%s), NFD), '[^a-z0-9]', '', 'g')".formatted(column);
  }
}
