package gal.conxugal.domain.contrato;

import gal.conxugal.domain.organo.OrganoId;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * Every Órgano's contratos menores import status, keyed by Órgano.
 *
 * <p>Only Órganos whose import has started have a state row, so the map is deliberately partial:
 * an Órgano missing from it has never been imported. Callers read that absence through
 * {@link #statusOf}, which answers {@link ContratosMenoresImportStatus#NEVER_STARTED} for it —
 * a never-loaded Órgano must never read as up to date, and returning a missing entry to be
 * interpreted at each call site is how that would eventually happen.
 *
 * <p>It answers for the whole catalogue in one read rather than per Órgano, because the
 * administrator's catalogue read needs all of them at once.
 */
@Singleton
public class ListContratosMenoresImportState {

  private final ContratosMenoresImportStateRepository importStateRepository;

  public ListContratosMenoresImportState(
      ContratosMenoresImportStateRepository importStateRepository) {
    this.importStateRepository = importStateRepository;
  }

  public Map<OrganoId, ContratosMenoresImportStatus> byOrgano() {
    Map<OrganoId, ContratosMenoresImportStatus> statuses = new HashMap<>();
    for (ContratosMenoresImportState state : importStateRepository.findAll()) {
      statuses.put(state.organoId(), state.state());
    }
    return Map.copyOf(statuses);
  }

  /** The status an Órgano holds in a map from {@link #byOrgano()}, never started when absent. */
  public static ContratosMenoresImportStatus statusOf(
      Map<OrganoId, ContratosMenoresImportStatus> statuses, OrganoId organoId) {
    return statuses.getOrDefault(organoId, ContratosMenoresImportStatus.NEVER_STARTED);
  }
}
