package gal.conxugal.infrastructure.licitacion;

import java.util.regex.Pattern;

/**
 * What tells a fiscal identifier from a word, in the one place both cells that have to make that
 * judgement read it from.
 *
 * <p>Two cells on the record publish a name and an identifier in one string and must find the
 * boundary between them: the formalisation's {@code Contratista}, which writes the name first, and
 * a consortium member's entry, which writes the identifier first. They split on opposite ends, but
 * the question each asks of the candidate token is the same one — and two copies of it would drift
 * the first time either was corrected, leaving one cell cataloguing under a token the other had
 * already learned to reject.
 *
 * <p><strong>Measured against every awardee, bidder, member and contratista name on the captured
 * records.</strong> The rule accepts each of {@code A41111220}, {@code B36746584},
 * {@code U86486669} — a UTE's own — {@code A70319678} and {@code B94181807} on the member entries,
 * and the {@code 12345678Z} and {@code X1234567Z} shapes. It rejects every trailing or leading name
 * token seen: {@code S.A.} and {@code S.L.} on the dot, {@code MARTÍN} on the accent,
 * {@code TEMP-2026-0001} on the hyphen, {@code ABADIN} and {@code SLU} on the length, and
 * {@code CONSTRUCCIONES} and {@code GRUPO2000} on the digit count.
 *
 * <p>No letter is required, so a purely numeric foreign VAT number still reads as one. The hyphen
 * is excluded deliberately: admitting it would also admit the {@code TEMP-…} placeholder the source
 * writes where it has none — 14 characters and 8 digits, so every other test here passes it — and
 * cataloguing an operador under a placeholder pools unrelated suppliers under one identity. A real
 * hyphenated identifier merely falls to the next route; the placeholder would not.
 */
final class PublishedIdentifier {

  /**
   * The characters and the width. Exposed as a fragment rather than only as a compiled pattern
   * because {@code ContratistaCell} splices it into a larger expression — its split point is
   * <em>found</em> by this shape, so a copy there would be the drift this type exists to prevent.
   */
  static final String TOKEN = "[A-Za-z0-9]{8,14}";

  private static final Pattern WHOLE_TOKEN = Pattern.compile("^" + TOKEN + "$");

  /**
   * How many digits a token must carry to be an identifier rather than a word. Six is under the
   * seven a NIE carries and the eight a NIF or CIF does, and above anything a company name ends
   * in — it is what tells {@code A41111220} from {@code CONSTRUCCIONES}, which is fourteen letters
   * and would otherwise pass every other test here.
   */
  private static final int MINIMUM_DIGITS = 6;

  private PublishedIdentifier() {}

  /** Whether {@code token} is shaped like a fiscal identifier rather than like a word. */
  static boolean isIdentifierShaped(String token) {
    return WHOLE_TOKEN.matcher(token).matches()
        && token.chars().filter(Character::isDigit).count() >= MINIMUM_DIGITS;
  }
}
