package gal.conxugal.domain.organo;

import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reconciles a fetched source list against {@link OrganoRepository} within a single
 * transaction: insert new entries active, refresh a matched entry's name in place and
 * reactivate it if it had been deactivated, and deactivate stored entries absent from the
 * source.
 *
 * <p>The class itself is public — not to be constructed outside this package, but because a
 * Micronaut Test {@code @MockBean}-generated proxy for {@link ImportOrganos} in another module
 * needs this type in its constructor signature. The constructor stays package-private so {@link
 * ImportOrganos} remains the only way to reach {@link #reconcile}.
 */
@Singleton
public class OrganoReconciler {

  private final OrganoRepository organoRepository;

  OrganoReconciler(OrganoRepository organoRepository) {
    this.organoRepository = organoRepository;
  }

  @Transactional
  public ImportOutcome reconcile(List<OrganoSourceEntry> sourceEntries) {
    Map<String, OrganoSourceEntry> distinctBySourceKey = distinctBySourceKey(sourceEntries);
    Map<String, OrganoDeContratacion> storedByKey =
        organoRepository.findAll().stream()
            .collect(Collectors.toMap(OrganoDeContratacion::sourceKey, Function.identity()));

    ReconciliationCounts addRefreshCounts = addOrRefresh(distinctBySourceKey, storedByKey);
    int deactivated = deactivateAbsentees(storedByKey, distinctBySourceKey);

    return ImportOutcome.success(
        addRefreshCounts.added(), addRefreshCounts.refreshed(), deactivated);
  }

  // Two source entries can carry the same sourceKey (see OrganoRepository's javadoc); the last
  // one wins so at most one row is inserted/matched per key.
  private static Map<String, OrganoSourceEntry> distinctBySourceKey(
      List<OrganoSourceEntry> sourceEntries) {
    return sourceEntries.stream()
        .collect(
            Collectors.toMap(
                OrganoSourceEntry::sourceKey, Function.identity(), (first, second) -> second,
                LinkedHashMap::new));
  }

  private ReconciliationCounts addOrRefresh(
      Map<String, OrganoSourceEntry> distinctBySourceKey,
      Map<String, OrganoDeContratacion> storedByKey) {
    int added = 0;
    int refreshed = 0;
    for (OrganoSourceEntry entry : distinctBySourceKey.values()) {
      OrganoDeContratacion stored = storedByKey.get(entry.sourceKey());
      if (stored == null) {
        organoRepository.insert(newOrgano(entry));
        added++;
      } else if (!stored.name().equals(entry.name()) || !stored.active()) {
        organoRepository.update(stored.id(), entry.name(), true);
        refreshed++;
      }
    }
    return new ReconciliationCounts(added, refreshed);
  }

  private int deactivateAbsentees(
      Map<String, OrganoDeContratacion> storedByKey,
      Map<String, OrganoSourceEntry> distinctBySourceKey) {
    int deactivated = 0;
    for (OrganoDeContratacion stored : storedByKey.values()) {
      if (stored.active() && !distinctBySourceKey.containsKey(stored.sourceKey())) {
        organoRepository.updateActive(stored.id(), false);
        deactivated++;
      }
    }
    return deactivated;
  }

  private static OrganoDeContratacion newOrgano(OrganoSourceEntry entry) {
    return new OrganoDeContratacion(entry.sourceKey(), entry.name());
  }

  private record ReconciliationCounts(int added, int refreshed) {}
}
