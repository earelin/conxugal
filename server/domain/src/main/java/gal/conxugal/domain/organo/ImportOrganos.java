package gal.conxugal.domain.organo;

import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Import & reconciliation use case: pulls the entire catalogue from {@link OrganoSource} and
 * reconciles it against {@link OrganoRepository} — insert new entries inactive, refresh matched
 * entries in place (which also reactivates them), and deactivate stored entries absent from the
 * source — within a single transaction, guarded so at most one run proceeds at a time.
 */
@Singleton
public class ImportOrganos {

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
        return ImportOutcome.failure();
      }
      return reconcile(sourceEntries);
    } finally {
      running.set(false);
    }
  }

  @Transactional
  protected ImportOutcome reconcile(List<OrganoSourceEntry> sourceEntries) {
    Set<String> sourceKeys =
        sourceEntries.stream().map(OrganoSourceEntry::sourceKey).collect(Collectors.toSet());
    Map<String, OrganoDeContratacion> storedByKey =
        organoRepository.findAllBySourceKeyIn(sourceKeys).stream()
            .collect(Collectors.toMap(OrganoDeContratacion::sourceKey, Function.identity()));

    int added = 0;
    int refreshed = 0;
    for (OrganoSourceEntry entry : sourceEntries) {
      OrganoDeContratacion stored = storedByKey.get(entry.sourceKey());
      if (stored == null) {
        organoRepository.insert(newOrgano(entry));
        added++;
      } else {
        organoRepository.update(stored.id(), entry.name(), true);
        refreshed++;
      }
    }

    int deactivated = 0;
    for (OrganoDeContratacion stored : organoRepository.findAll()) {
      if (stored.active() && !sourceKeys.contains(stored.sourceKey())) {
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
