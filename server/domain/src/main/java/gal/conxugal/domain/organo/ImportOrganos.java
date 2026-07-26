package gal.conxugal.domain.organo;

import jakarta.inject.Singleton;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Import & reconciliation use case: pulls the entire catalogue from {@link OrganoSource} and
 * hands it to {@link OrganoReconciler} to reconcile against {@link OrganoRepository} — insert
 * new entries active, refresh a matched entry's name in place and reactivate it if it had been
 * deactivated, and deactivate stored entries absent from the source — within a single
 * transaction. Guarded so at most one run proceeds at a time; the guard is a plain in-process
 * flag, so it only serializes runs within this JVM — running more than one instance of the
 * service would need a shared lock instead.
 */
@Singleton
public class ImportOrganos {

  private static final Logger LOG = LoggerFactory.getLogger(ImportOrganos.class);

  private final OrganoSource organoSource;
  private final OrganoReconciler organoReconciler;
  private final AtomicBoolean running = new AtomicBoolean(false);

  public ImportOrganos(OrganoSource organoSource, OrganoReconciler organoReconciler) {
    this.organoSource = organoSource;
    this.organoReconciler = organoReconciler;
  }

  public ImportOutcome run() {
    if (!running.compareAndSet(false, true)) {
      return ImportOutcome.alreadyRunning();
    }
    try {
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
    } finally {
      running.set(false);
    }
  }
}
