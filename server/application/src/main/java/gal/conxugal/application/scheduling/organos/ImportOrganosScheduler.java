package gal.conxugal.application.scheduling.organos;

import gal.conxugal.domain.organo.ImportOrganos;
import gal.conxugal.domain.organo.ImportOutcome;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class ImportOrganosScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(ImportOrganosScheduler.class);

  private final ImportOrganos importOrganos;

  ImportOrganosScheduler(ImportOrganos importOrganos) {
    this.importOrganos = importOrganos;
  }

  @Scheduled(
      cron = "${conxugal.organos.import.schedule}",
      zoneId = "Europe/Madrid",
      scheduler = "organos-import")
  // PMD flags the default below as redundant since the switch already covers every known
  // Status; checkstyle requires it regardless, so the two tools disagree on this pattern.
  @SuppressWarnings("PMD.ExhaustiveSwitchHasDefault")
  void run() {
    ImportOutcome outcome = importOrganos.run();
    switch (outcome.status()) {
      case SUCCESS ->
          LOG.info(
              "Órganos import completed: {} added, {} refreshed, {} deactivated",
              outcome.added(),
              outcome.refreshed(),
              outcome.deactivated());
      case ALREADY_RUNNING ->
          LOG.info("Órganos import skipped: another run is already in progress");
      case FAILURE -> {} // already logged by ImportOrganos itself
      default ->
          throw new IllegalStateException("Unexpected import outcome status: " + outcome.status());
    }
  }
}
