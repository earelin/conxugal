package gal.conxugal.application.rest.admin.organos;

import gal.conxugal.domain.organo.ImportOutcome;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ImportOutcomeResponse(
    ImportOutcome.Status status, int added, int refreshed, int deactivated) {

  static ImportOutcomeResponse of(ImportOutcome outcome) {
    return new ImportOutcomeResponse(
        outcome.status(), outcome.added(), outcome.refreshed(), outcome.deactivated());
  }
}
