package gal.conxugal.application.rest.admin.importruns;

import com.fasterxml.jackson.annotation.JsonInclude;
import gal.conxugal.commons.text.Whitespace;
import gal.conxugal.domain.importrun.ImportRunOrganoCoverage;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * One Órgano a run covers, for one family, and how it fared. A run asked for both families of an
 * Órgano answers two of these, which is what lets a reader say what each family's import did.
 *
 * <p>The reason says what the state cannot: which of the covered Órganos failed and on what, why a
 * skipped one was passed over, and which withdrawal stopped a stopped one.
 */
@Serdeable
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ImportRunOrganoResponse(
    UUID organoId,
    String family,
    String state,
    int added,
    int refreshed,
    @Nullable String failureReason) {

  /** The longest reason this answers with, matching the bound the contract declares. */
  private static final int LONGEST_REASON = 500;

  private static final Pattern BLANK_RUN = Pattern.compile("[\\s\\p{Z}\\x{85}]+");

  static ImportRunOrganoResponse of(ImportRunOrganoCoverage coverage) {
    return new ImportRunOrganoResponse(
        coverage.organoId().value(),
        coverage.family().name(),
        coverage.state().name(),
        coverage.added(),
        coverage.refreshed(),
        reasonOf(coverage.failureReason()));
  }

  /**
   * The recorded reason as a client may see it: one line, bounded, and absent when it says
   * nothing.
   *
   * <p>A failed Órgano's reason is whatever the walk caught, so it arrives carrying the shape of
   * whatever went wrong — a wrapped statement across several lines, a message longer than the
   * bound, or an empty one. This is the boundary that decides what leaves, and what leaves is a
   * summary rather than the internals of a failure.
   */
  private static @Nullable String reasonOf(@Nullable String failureReason) {
    if (failureReason == null) {
      return null;
    }
    String oneLine = Whitespace.strip(BLANK_RUN.matcher(failureReason).replaceAll(" "));
    if (oneLine.isEmpty()) {
      return null;
    }
    return oneLine.length() <= LONGEST_REASON
        ? oneLine
        : oneLine.substring(0, LONGEST_REASON - 1) + "…";
  }
}
