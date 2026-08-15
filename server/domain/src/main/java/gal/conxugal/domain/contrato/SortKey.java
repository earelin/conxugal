package gal.conxugal.domain.contrato;

import io.micronaut.data.model.Sort;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The value a browse read is ordered by. Two cases, and the set is closed: it cannot grow without
 * the requirement that fixed it changing, and each case has a statement written for it rather than
 * an ordering assembled from what a caller asked for.
 *
 * <p>{@link #parse} accepts exactly the spelling the contract publishes and nothing else — not
 * another property, not another case, not a longer form of the same word. Anything else answers
 * nothing at all rather than a default, which is the difference between refusing an ordering that
 * was never offered and quietly serving a different one under its label.
 */
public enum SortKey {
  PUBLICATION_DATE("publication_date"),
  AMOUNT("amount");

  private static final String TIEBREAKER = "source_id";

  private final String column;

  SortKey(String column) {
    this.column = column;
  }

  public static Optional<SortKey> parse(@Nullable String published) {
    return switch (published) {
      case "publicationDate" -> Optional.of(PUBLICATION_DATE);
      case "amount" -> Optional.of(AMOUNT);
      case null, default -> Optional.empty();
    };
  }

  /**
   * The whole ordering of a browse read: this key, then the source identifier, both in {@code
   * direction}. It is the only place one is built, and it takes an enum in each hand, so no
   * caller's text can reach the emitted statement.
   *
   * <p><b>The names are columns rather than properties.</b> The browse statement is hand-written
   * SQL and this ordering is appended to it as written — nothing maps a property name onto a
   * column on the way. Naming the properties instead would emit {@code ORDER BY publicationDate},
   * which no table has a column for.
   *
   * <p><b>The tiebreaker takes the direction of the key it breaks ties for.</b> Neither key is
   * unique, so without it the order is partial and <em>the next page</em> denotes nothing. Either
   * direction would make it total, but only the matching one is a plain backward scan of what the
   * browse indexes already hold; the opposite one forces a sort.
   */
  public Sort ordering(Sort.Order.Direction direction) {
    return Sort.of(order(column, direction), order(TIEBREAKER, direction));
  }

  private static Sort.Order order(String column, Sort.Order.Direction direction) {
    return direction == Sort.Order.Direction.ASC ? Sort.Order.asc(column) : Sort.Order.desc(column);
  }
}
