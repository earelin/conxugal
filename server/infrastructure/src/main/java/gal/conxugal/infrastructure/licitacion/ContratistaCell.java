package gal.conxugal.infrastructure.licitacion;

import gal.conxugal.domain.operador.FiscalIdentifier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The formalisation table's {@code Contratista} cell, which publishes a name and a fiscal
 * identifier in one place. A type of its own because the rule that separates them is a judgement
 * with a failure mode, and both halves of it need somewhere to be argued.
 *
 * <p><strong>This is the primary route to an awardee's identifier</strong> — 58% of all award rows
 * and 95% of those on a formalised procedure — so what this gets wrong is not one field but which
 * operador an award is attributed to.
 *
 * <p><strong>The two halves are separated by whitespace, whatever the markup does.</strong>
 * Measured, the source writes the pair two ways: {@code EQUINSE, S.A. A41111220} on one line, and
 * the name, a {@code <br>} and the identifier on the captured records. jsoup's {@code wholeText()}
 * renders that break as a newline even where the markup puts no space around it, so both layouts
 * arrive here with a blank run between the halves and one rule reads both.
 *
 * <p><strong>The split takes the last blank run, and only if what follows is shaped like an
 * identifier.</strong> Where it is not, the whole cell is the name and there is no identifier —
 * that row is still a valid formalisation, one route to an identifier that did not answer rather
 * than a broken record, and it never reaches the outstanding ledger. A cell holding a single token
 * is a name too: {@code the remainder is the name} presupposes a remainder, and a formalisation
 * carrying an identifier and nobody's name is not something the source publishes.
 */
record ContratistaCell(@Nullable String name, @Nullable FiscalIdentifier fiscalIdentifier) {

  /** The class {@link PublishedValues} and {@code Whitespace} both call blank. */
  private static final String BLANK = "[\\s\\p{Z}\\x{85}\\x{1C}-\\x{1F}]";

  /**
   * The name, then the last blank run, then a candidate token. {@code .*} is greedy so the
   * <em>last</em> run splits; {@code DOTALL} is what lets it cross the newline a {@code <br>}
   * leaves behind. The token's own shape is judged separately, because a cell whose trailing word
   * merely looks structural must still yield the whole cell as a name.
   */
  private static final Pattern NAME_AND_TOKEN =
      Pattern.compile(
          "^(?<name>.*[^\\s\\p{Z}\\x{85}\\x{1C}-\\x{1F}])"
              + BLANK
              + "+(?<token>[A-Za-z0-9]{8,14})$",
          Pattern.DOTALL);

  /**
   * How many digits a token must carry to be an identifier rather than a word. Six is under the
   * seven a NIE carries and the eight a NIF or CIF does, and above anything a company name ends
   * in — it is what tells {@code A41111220} from {@code CONSTRUCCIONES}, which is fourteen
   * letters and would otherwise pass every other test here.
   */
  private static final int MINIMUM_DIGITS = 6;

  /**
   * What {@code published} states. The name is trimmed at its ends and reduced no further, so a
   * published double space survives; the identifier is reduced by {@link FiscalIdentifier} itself.
   *
   * <p>A token is identifier-shaped when it is 8 to 14 characters of ASCII letters and digits
   * carrying at least {@link #MINIMUM_DIGITS} digits. Measured against every awardee, bidder and
   * contratista name on the captured records, that accepts each of {@code A41111220},
   * {@code B36746584}, {@code U86486669} — a UTE's own — and the {@code 12345678Z} and
   * {@code X1234567Z} shapes, and rejects every trailing name token seen: {@code S.A.} and
   * {@code S.L.} on the dot, {@code MARTÍN} on the accent, {@code TEMP-2026-0001} on the hyphen,
   * {@code ABADIN} and {@code SLU} on the length, and {@code CONSTRUCCIONES} and
   * {@code GRUPO2000} on the digit count. No letter is required, so a purely numeric foreign VAT
   * number still reads as one.
   */
  static ContratistaCell read(@Nullable String published) {
    String cell = PublishedValues.text(published);
    if (cell == null) {
      return new ContratistaCell(null, null);
    }
    Matcher matcher = NAME_AND_TOKEN.matcher(cell);
    if (!matcher.matches() || !isIdentifierShaped(matcher.group("token"))) {
      return new ContratistaCell(cell, null);
    }
    return new ContratistaCell(
        matcher.group("name"), FiscalIdentifier.of(matcher.group("token")).orElse(null));
  }

  private static boolean isIdentifierShaped(String token) {
    return token.chars().filter(Character::isDigit).count() >= MINIMUM_DIGITS;
  }
}
