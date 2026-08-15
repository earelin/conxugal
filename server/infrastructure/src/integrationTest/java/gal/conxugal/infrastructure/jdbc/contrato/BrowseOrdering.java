package gal.conxugal.infrastructure.jdbc.contrato;

import gal.conxugal.domain.contrato.SortKey;
import gal.conxugal.domain.contrato.VisibleContratoMenor;
import io.micronaut.data.model.Sort;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * One of the four orderings a browse read offers, held once for the two tests that need it: the
 * schema test, which pins the plan of the SQL, and the repository test, which pins the SQL
 * Micronaut Data emits. Both take their {@code ORDER BY} from the same {@link SortKey#ordering}, so
 * a plan proved against one statement cannot come to describe a different one.
 */
record BrowseOrdering(SortKey key, Sort.Order.Direction direction) {

  static List<BrowseOrdering> all() {
    return List.of(
        new BrowseOrdering(SortKey.PUBLICATION_DATE, Sort.Order.Direction.ASC),
        new BrowseOrdering(SortKey.PUBLICATION_DATE, Sort.Order.Direction.DESC),
        new BrowseOrdering(SortKey.AMOUNT, Sort.Order.Direction.ASC),
        new BrowseOrdering(SortKey.AMOUNT, Sort.Order.Direction.DESC));
  }

  Sort sort() {
    return key.ordering(direction);
  }

  /** The clause the framework appends for {@link #sort()}, rendered as it renders it. */
  String orderBy() {
    return sort().getOrderBy().stream()
        .map(order -> "%s %s".formatted(order.getProperty(), order.getDirection()))
        .collect(Collectors.joining(", ", "ORDER BY ", ""));
  }

  /** What the rows must come back in, if the appended clause is doing what it says. */
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
