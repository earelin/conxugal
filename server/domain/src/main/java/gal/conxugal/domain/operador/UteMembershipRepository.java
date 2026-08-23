package gal.conxugal.domain.operador;

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
