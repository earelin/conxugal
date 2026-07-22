package gal.conxugal.commons.validation;

/** Argument-validation checks shared by value types across the server's modules. */
public final class Preconditions {

  private Preconditions() {}

  /**
   * Requires {@code value} to be zero or positive.
   *
   * @throws IllegalArgumentException if {@code value} is negative
   */
  public static void requireNotNegative(long value, String name) {
    if (value < 0) {
      throw new IllegalArgumentException(name + " must not be negative, was " + value);
    }
  }
}
