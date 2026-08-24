package gal.conxugal.infrastructure.licitacion;

import gal.conxugal.commons.text.Whitespace;
import gal.conxugal.domain.licitacion.PublishedAmount;
import gal.conxugal.domain.licitacion.VatBasis;
import gal.conxugal.domain.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * How the record's text becomes values. It is the record's own conventions that make this a type
 * rather than three expressions at their call sites: the page writes amounts in Galician notation
 * with the tax basis appended, and dates in three widths — neither survives a default parse, and
 * both would pass every test written against an ASCII stub.
 *
 * <p>A value that cannot be read answers null rather than raising. R33 is explicit that a value
 * which cannot be interpreted is absent and the procedure is stored regardless; the failures worth
 * losing a whole record over are structural, and they are the adapter's to judge.
 */
final class PublishedValues {

  /**
   * The same characters {@link Whitespace} calls blank, so that a value the strip would have
   * removed at the ends is also flattened in the middle. A narrower class would leave a
   * non-breaking space between a figure and its currency mark, and the figure would read as
   * unparseable rather than as itself.
   */
  private static final Pattern BLANK_RUN = Pattern.compile("[\\s\\p{Z}\\x{85}\\x{1C}-\\x{1F}]+");

  /**
   * Galician notation: {@code .} groups thousands and {@code ,} opens the decimals — the exact
   * reverse of what a default parse assumes, which reads {@code 3.378.552,09} as {@code 3.378} and
   * is wrong by six orders of magnitude without failing.
   *
   * <p>The trailing tokens are read off rather than into the number. {@code con IVE} and
   * {@code sen IVE} are the source stating its own tax basis; the currency mark is present on an
   * awarded amount and absent from the two budgets, and carries nothing either way — every figure
   * this source publishes is in euros and it never says otherwise. That mark is measured to be
   * {@code €}, published as the entity {@code &amp;#8364;} and decoded by jsoup whatever the page's
   * charset is; no measured row writes the word.
   *
   * <p>A sign is not accepted, because no published figure carries one. A negative amount would
   * therefore read as absent rather than as a negative.
   */
  private static final Pattern AMOUNT =
      Pattern.compile(
          "^(?<number>\\d{1,3}(?:\\.\\d{3})*(?:,\\d+)?|\\d+(?:,\\d+)?)"
              + "(?:\\s*€)?"
              + "(?:\\s*(?<basis>con|sen)\\s+IVE)?$",
          Pattern.CASE_INSENSITIVE);

  /**
   * {@code DD-MM-YYYY}, optionally with a time, and the time optionally with seconds. All three
   * widths occur on one page — the bare date on a difusión column, the minute form on the
   * procedure's own difusión stamp, the second form on a resolution's — so a formatter accepting
   * only the widest reads none of the others.
   *
   * <p>{@code uuuu} and {@code STRICT} together are what make a published {@code 31-02} fail
   * instead of resolving to the 28th: a date nobody published is worse than no date at all, and
   * {@code SMART} — the default — would quietly produce one.
   */
  private static final DateTimeFormatter PUBLISHED_DATE =
      new DateTimeFormatterBuilder()
          .appendPattern("dd-MM-uuuu")
          .optionalStart()
          .appendPattern(" HH:mm")
          .optionalStart()
          .appendPattern(":ss")
          .optionalEnd()
          .optionalEnd()
          .toFormatter(Locale.ROOT)
          .withResolverStyle(ResolverStyle.STRICT);

  private PublishedValues() {}

  /**
   * {@code published} trimmed at its ends and reduced no further, or null when nothing survives.
   *
   * <p>The trim is the ends only because the markup's indentation is the source's formatting and
   * anything inside the value is the source's content. A record really does publish an object
   * containing a double space, and collapsing it would store text the source never published while
   * every test written against a tidy fixture went on passing.
   */
  static @Nullable String text(@Nullable String published) {
    if (published == null) {
      return null;
    }
    String trimmed = Whitespace.strip(published);
    return trimmed.isEmpty() ? null : trimmed;
  }

