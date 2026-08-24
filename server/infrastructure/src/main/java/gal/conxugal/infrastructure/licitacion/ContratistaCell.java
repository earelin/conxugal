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
 * carrying an identifier and nobody's name is not something the source publishes. The cost of that
 * last rule is that a cell of nothing but an identifier is held as a name that looks like one,
 * which is worth knowing wherever awardee names are matched.
 *
 * <p><strong>Two shapes are deliberately not handled, both unmeasured.</strong> Naming them is the
 * point — each fails towards no identifier rather than towards a wrong one, and each would be
 * cheap to add the day the source is observed publishing it:
 *
 * <ul>
 *   <li><strong>A hyphenated identifier</strong> ({@code A-41111220}) yields no identifier.
 *       Admitting the hyphen would also admit the {@code TEMP-…} placeholder the source writes
 *       where it has none — 14 characters and 8 digits, so every other test here passes it — and
 *       cataloguing an operador under a placeholder pools unrelated suppliers under one identity.
 *       A real hyphenated identifier merely falls to the next route; the placeholder would not.
 *   <li><strong>A cell naming several parties</strong> ({@code NAME A<br>NIF A<br>NAME B<br>NIF B})
 *       would take the last identifier and fold the first party's name and identifier into the
 *       name. Measured, this table publishes one party per row and a consortium under its own
 *       single identifier; the multi-party rendering exists elsewhere on the page, in modals this
 *       parse does not read.
 * </ul>
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
              + "+(?<token>"
              + PublishedIdentifier.TOKEN
              + ")$",
          Pattern.DOTALL);

  /**
   * What {@code published} states. The name is trimmed at its ends and reduced no further, so a
   * published double space survives; the identifier is reduced by {@link FiscalIdentifier} itself.
   *
   * <p>What counts as identifier-shaped is {@link PublishedIdentifier}'s, and the same rule judges
   * a consortium member's entry — which splits on the opposite end and would otherwise carry its
   * own copy of it.
   */
  static ContratistaCell read(@Nullable String published) {
    String cell = PublishedValues.text(published);
    if (cell == null) {
      return new ContratistaCell(null, null);
    }
    Matcher matcher = NAME_AND_TOKEN.matcher(cell);
    if (!matcher.matches() || !PublishedIdentifier.isIdentifierShaped(matcher.group("token"))) {
      return new ContratistaCell(cell, null);
    }
    return new ContratistaCell(
        matcher.group("name"), FiscalIdentifier.of(matcher.group("token")).orElse(null));
  }
}
