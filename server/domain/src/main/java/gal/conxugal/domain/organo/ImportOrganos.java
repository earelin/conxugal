package gal.conxugal.domain.organo;

import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Import & reconciliation use case: pulls the entire catalogue from {@link OrganoSource} and
 * reconciles it against {@link OrganoRepository} — insert new entries inactive, refresh a matched
 * entry's name in place when it changed (never touching its active state), and deactivate stored
 * entries absent from the source — within a single transaction. Guarded so at most one run
 * proceeds at a time; the guard is a plain in-process flag, so it only serializes runs within
 * this JVM — running more than one instance of the service would need a shared lock instead.
 */
@Singleton
public class ImportOrganos {

  private static final Logger LOG = LoggerFactory.getLogger(ImportOrganos.class);

  private final OrganoSource organoSource;
  private final OrganoRepository organoRepository;
  private final AtomicBoolean running = new AtomicBoolean(false);

  public ImportOrganos(OrganoSource organoSource, OrganoRepository organoRepository) {
    this.organoSource = organoSource;
    this.organoRepository = organoRepository;
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
      return reconcile(sourceEntries);
    } finally {
      running.set(false);
    }
  }

  @Transactional
  protected ImportOutcome reconcile(List<OrganoSourceEntry> sourceEntries) {
    // Two source entries can carry the same sourceKey (see OrganoRepository's javadoc); the
    // last one wins so at most one row is inserted/matched per key.
    Map<String, OrganoSourceEntry> distinctBySourceKey =
        sourceEntries.stream()
            .collect(
                Collectors.toMap(
                    OrganoSourceEntry::sourceKey, Function.identity(), (first, second) -> second,
                    LinkedHashMap::new));
    Map<String, OrganoDeContratacion> storedByKey =
        organoRepository.findAll().stream()
            .collect(Collectors.toMap(OrganoDeContratacion::sourceKey, Function.identity()));

    int added = 0;
    int refreshed = 0;
    for (OrganoSourceEntry entry : distinctBySourceKey.values()) {
      OrganoDeContratacion stored = storedByKey.get(entry.sourceKey());
      if (stored == null) {
        organoRepository.insert(newOrgano(entry));
        added++;
      } else if (!stored.name().equals(entry.name())) {
        organoRepository.update(stored.id(), entry.name(), stored.active());
        refreshed++;
      }
    }

    int deactivated = 0;
    for (OrganoDeContratacion stored : storedByKey.values()) {
      if (stored.active() && !distinctBySourceKey.containsKey(stored.sourceKey())) {
        organoRepository.updateActive(stored.id(), false);
        deactivated++;
      }
    }

    return ImportOutcome.success(added, refreshed, deactivated);
  }

  private static OrganoDeContratacion newOrgano(OrganoSourceEntry entry) {
    return new OrganoDeContratacion(entry.sourceKey(), entry.name());
  }
}
