package gal.conxugal.domain.licitacion;

import gal.conxugal.commons.text.Whitespace;
import java.util.Objects;

/**
 * What a published name has to be for a type vocabulary to be keyed on it. One definition because
 * the three type vocabularies enforce the same rule, and a copy of it that drifted would let one
 * of them key a row the other two refuse.
 *
 * <p>It checks and never repairs. A name that fails here is an adapter mistake rather than
 * something the source published — the padding is an artefact of the record's HTML and the parse
 * is contracted to strip it — so the value is refused where the mistake can still be found, not
 * quietly corrected on the way past.
 */
final class VocabularyName {

  private VocabularyName() {}

  /**
   * Throws unless {@code name} can key a vocabulary row: it must be present, carry something other
   * than whitespace, and already be trimmed. A blank name would key an entry that is not a fact
   * about anything, and an untrimmed one would key an entry of its own beside the trimmed spelling
   * of the same value — two rows for one published name, each with its own procedures hanging off
   * it.
   */
  static void validate(String name) {
    Objects.requireNonNull(name, "name must not be null");
    if (Whitespace.isBlank(name)) {
      throw new IllegalArgumentException("name must not be blank");
    }
    if (!name.equals(Whitespace.strip(name))) {
      throw new IllegalArgumentException(
          "name must arrive trimmed, and this one did not: '%s'".formatted(name));
    }
  }
}
