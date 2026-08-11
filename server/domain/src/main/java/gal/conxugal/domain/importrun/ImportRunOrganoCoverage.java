package gal.conxugal.domain.importrun;

import gal.conxugal.domain.organo.OrganoId;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One Órgano a run covers, and how it fared. The reason says what the state cannot: which of the
 * covered Órganos failed and on what, or why a {@link ImportRunOrganoState#SKIPPED} one was passed
 * over. The states that speak for themselves carry none.
 */
public record ImportRunOrganoCoverage(
    OrganoId organoId,
    ImportRunOrganoState state,
    int added,
    int refreshed,
    @Nullable String failureReason) {

  public ImportRunOrganoCoverage {
    Objects.requireNonNull(organoId, "organoId must not be null");
    Objects.requireNonNull(state, "state must not be null");
  }
}
