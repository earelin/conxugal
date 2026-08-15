package gal.conxugal.infrastructure.jdbc.contrato;

import gal.conxugal.domain.contrato.SortKey;
import gal.conxugal.domain.contrato.VisibleContratoMenor;
import io.micronaut.data.model.Sort;
import java.util.Comparator;
import java.util.List;

/**
 * One of the four orderings a browse read offers, held once for the two tests that need it: the
 * schema test, which pins the plan of the SQL, and the repository test, which pins the SQL the
 * read emits.
 *
 * <p>{@link #orderBy} is <strong>written out rather than rendered from {@link #sort()}</strong>,
 * and the difference matters. Rendering it would restate the production expression on both sides
 * of the assertion, so a {@link SortKey} that named {@code publicationDate} would change the
 * expected clause along with the emitted one and the test would stay green. Written out, it is a
 * fixed point: the repository test proves what the read emits against it, and the schema test
 * proves the plan of it, so the clause whose plan is known is the clause that runs.
 */
record BrowseOrdering(SortKey key, Sort.Order.Direction direction, String orderBy) {

  static List<BrowseOrdering> all() {
    return List.of(
        new BrowseOrdering(
            SortKey.PUBLICATION_DATE,
            Sort.Order.Direction.ASC,
            "ORDER BY publication_date ASC, source_id ASC"),
        new BrowseOrdering(
            SortKey.PUBLICATION_DATE,
            Sort.Order.Direction.DESC,
            "ORDER BY publication_date DESC, source_id DESC"),
        new BrowseOrdering(
            SortKey.AMOUNT, Sort.Order.Direction.ASC, "ORDER BY amount ASC, source_id ASC"),
        new BrowseOrdering(
            SortKey.AMOUNT, Sort.Order.Direction.DESC, "ORDER BY amount DESC, source_id DESC"));
  }

  Sort sort() {
    return key.ordering(direction);
  }

  /** What the rows must come back in, if the clause is doing what it says. */
  Comparator<VisibleContratoMenor> comparator() {
    Comparator<VisibleContratoMenor> byKey =
        key == SortKey.PUBLICATION_DATE
            ? Comparator.comparing(VisibleContratoMenor::publicationDate)
            : Comparator.comparing(visible -> visible.amount().value());
    Comparator<VisibleContratoMenor> total =
        byKey.thenComparingLong(VisibleContratoMenor::sourceId);
    return direction == Sort.Order.Direction.ASC ? total : total.reversed();
  }

  @Override
  public String toString() {
    return "%s %s".formatted(key, direction);
  }
}
