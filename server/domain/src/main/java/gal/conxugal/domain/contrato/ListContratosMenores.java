package gal.conxugal.domain.contrato;

import gal.conxugal.domain.organo.OrganoId;
import gal.conxugal.domain.organo.OrganoNotFoundException;
import gal.conxugal.domain.organo.OrganoRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import jakarta.inject.Singleton;

/**
 * One Órgano's contratos menores of one year, in one ordering, one page at a time, with the count
 * of the whole selection beside it.
 *
 * <p><b>It is the last place an ordering could be assembled from a caller's text, and so the place
 * that guarantees none is.</b> What arrives here is a {@link SortKey} and a
 * {@link Sort.Order.Direction} — two enums whose values together are the closed set of orderings
 * this read offers — and the {@link Sort} the store is handed is built from those and nothing else.
 * The invariant is the ordering's <em>provenance</em>, not its absence: the browse statement is
 * native and appends a property name verbatim, so a sort assembled from a request parameter would
 * put a caller's text into the emitted SQL. A property name never reaches this method, because
 * there is no parameter it could arrive on.
 *
 * <p><b>The four orderings are {@link SortKey#ordering}, not a mapping restated here.</b> That
 * method is total over both enums — it takes no default branch, because the compiler can see the
 * set is covered — and it is what appends the source identifier in the direction of the key it
 * breaks ties for. Neither key is unique, so that tiebreaker is what makes the order total and
 * <em>the next page</em> denote something; and it is the same expression the adapter derives the
 * columns it will accept from, so a second one written here could only come to disagree with it.
 *
 * <p><b>An unknown Órgano is refused, not answered with an empty page.</b> A page of nothing and
 * <em>there is no such Órgano</em> are two different answers and only one of them is true, so a
 * typed or stale identifier raises {@link OrganoNotFoundException} rather than reading a year of a
 * catalogue row that is not there. It is the same refusal the classification and import-mark use
 * cases raise, and the driving side already renders it.
 *
 * <p><b>It applies no default and corrects nothing.</b> No default year, no fallback ordering, no
 * clamped page: the page number and size arrive as given and are handed on as given. Every input
 * has been validated by the time it reaches here, and refusing a malformed one is the driving
 * adapter's job — a use case that quietly corrected one would answer a question nobody asked.
 */
@Singleton
public class ListContratosMenores {

  private final OrganoRepository organos;
  private final VisibleContratoMenorRepository visibleContratos;

  public ListContratosMenores(
      OrganoRepository organos, VisibleContratoMenorRepository visibleContratos) {
    this.organos = organos;
    this.visibleContratos = visibleContratos;
  }

  /**
   * The requested page of the selection, and the total of the whole selection with it — the same
   * total on every page of it, since it counts the year rather than what this page holds.
   *
   * <p>The catalogue is asked first, and its answer is discarded: what is wanted from it is that
   * the Órgano exists, and reading the row is how this system asks. One lookup by primary key is
   * what it costs, and it is the only thing standing between a mistyped identifier and an empty
   * page presented as an answer.
   *
   * <p><b>The ordering replaces whatever the pageable carried rather than joining it.</b>
   * {@link Pageable#withSort} is that replacement; {@code order(...)} beside it would append, and
   * appending would let an ordering that arrived from somewhere else survive into a clause this
   * method is supposed to be the whole source of. Nothing else about the pageable is touched — not
   * the page, not the size, not whether a total was asked for.
   */
  public Page<VisibleContratoMenor> list(
      OrganoId organoId,
      YearSelection year,
      SortKey key,
      Sort.Order.Direction direction,
      Pageable pageable) {
    if (organos.findById(organoId).isEmpty()) {
      throw new OrganoNotFoundException(organoId);
    }
    return visibleContratos.page(organoId, year, pageable.withSort(key.ordering(direction)));
  }
}
