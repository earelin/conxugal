package gal.conxugal.application.rest.request;

import static gal.conxugal.application.rest.request.Refusals.refused;

import io.micronaut.core.annotation.Nullable;
import java.util.regex.Pattern;

/**
 * A query parameter read as the type the contract declares it, refusing what the framework would
 * otherwise bend into shape — the same job {@code StrictBody} does for a request body, on the
 * other half of the request.
 *
 * <p><b>The value arrives as text on purpose.</b> Bound as an {@code int} carrying a
 * {@code defaultValue}, a value Micronaut's conversion cannot read is not refused: the binder
 * falls back to the default. So {@code ?size=} was answered with fifty rows and a body stating
 * {@code "size": 50} — the wrong answer to a question nobody asked, and invisible, since the
 * response repeats the value the server chose rather than the one the caller sent. <b>Absent and
 * empty are different requests, and only the first has a default.</b>
 */
public final class QueryValues {

  private static final Pattern WHOLE_NUMBER = Pattern.compile("-?\\d{1,10}");

  private QueryValues() {
  }

  /**
   * The parameter as the whole number it claims to be, {@code whenAbsent} if it was not sent at
   * all, or a refusal.
   *
   * <p>The pattern does the refusing rather than {@link Integer#parseInt}, for the reason a year
   * is parsed the same way: {@code parseInt} accepts a leading {@code +} and digits from every
   * script Unicode defines, none of which the contract offers. Ten digits is the widest a
   * {@code format: int32} parameter can be, and anything beyond what an {@code int} holds is
   * refused rather than wrapped.
   */
  public static int wholeNumberOf(String name, @Nullable String published, int whenAbsent) {
    if (published == null) {
      return whenAbsent;
    }
    if (WHOLE_NUMBER.matcher(published).matches()) {
      long value = Long.parseLong(published);
      if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
        return (int) value;
      }
    }
    throw refused("%s must be a whole number".formatted(name));
  }
}
