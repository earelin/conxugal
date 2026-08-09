package gal.conxugal.commons.time;

import java.time.LocalDate;
import java.util.Objects;

/**
 * The comparisons {@link LocalDate} leaves to its callers. It is {@link Comparable}, so each is
 * expressible at the call site — and each reads there as a ternary over {@code isAfter}, which says
 * how the answer is computed rather than what it is.
 */
public final class Dates {

  private Dates() {}

  /** The later of the two, and {@code one} when they are the same date. */
  public static LocalDate latest(LocalDate one, LocalDate other) {
    Objects.requireNonNull(one, "one must not be null");
    Objects.requireNonNull(other, "other must not be null");
    return one.isBefore(other) ? other : one;
  }
}
