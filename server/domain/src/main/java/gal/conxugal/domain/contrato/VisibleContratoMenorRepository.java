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
 * <p>The read is scoped to one Órgano and one year, and it carries the definition of
 * <em>visible</em> — a contract holding all of its publication date, its amount and its awardee.
 * That predicate is the definition rather than a filter the statement bolts on, so a contract
 * missing any of the three reaches no page and no count.
 *
 * <p><b>The ordering arrives on the {@link Pageable}</b>, and three obligations travel with it.
 * They are the caller's because the statement cannot enforce them, and each is load-bearing:
 *
 * <ul>
 *   <li><b>The {@link io.micronaut.data.model.Sort} is built from {@link SortKey} and
 *       {@link io.micronaut.data.model.Sort.Order.Direction}, never from a caller's string.</b>
 *       Those two enums are the closed set of orderings offered, and building the sort from them
 *       is what guarantees only a property this system named can appear. The statement is native,
 *       so a property name is appended to it verbatim and unescaped — a sort assembled from raw
 *       input would put a caller's text into the emitted SQL.
 *   <li><b>The sort ends with the source identifier, in the direction of the key it breaks ties
 *       for.</b> Neither sort key is unique, so without it the order is partial and <em>the next
 *       page</em> denotes nothing; and while either direction would make the order total, only the
 *       matching one is a plain backward scan of what an index already holds.
 *   <li><b>The statement carries no ordering of its own.</b> The framework appends the
 *       {@code Pageable}'s ordering to the end of a native statement, so one that already ordered
 *       would emit two.
 * </ul>
 *
 * <p>It answers the count of the <em>whole</em> year alongside the page, not of the page.
 */
public interface VisibleContratoMenorRepository {

  Page<VisibleContratoMenor> page(OrganoId organoId, YearSelection year, Pageable pageable);
}
