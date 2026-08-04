package gal.conxugal.commons.text;

/**
 * One definition of whitespace for text the API accepts and stores.
 *
 * <p>{@link String#strip()} answers to {@link Character#isWhitespace}, which calls a
 * non-breaking space — U+00A0 and its relatives — not whitespace at all, so a name led by one
 * survives stripping and reaches a client that reads it as surrounding whitespace after all.
 * The reverse gap exists too: the separator controls U+001C–U+001F are whitespace to
 * {@code isWhitespace} but carry no Unicode whitespace property. Taking every character either
 * rule calls blank leaves one answer, and it is the broad one.
 */
public final class Whitespace {

  /**
   * Matches a value carrying at least one non-blank character. Written for
   * {@code jakarta.validation.constraints.Pattern}, which needs a constant and matches whole.
   */
  public static final String NON_BLANK_PATTERN =
      "[\\s\\S]*[^\\s\\p{Z}\\x{85}\\x{1C}-\\x{1F}][\\s\\S]*";

  private Whitespace() {}

  /** {@code value} without its surrounding blank characters. */
  public static String strip(String value) {
    int start = 0;
    int end = value.length();
    while (start < end && isBlank(value.charAt(start))) {
      start++;
    }
    while (end > start && isBlank(value.charAt(end - 1))) {
      end--;
    }
    return value.substring(start, end);
  }

  private static boolean isBlank(char character) {
    return Character.isWhitespace(character)
        || Character.isSpaceChar(character)
        || character == '\u0085';
  }
}
