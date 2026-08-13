package gal.conxugal.application.rest.admin.importruns;

import com.fasterxml.jackson.annotation.JsonInclude;
import gal.conxugal.domain.importrun.ImportRunOrganoCoverage;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import java.util.UUID;

/**
 * One Órgano a run covers, and how it fared. The reason says what the state cannot: which of the
 * covered Órganos failed and on what, why a skipped one was passed over, and which withdrawal
 * stopped a stopped one.
 */
@Serdeable
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ImportRunOrganoResponse(
    UUID organoId, String state, int added, int refreshed, @Nullable String failureReason) {

  static ImportRunOrganoResponse of(ImportRunOrganoCoverage coverage) {
    return new ImportRunOrganoResponse(
        coverage.organoId().value(),
        coverage.state().name(),
        coverage.added(),
        coverage.refreshed(),
        coverage.failureReason());
  }
}
