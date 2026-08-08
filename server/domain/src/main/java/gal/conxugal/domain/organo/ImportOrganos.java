package gal.conxugal.domain.organo;

import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.importrun.ImportRunRepository;
import gal.conxugal.domain.importrun.ImportRunState;
import gal.conxugal.domain.importrun.Importer;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Import & reconciliation use case: pulls the entire catalogue from {@link OrganoSource} and
 * hands it to {@link OrganoReconciler} to reconcile against {@link OrganoRepository} — insert
 * new entries active, refresh a matched entry's name in place and reactivate it if it had been
 * deactivated, and deactivate stored entries absent from the source — within a single
 * transaction.
 *
 * <p>It runs only while it holds the system-wide guard, which is durable and shared with every
 * other importer. So {@link ImportOutcome#alreadyRunning()} now means <em>some</em> import holds
 * it, not merely another catalogue import in this JVM: a contratos menores import measured in
 * days refuses every overnight catalogue run it overlaps, which is the cost the guard is worth.
 *
 * <p>Holding it means recording it — a guard cannot see an import that records nothing — so a run
 * claims a row and settles it with its verdict and counts. The run covers no Órganos of its own:
 * this import reports one outcome for the whole catalogue, not one per Órgano.
 *
 * <p><strong>The record never breaks the import.</strong> A completion write that fails is logged
 * and let go, because a reconciliation that committed has already happened and nothing about
 * bookkeeping may undo it. The claim is the exception, and deliberately: a claim that refuses
 * <em>is</em> the refusal.
 */
@Singleton
public class ImportOrganos {

  private static final Logger LOG = LoggerFactory.getLogger(ImportOrganos.class);

  private final OrganoSource organoSource;
  private final OrganoReconciler organoReconciler;
  private final ImportRunRepository importRuns;

  public ImportOrganos(
      OrganoSource organoSource,
      OrganoReconciler organoReconciler,
      ImportRunRepository importRuns) {
    this.organoSource = organoSource;
    this.organoReconciler = organoReconciler;
    this.importRuns = importRuns;
  }

  public ImportOutcome run() {
    Optional<ImportRunId> claimed = importRuns.claim(Importer.ORGANOS, List.of());
    if (claimed.isEmpty()) {
      LOG.info("Órganos import refused: another import holds the guard");
      return ImportOutcome.alreadyRunning();
    }
    ImportRunId runId = claimed.get();
    ImportOutcome outcome;
    try {
      outcome = reconcile();
    } catch (RuntimeException e) {
      // Settling the run is what the in-process flag's finally block used to buy: a reconciliation
      // that throws would otherwise leave the guard held until the abandonment bound expired.
      record(runId, ImportRunState.FAILED, ImportOutcome.failure());
      throw e;
    }
    record(runId, verdictOf(outcome), outcome);
    return outcome;
  }

  private ImportOutcome reconcile() {
    List<OrganoSourceEntry> sourceEntries;
    try {
      sourceEntries = organoSource.fetchAll();
    } catch (OrganoSourceUnavailableException e) {
      LOG.warn("Órganos import failed: source is unavailable or returned an unusable list", e);
      return ImportOutcome.failure();
    }
    // OrganoSource's contract forbids an empty success, but this is defence-in-depth
    // against a source implementation that doesn't honour it: an empty list would
    // otherwise deactivate the entire catalogue.
    if (sourceEntries.isEmpty()) {
      LOG.warn("Órganos import failed: source returned an empty list");
      return ImportOutcome.failure();
    }
    return organoReconciler.reconcile(sourceEntries);
  }

  /**
   * Settles the run, and only the run. The deactivated count is not recorded: the outcome carries
   * it to whoever triggered, and the record holds the columns the guard and the outcome read.
   */
  private void record(ImportRunId runId, ImportRunState verdict, ImportOutcome outcome) {
    try {
      importRuns.complete(runId, verdict, outcome.added(), outcome.refreshed());
    } catch (RuntimeException e) {
      LOG.warn(
          "Órganos import finished as {} but its run {} was not settled; the catalogue is as this"
              + " import left it, and the guard stays held until the abandonment bound passes",
          verdict, runId, e);
    }
  }

  private static ImportRunState verdictOf(ImportOutcome outcome) {
    return outcome.status() == ImportOutcome.Status.SUCCESS
        ? ImportRunState.SUCCEEDED
        : ImportRunState.FAILED;
  }
}
