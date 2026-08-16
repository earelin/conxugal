package gal.conxugal.application.rest.paging;

import static gal.conxugal.application.rest.request.QueryValues.wholeNumberOf;
import static gal.conxugal.application.rest.request.Refusals.refused;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Pageable;

/**
 * The {@code page} and {@code size} a paginated read takes, and the pageable they become — the
 * inbound half of the contract {@link PagedResponse} carries back.
 *
 * <p><b>Declared once for the same reason the envelope is.</b> Three specifications' lists take
 * these two parameters, and a reader meeting several of them in one session would experience one
 * paging differently from the rest as a defect rather than as a design. Held here, the defaults,
 * the bounds and the change of base are one implementation rather than three that have to be kept
 * in step — and the refusals below are ones an operation would otherwise have to remember to make.
 *
 * <p><b>Every bad value is refused, never corrected.</b> A page below the first, a size outside
 * its range, a value that is not a number, and a value sent empty are each a {@code 400}. Clamping
 * would make the response disagree with the request that produced it, and defaulting would answer
 * a question nobody asked — which is exactly what an empty {@code size} did while the framework's
 * binder owned it.
 *
 * <p>A page beyond the last is <b>not</b> among them: it is a legitimate question about a real
 * selection, and the read answers it with an empty page carrying that selection's true totals.
 */
public final class PagingParameters {

  /** The page a caller who names none is reading, and the base the wire counts from. */
  public static final int DEFAULT_PAGE = 1;

  /** How many entries a caller who names no size gets. */
  public static final int DEFAULT_SIZE = 50;

  private static final int MIN_PAGE = 1;
  private static final int MIN_SIZE = 1;
  private static final int MAX_SIZE = 100;

  private PagingParameters() {
  }

  /**
   * The two parameters as the store's pageable: <b>0-based, and carrying no ordering</b>.
   *
   * <p>The ordering is deliberately absent. It is the use case's to build, from the closed set of
   * values its operation offers, and a pageable arriving with one would be a second source for a
   * clause that must have exactly one — which for a native statement is the difference between an
   * ordering this system named and a caller's text reaching {@code ORDER BY}.
   *
   * <p>The {@code - 1} is the change of base and nothing else. It is written as a literal rather
   * than as the minimum page, which happens to be the same number for a different reason, so that
   * raising the floor could never silently move the origin with it.
   */
  public static Pageable pageableOf(@Nullable String page, @Nullable String size) {
    int requestedPage = wholeNumberOf("page", page, DEFAULT_PAGE);
    int requestedSize = wholeNumberOf("size", size, DEFAULT_SIZE);
    refuseOutsideItsRange(requestedPage, requestedSize);
    return Pageable.from(requestedPage - 1, requestedSize);
  }

  private static void refuseOutsideItsRange(int page, int size) {
    if (page < MIN_PAGE) {
      throw refused("page is 1-based, so it must be %d or greater".formatted(MIN_PAGE));
    }
    if (size < MIN_SIZE || size > MAX_SIZE) {
      throw refused("size must be between %d and %d".formatted(MIN_SIZE, MAX_SIZE));
    }
  }
}
