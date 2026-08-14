package gal.conxugal.domain.contrato;

import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The publication year a browse read is scoped to, and the whole of that scope. Its own type
 * rather than a bare {@code int} so that a year cannot be passed where a page number, a count or
 * an amount is expected — and, more importantly, so that <strong>there is no way to ask for
 * anything but a year</strong>.
 *
 * <p><b>There is one case and no absence.</b> No constant standing for <em>all years</em>, no
 * <em>undated</em> selection, no nullable component and no sentinel value: a selection that could
 * represent the absence of a year is a selection every reader would have to check, and the check
 * would be forgotten exactly once. Holding it as a type is what makes the all-years list
 * unrepresentable rather than merely refused.
 *
 * <p>{@link #parse} answers {@link Optional} rather than throwing. A malformed year is a refusal
 * the caller has to render, only the driving adapter knows it renders as a status code, and
 * reporting <em>no value</em> is how the domain says so without knowing about HTTP.
 */
@TypeDef(type = DataType.INTEGER, converter = YearSelectionConverter.class)
public record YearSelection(int year) {

  private static final Pattern FOUR_DIGITS = Pattern.compile("\\d{4}");

  public static YearSelection of(int year) {
    return new YearSelection(year);
  }

  /**
   * The year a caller asked for, or nothing at all when what they asked for is not one. Exactly
   * four digits and nothing else is a year: no sign, no padding, no surrounding whitespace, and no
   * word — {@code all} and {@code undated} are refused like any other text, because neither names
   * a selection this type has.
   *
   * <p>The pattern is what does the refusing rather than {@link Integer#parseInt}, which accepts a
   * sign, an arbitrary number of digits, and digits from every script Unicode defines.
   */
  public static Optional<YearSelection> parse(@Nullable String published) {
    if (published == null || !FOUR_DIGITS.matcher(published).matches()) {
      return Optional.empty();
    }
    return Optional.of(new YearSelection(Integer.parseInt(published)));
  }
}
