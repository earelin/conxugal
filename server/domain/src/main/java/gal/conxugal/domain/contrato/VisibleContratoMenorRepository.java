package gal.conxugal.domain.contrato;

import gal.conxugal.domain.organo.OrganoId;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

/**
 * Port for reading the contratos menores a reader may be shown. Implemented by the
 * {@code infrastructure} module, beside the port that stores them: reading a browse page and
 * writing an import batch are two questions of one table, and separating them is what keeps a
 * read off a port whose whole design argument is about what a write must never offer.
 *
 * <p>Every read here is scoped to one Órgano and one year, and every one carries the same
 * definition of <em>visible</em> — a contract holding all of its publication date, its amount and
 * its awardee. That predicate is the definition rather than a filter each statement bolts on, so a
 * contract missing any of the three reaches no page and no count.
 *
 * <p><b>The ordering is chosen by which method is called</b>, and there is a method per ordering
 * rather than a parameter naming one. The set of orderings is closed — it cannot grow without the
 * requirement that fixed it changing — and a clause assembled from what a caller asked for is
 * where an unindexed or non-total ordering slips in unreviewed. These become native statements,
 * where a property name a caller supplied is interpolated into {@code ORDER BY} verbatim, so no
 * method here takes a sort, a property name or a direction, and none ever should.
 *
 * <p>Two invariants an implementer must not lose:
 *
 * <ul>
 *   <li><b>The {@link Pageable} arrives carrying no ordering</b>, and the implementation adds none
 *       from it. Each statement already orders, and an ordering appended to one that has its own
 *       emits a second {@code ORDER BY} and fails.
 *   <li><b>Every ordering ends with the source identifier as its final key, in the direction of
 *       the key it breaks ties for</b> — a descending sort ends with the identifier descending,
 *       not ascending. Neither sort key is unique, so without it the order is partial and
 *       <em>the next page</em> denotes nothing; and while either direction would make the order
 *       total, only the matching one is a plain backward scan of what an index already holds.
 * </ul>
 *
 * <p>Each answers the count of the <em>whole</em> year alongside the page, not of the page.
 */
public interface VisibleContratoMenorRepository {

  Page<VisibleContratoMenor> byPublicationDateAscending(
      OrganoId organoId, YearSelection year, Pageable pageable);

  Page<VisibleContratoMenor> byPublicationDateDescending(
      OrganoId organoId, YearSelection year, Pageable pageable);

  Page<VisibleContratoMenor> byAmountAscending(
      OrganoId organoId, YearSelection year, Pageable pageable);

  Page<VisibleContratoMenor> byAmountDescending(
      OrganoId organoId, YearSelection year, Pageable pageable);
}
