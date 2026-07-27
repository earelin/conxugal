package gal.conxugal.application.scheduling.organos;

import gal.conxugal.domain.organo.ImportOrganos;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;

@Singleton
class ImportOrganosScheduler {

  private final ImportOrganos importOrganos;

  ImportOrganosScheduler(ImportOrganos importOrganos) {
    this.importOrganos = importOrganos;
  }

  @Scheduled(cron = "${conxugal.organos.import.schedule:0 0 3 * * *}", zoneId = "Europe/Madrid")
  @ExecuteOn(TaskExecutors.BLOCKING)
  void run() {
    importOrganos.run();
  }
}
