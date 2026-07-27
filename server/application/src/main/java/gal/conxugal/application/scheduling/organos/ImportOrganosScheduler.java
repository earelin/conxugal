package gal.conxugal.application.scheduling.organos;

import gal.conxugal.domain.organo.ImportOrganos;
import gal.conxugal.domain.organo.ImportOutcome;
import io.micronaut.scheduling.TaskExecutors;
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
      cron = "${conxugal.organos.import.schedule:0 0 3 * * *}",
      zoneId = "Europe/Madrid",
      scheduler = TaskExecutors.BLOCKING)
  void run() {
    ImportOutcome outcome = importOrganos.run();
    if (outcome.status() == ImportOutcome.Status.ALREADY_RUNNING) {
      LOG.info("Órganos import skipped: another run is already in progress");
    } else if (outcome.status() == ImportOutcome.Status.SUCCESS) {
      LOG.info(
          "Órganos import completed: {} added, {} refreshed, {} deactivated",
          outcome.added(),
          outcome.refreshed(),
          outcome.deactivated());
    }
  }
}