  /**
   * A label reduced to the form values are looked up by: trimmed at its ends and its internal runs
   * flattened to one space.
   *
   * <p>Flattened, unlike {@link #text}, because a label is a key rather than a published value.
   * The markup already wraps this page's <em>values</em> across four lines, so a template that
   * wrapped a label too is not a hypothetical — and a key holding the wrap would be one no lookup
   * could reach, reading the field as absent from a record that published it, with nothing to say
   * why.
   */
  static String label(String published) {
    String reduced = collapse(published);
    return reduced == null ? "" : reduced;
  }

  /** The figure {@code published} states, with the tax basis it states beside it. */
  static @Nullable PublishedAmount amount(@Nullable String published) {
    String collapsed = collapse(published);
    if (collapsed == null) {
      return null;
    }
    Matcher matcher = AMOUNT.matcher(collapsed);
    if (!matcher.matches()) {
      return null;
    }
    BigDecimal value =
        new BigDecimal(matcher.group("number").replace(".", "").replace(',', '.'));
    return new PublishedAmount(new Money(value), vatBasis(matcher.group("basis")));
  }

  /** The day {@code published} states, whichever of the record's three widths it is written in. */
  static @Nullable LocalDate date(@Nullable String published) {
    String collapsed = collapse(published);
    if (collapsed == null) {
      return null;
    }
    try {
      return LocalDate.parse(collapsed, PUBLISHED_DATE);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  /** The count {@code published} states, or null where it states none or states a negative one. */
  static @Nullable Integer count(@Nullable String published) {
    String collapsed = collapse(published);
    if (collapsed == null) {
      return null;
    }
    try {
      int value = Integer.parseInt(collapsed);
      return value < 0 ? null : value;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * The regulated-list code a classification cell names: everything up to the first blank run.
   *
   * <p><strong>The cell carries the code and its description together.</strong> Measured on
   * 822054, a CPV cell reads {@code 45000000}, a non-breaking space, then
   * {@code Trabajos de construcción}, and a NUT cell reads {@code ES111} then {@code A Coruña} —
   * so a parse taking the whole cell would key the vocabulary on a code-and-wording string, and
   * one splitting on a plain space would not split at all. The separator is U+00A0, which
   * {@link #BLANK_RUN} already calls blank, so this splits on the same character class everything
   * else here trims by.
   *
   * <p>The description beside it is {@link #description}'s.
   */
  static @Nullable String code(@Nullable String published) {
    String trimmed = text(published);
    if (trimmed == null) {
      return null;
    }
    Matcher blank = BLANK_RUN.matcher(trimmed);
    return blank.find() ? trimmed.substring(0, blank.start()) : trimmed;
  }

  /**
   * The wording a classification cell carries after its code, or null where it carries none. The
   * remainder is trimmed at its ends and reduced no further, so its own internal spacing is the
   * source's.
   */
  static @Nullable String description(@Nullable String published) {
    String trimmed = text(published);
    if (trimmed == null) {
      return null;
    }
    Matcher blank = BLANK_RUN.matcher(trimmed);
    return blank.find() ? text(trimmed.substring(blank.end())) : null;
  }

  /**
   * Internal whitespace runs flattened to one space, which {@link #text} deliberately does not do.
   * A number's layout carries no meaning — the markup breaks {@code 3.378.552,09} and its
   * {@code con IVE} across four lines — so the two rules differ because the two kinds of value do.
   */
  private static @Nullable String collapse(@Nullable String published) {
    String trimmed = text(published);
    return trimmed == null ? null : BLANK_RUN.matcher(trimmed).replaceAll(" ");
  }

  private static @Nullable VatBasis vatBasis(@Nullable String marker) {
    if (marker == null) {
      return null;
    }
    return "con".equalsIgnoreCase(marker) ? VatBasis.INCLUSIVE : VatBasis.EXCLUSIVE;
  }
}
