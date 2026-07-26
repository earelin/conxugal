package gal.conxugal.application.rest.admin.organos;

import gal.conxugal.domain.organo.ImportOutcome;
import io.micronaut.serde.annotation.Serdeable;

/**
 * The result of one Órganos import run: its status plus the added/refreshed/deactivated
 * counts, which are always 0 unless {@code status} is {@code SUCCESS}.
 */
@Serdeable
public record ImportOutcomeResponse(
    ImportOutcome.Status status, int added, int refreshed, int deactivated) {

  static ImportOutcomeResponse of(ImportOutcome outcome) {
    return new ImportOutcomeResponse(
        outcome.status(), outcome.added(), outcome.refreshed(), outcome.deactivated());
  }
}
