package gal.conxugal.domain.importrun;

import gal.conxugal.domain.organo.OrganoId;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One Órgano a run covers, and how it fared. The reason is present only for a
 * {@link ImportRunOrganoState#FAILED} Órgano — an outcome has to name which of the covered
 * Órganos failed, and a state without a reason names the fact without saying anything about it.
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
