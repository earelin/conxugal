package gal.conxugal.domain.contrato;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Which way a browse read's ordering runs. Two cases, closed for the same reason
 * {@link SortKey}'s are: the pair chooses one of a fixed set of statements, not a clause built
 * around whatever arrived.
 *
 * <p>{@link #parse} accepts exactly {@code asc} and {@code desc}. {@code ascending},
 * {@code descending}, {@code ASC} and an empty value all answer nothing at all — a direction that
 * fell back to ascending when it was not recognised would serve one ordering while claiming
 * another, which is the failure this parse exists to make impossible.
 */
public enum Direction {
  ASC,
  DESC;

  public static Optional<Direction> parse(@Nullable String published) {
    return switch (published) {
      case "asc" -> Optional.of(ASC);
      case "desc" -> Optional.of(DESC);
      case null, default -> Optional.empty();
    };
  }
}
