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
   *
   * <p><strong>The procedure's state and any type it names must already be stored</strong>, each
   * carrying the identity its own upsert answered with. Nothing in the domain enforces the
   * ordering — a {@link Licitacion} constructs perfectly well around a vocabulary value the
   * database has never seen — so an implementation must refuse an unstored one rather than write
   * a null into a {@code NOT NULL} foreign key, on the precedent {@code retainName} sets for an
   * unstored operador.
   */
  UpsertOutcome upsert(Licitacion licitacion);

  /**
   * The stored procedure published under this identifier, or nothing. This is how a reconciliation
   * reaches what it is about to restate — by the key the source publishes, since a restatement
   * arrives knowing nothing of the identity the system assigned.
   *
   * <p><strong>An implementation must fetch-join all four vocabulary references</strong> —
   * {@code state}, {@code contractType}, {@code procedureType} and {@code tramitacionType}. There
   * is no implicit to-one fetch: unjoined, the mapper tries to build each reference as an
   * id-only stub, cannot (none of the four has a constructor it can use for that), and hands the
   * aggregate a null state, which its own constructor refuses. That is every stored row, not an
   * unlucky one.
   *
   * <p>The join must be a <strong>left</strong> one. An inner join would drop the ordinary
   * procedure whose record published no contract type — most of them — from a read that asked
   * for it by its identifier.
   */
  Optional<Licitacion> findByPublicationId(long publicationId);
}
