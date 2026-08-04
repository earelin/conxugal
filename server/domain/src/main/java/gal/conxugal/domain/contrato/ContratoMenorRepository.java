package gal.conxugal.domain.contrato;

import gal.conxugal.domain.organo.OrganoId;
import java.util.Collection;

/**
 * Port for storing contratos menores. Implemented by the {@code infrastructure} module.
 *
 * <p>It offers <strong>no delete of any kind</strong>, and the absence is the point rather than
 * an omission: a publication missing from a later import has not been withdrawn — absence at the
 * source means nothing — so an import must never remove a stored contract, and the explicit
 * removal that <em>is</em> meaningful belongs to the later curation feature. A port with nothing
 * to call is what stops a delete being written by accident.
 */
public interface ContratoMenorRepository {

  /**
   * Stores one page of contracts, matching each against what is held by its source identifier and
   * refreshing it in place rather than adding a second row — so re-reading a page, whether across
   * a resumption overlap or a whole re-run, changes nothing.
   *
   * <p>One operation for the page, not a call per contract: the counts it answers with are what
   * the run's outcome reports, and a caller that wrote each row itself could only recover them by
   * reading first and racing whoever wrote next.
   *
   * <p>Matching on a natural key while reporting which branch each row took is beyond a derived
   * query, so the {@code infrastructure} implementation writes this as an explicit statement.
   */
  UpsertCounts upsertAll(Collection<ContratoMenor> contratos);

  /**
   * How many of the Órgano's contracts are stored. The walk tests this against the count the
   * source reports for that Órgano, which is what tells it the history is fully loaded.
   */
  long countByOrganoId(OrganoId organoId);
}
