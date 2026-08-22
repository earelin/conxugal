package gal.conxugal.domain.licitacion;

import gal.conxugal.commons.text.Whitespace;
import java.util.Objects;

/**
 * The one form a published value is held in when a row is keyed on it: surrounding whitespace
 * removed, and nothing else. One definition because four values are keyed on — a procedure's
 * publication identifier and the name of each of the three type vocabularies — and a copy of the
 * rule that drifted would let one of them key a row the others would have matched.
 *
 * <p><b>It reduces rather than refuses</b>, on {@link gal.conxugal.domain.operador.FiscalIdentifier
 * FiscalIdentifier}'s reasoning: a rule that holds only when its caller has already stripped is one
 * that silently mismatches the day a caller has not. The padding is an artefact of the record's
 * HTML and the parse is contracted to strip it — but refusing a padded value would cost a real
 * procedure or a real vocabulary entry, and R33 is explicit that losing a published row is the
 * larger harm. Reduced here, a value the adapter forgot to strip matches the row already stored
 * instead of keying a second one beside it.
 *
 * <p><b>Nothing else is reduced.</b> Internal spacing, punctuation, letter case and any differing
 * character make a different key and therefore a different row. Folding case would assert an
 * equivalence the source never published, and unlike a fiscal identifier — whose canonical form is
 * measured to be case-insensitive — nothing says these values are.
 */
final class PublishedKey {

  private PublishedKey() {}

  /**
   * {@code value} stripped of surrounding whitespace, which is the form the key is held in.
   * Throws when it is absent, or empty once stripped: a key that reduced to nothing would collapse
   * every row carrying one onto a single entry, and there is no such published value to hold.
   *
   * @param field the component's name, so the message names what was wrong rather than where
   */
  static String canonical(String value, String field) {
    Objects.requireNonNull(value, "%s must not be null".formatted(field));
    String stripped = Whitespace.strip(value);
    if (stripped.isEmpty()) {
      throw new IllegalArgumentException("%s must not be empty once trimmed".formatted(field));
    }
    return stripped;
  }
}
