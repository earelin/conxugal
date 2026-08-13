package gal.conxugal.domain.importrun;

import gal.conxugal.domain.organo.OrganoId;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One Órgano a run covers, and how it fared. The reason says what the state cannot: which of the
 * covered Órganos failed and on what, why a {@link ImportRunOrganoState#SKIPPED} one was passed
 * over, and which of the two withdrawals stopped a {@link ImportRunOrganoState#STOPPED} one — a
 * mark taken back before the run reached it reads the same on the row as one taken back mid-walk,
 * and only the reason tells an administrator which happened.
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
