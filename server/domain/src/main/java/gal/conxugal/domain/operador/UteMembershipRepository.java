package gal.conxugal.domain.operador;

import gal.conxugal.domain.licitacion.LicitacionId;
import java.util.Collection;
import java.util.List;

/**
 * Port for which firms made up a consortium. Implemented by the {@code infrastructure} module.
 *
 * <p>No delete, on {@link OperadorRepository}'s reasoning: the catalogue has one writer and a
 * membership the source stops publishing is withdrawn rather than removed.
 */
public interface UteMembershipRepository {

  /**
   * Stores one membership, matching it against what is held for the same pair — the consortium and
   * the member firm — and refreshing it in place. One operation rather than find-then-write, so a
   * source that lists a member twice leaves one row and no caller loses a race to a duplicate the
   * store would then reject.
   *
   * <p>Answers nothing, unlike the catalogue's other writes: the pair the table keys on <em>is</em>
   * the membership's identity, so there is no assigned identity to hand back.
   *
   * <p>Both operadores must already be stored.
   */
  void upsert(UteMembership membership);

  /**
   * Marks withdrawn every membership <strong>this procedure</strong> states and the given ones do
   * not — the reconciliation of a consortium the record has restated, or dropped altogether.
   *
   * <p><strong>Scoped to the one procedure, which is the whole point.</strong> The rule is that a
   * membership stops being visible when no visible bid of its UTE still publishes it, not when one
   * procedure stops publishing it. A UTE the source identifies is one operador across every
   * procedure naming it, so withdrawing what this procedure no longer states leaves what another
   * still states visible, and the pair disappears from a reader's view only when the last statement
   * of it does. For a UTE the source declines to identify every statement is this procedure's, so
   * the same call withdraws the lot.
   *
   * <p>An <strong>empty</strong> retained set is the ordinary case rather than a caller's mistake:
   * it is a procedure whose record no longer publishes the consortium at all, and every membership
   * it stated goes.
   *
   * <p>A membership already withdrawn is left alone, so a re-import that changes nothing writes
   * nothing here. Answers how many it <em>newly</em> marked, which is what lets a caller — a test
   * above all — tell that apart from marking the same rows again.
   */
  int withdrawAbsent(LicitacionId licitacionId, Collection<UteMembership> retained);

  /**
   * The consortia this operador is <strong>visibly</strong> a member of — the memberships that
   * count toward its reachability. One marked withdrawn is not among them: it would otherwise keep
   * a member firm reachable through a fact no reader is shown.
   */
  List<UteMembership> findByOperadorIdAndWithdrawnFalse(OperadorId operadorId);

  /**
   * The firms this consortium was <strong>visibly</strong> made up of. The other end of the same
   * relation, and the direction the earlier model could not answer for a consortium the source
   * declined to identify — it had no catalogue entry to ask.
   */
  List<UteMembership> findByUteIdAndWithdrawnFalse(OperadorId uteId);
}
