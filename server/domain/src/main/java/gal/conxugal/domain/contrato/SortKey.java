package gal.conxugal.domain.contrato;

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
  PUBLICATION_DATE,
  AMOUNT;

  public static Optional<SortKey> parse(@Nullable String published) {
    return switch (published) {
      case "publicationDate" -> Optional.of(PUBLICATION_DATE);
      case "amount" -> Optional.of(AMOUNT);
      case null, default -> Optional.empty();
    };
  }
}
