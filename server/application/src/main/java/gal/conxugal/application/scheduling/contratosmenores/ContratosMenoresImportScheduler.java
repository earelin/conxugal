package gal.conxugal.application.scheduling.contratosmenores;

import gal.conxugal.application.contrato.StartContratosMenoresImport;
import gal.conxugal.domain.importrun.ImportAlreadyRunningException;
import gal.conxugal.domain.importrun.ImportRunId;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The recurring trigger for the contratos menores import: a tick that asks for a sweep of every
 * eligible Órgano and decides nothing else. Which Órganos are covered, which mode each takes, the
 * order they are taken in and whether an import may start at all are all answered where every
 * other trigger asks them, so this one cannot disagree.
 *
 * <p>It declares no executor of its own, unlike the catalogue import's scheduler: that one holds
 * its thread for the whole run, while {@code startAll()} claims in milliseconds and hands the
 * walking to the import's own executor. The default {@code scheduled} pool is enough for a tick
 * that short.
 *
 * <p>A refusal while another import holds the guard is the system working as intended — asking
 * again later is the schedule's own next tick — so it is logged rather than allowed to reach the
 * scheduled-task handler as an error.
 */
@Singleton
class ContratosMenoresImportScheduler {

  private static final Logger LOG = LoggerFactory.getLogger(ContratosMenoresImportScheduler.class);

  private final StartContratosMenoresImport startImport;

  ContratosMenoresImportScheduler(StartContratosMenoresImport startImport) {
    this.startImport = startImport;
  }

  @Scheduled(cron = "${conxugal.contratos-menores.import.schedule}", zoneId = "Europe/Madrid")
  void run() {
    try {
      ImportRunId runId = startImport.startAll();
      LOG.info("Contratos menores import run {} started on schedule", runId);
    } catch (ImportAlreadyRunningException e) {
      LOG.info("Contratos menores import skipped: another import is already in progress");
    }
  }
}
