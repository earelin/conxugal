package gal.conxugal.domain.licitacion;

import java.util.Optional;

/**
 * Port for storing licitacións. Implemented by the {@code infrastructure} module.
 *
 * <p>It offers <strong>no delete of any kind</strong>, and the absence is the point rather than
 * an omission: a procedure missing from a later import has not been withdrawn — absence at the
 * source means nothing — so an import must never remove a stored procedure, and the explicit
 * removal that <em>is</em> meaningful belongs to the later curation feature. A port with nothing
 * to call is what stops a delete being written by accident.
 */
public interface LicitacionRepository {

  /**
   * Stores one procedure, matching it against what is held <strong>by its publication
   * identifier</strong> — the natural key, not the identity this port may be about to assign —
   * and refreshing it in place rather than adding a second row. So re-reading a page, whether
   * across a resumption overlap or a whole re-run, changes nothing.
   *
   * <p>One procedure at a time, unlike the batch its contratos menores sibling takes: a
   * licitación is retrieved by a request of its own and stored with its children in one
   * transaction, so there is no page of them to write at once.
   *
   * <p>Matching on a natural key while reporting which branch the row took is beyond a derived
   * query, so the {@code infrastructure} implementation writes this as an explicit statement.
   */
  UpsertOutcome upsert(Licitacion licitacion);

  /**
   * The stored procedure published under this identifier, or nothing. This is how a reconciliation
   * reaches what it is about to restate — by the key the source publishes, since a restatement
   * arrives knowing nothing of the identity the system assigned.
   */
  Optional<Licitacion> findByPublicationId(long publicationId);
}
