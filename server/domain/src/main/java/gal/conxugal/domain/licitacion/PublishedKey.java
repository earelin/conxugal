package gal.conxugal.domain.licitacion;

import gal.conxugal.commons.text.Whitespace;
import java.util.Objects;

/**
 * What a published value has to be for a row to be keyed on it. One definition because four of
 * them are — a procedure's publication identifier and the name of each of the three type
 * vocabularies — and a copy of the rule that drifted would let one of them key a row the others
 * refuse.
 *
 * <p>It checks and never repairs. A value that fails here is an adapter mistake rather than
 * something the source published — padding is an artefact of the record's HTML and the parse is
 * contracted to strip it — so it is refused where the mistake can still be found, not quietly
 * corrected on the way past.
 */
final class PublishedKey {

  private PublishedKey() {}

  /**
   * Throws unless {@code value} can key a row: it must be present, carry something other than
   * whitespace, and already be trimmed. A blank key would collapse every row that had one onto a
   * single entry, and an untrimmed key would sit beside the trimmed spelling of the same
   * published value as a second row — which for a procedure means importing it twice, and for a
   * vocabulary means two entries where the source published one.
   *
   * @param field the component's name, so the message names what was wrong rather than where
   */
  static void validate(String value, String field) {
    Objects.requireNonNull(value, "%s must not be null".formatted(field));
    if (Whitespace.isBlank(value)) {
      throw new IllegalArgumentException("%s must not be blank".formatted(field));
    }
    if (!value.equals(Whitespace.strip(value))) {
      throw new IllegalArgumentException(
          "%s must arrive trimmed, and this one did not: '%s'".formatted(field, value));
    }
  }
}
