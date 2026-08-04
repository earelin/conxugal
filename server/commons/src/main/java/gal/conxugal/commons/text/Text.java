package gal.conxugal.commons.text;

/** Limits on the shape of text the API accepts, beyond what any one field means. */
public final class Text {

  /**
   * Matches a value carrying no NUL character. PostgreSQL refuses one in any text column —
   * {@code invalid byte sequence for encoding "UTF8": 0x00} — so text carrying one has to be
   * turned away at the edge, where it is a bad request, rather than at the insert, where it is
   * an unhandled failure. Written for {@code jakarta.validation.constraints.Pattern}, which
   * needs a constant and matches whole.
   */
  public static final String NO_NUL_PATTERN = "[^\\x00]*";

  /**
   * Matches a value carrying no line terminator, for the fields that are a label rather than
   * prose. Stripping cannot answer this one: it reaches only the ends, while a line break in
   * the middle is the case a single-line field has no meaning for.
   */
  public static final String SINGLE_LINE_PATTERN =
      "[^\\n\\r\\u000B\\f\\u0085\\u2028\\u2029]*";

  private Text() {}
}
