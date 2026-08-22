package gal.conxugal.domain.licitacion;

/**
 * Port for the published state vocabulary. Implemented by the {@code infrastructure} module.
 *
 * <p><strong>The upsert is what makes an unseen code cost nothing.</strong> The set of states is
 * not closed — code 7 was never observed and higher ones may exist — so nothing seeds this table
 * and nothing validates against it: a procedure carrying a code the source has never published
 * before simply creates its row, inside the same transaction that stores the procedure. Seeding a
 * fixed catalogue instead would turn an unseen code into a rejected procedure, which is the harm
 * storing values as published exists to prevent.
 *
 * <p>No delete, on the same reasoning as {@link LicitacionRepository}'s: nothing an import does
 * removes a published value, and a state no procedure references any more is still a state the
 * source published.
 */
public interface LicitacionStateRepository {

  /**
   * Stores the state, matching it against what is held <strong>by its code</strong> — the
   * source's own identifier — and refreshing the label in place rather than adding a second row.
   * Answers the stored state, which carries the identity a procedure then refers to.
   *
   * <p>Matching on the code and not on the label is the whole point: 101 and 102 are both
   * published as <em>Histórico</em>, so a store keyed on the label would hold one row where the
   * source publishes two states.
   *
   * <p>The only method here, and the absence of a finder beside it is deliberate: the upsert
   * answers the stored state, so nothing storing a procedure needs to look one up first, and a
   * read nobody makes is a read nobody has specified.
   */
  LicitacionState upsert(LicitacionState state);
}
