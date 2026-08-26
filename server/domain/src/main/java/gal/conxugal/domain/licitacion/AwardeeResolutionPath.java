package gal.conxugal.domain.licitacion;

/**
 * How an award reached the operador it names — a fact recorded beside the link rather than derived
 * from it, so a derived link stays distinguishable from one the source published and can be undone
 * without guessing which it was.
 *
 * <p><strong>The four are totally ordered, and the order is the type's rather than a caller's
 * convention.</strong> They are declared weakest first, so the natural {@code compareTo} answers
 * which of any two supersedes and {@link #supersedes} says it without having to be read in the
 * right direction. A reconciliation that re-resolves an awardee gates its write on this: a
 * published identifier supersedes a derived one, even where that moves the award to a different
 * operador.
 *
 * <pre>{@code
 * PUBLISHED_BY_FORMALISATION > PUBLISHED_BY_BIDDER > NAME_DERIVED > UNRESOLVED
 * }</pre>
 *
 * <p>Measured over 284 award rows, the formalisation publishes the identifier for 58%, the bidder
 * list for a further 7%, and the remaining 36% have only a published name. So the two published
 * routes carry two thirds of the population and the inferring one carries the tail.
 */
public enum AwardeeResolutionPath {

  /**
   * No operador is named. R16 and R25 make this a supported outcome rather than a failure: an
   * award whose awardee no route reaches is stored, stays visible, and names nobody.
   */
  UNRESOLVED,

  /**
   * The published name matched exactly one operador in the catalogue. The only route that infers,
   * and the only one this vocabulary marks as such — it never creates an operador and never links
   * on an ambiguous match.
   */
  NAME_DERIVED,

  /**
   * The procedure's bidder list published the awardee. Ordinarily that means it published the
   * awardee's fiscal identifier — and for <strong>a consortium the source declines to
   * identify</strong> it means the bidder list published the party itself, structured as a
   * consortium and listing its members, which is the publication SPEC-0006 R3 keys that
   * operador on in place of an identifier.
   * Either way nothing is inferred: the party the award names is one the record itself put on the
   * page.
   */
  PUBLISHED_BY_BIDDER,

  /**
   * The formalisation published the awardee's fiscal identifier, in the {@code Contratista} cell
   * that carries the name and the identifier together. The strongest route, and the one available
   * for almost every award on a formalised procedure.
   */
  PUBLISHED_BY_FORMALISATION;

  /**
   * Whether a link reached this way replaces one reached {@code incumbent}'s way. A strict win, so
   * re-resolving an award by the route it already holds changes nothing and a replayed
   * reconciliation cannot make an awardee flap.
   */
  public boolean supersedes(AwardeeResolutionPath incumbent) {
    return compareTo(incumbent) > 0;
  }
}
