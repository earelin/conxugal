package gal.conxugal.domain.contrato;

import gal.conxugal.domain.organo.ContratosMenoresImportStatus;
import java.util.Objects;

/**
 * What one Órgano's walk stored, and how far its history has been loaded once it finished.
 *
 * <p>{@code status} is {@link ContratosMenoresImportStatus#COMPLETE} only when the stored count
 * reached the count the source reports; every other ending — the history floor, a walk that stopped
 * because its run no longer holds the guard — is {@link ContratosMenoresImportStatus#INCOMPLETE},
 * so the Órgano is resumed later rather than treated as loaded.
 * {@link ContratosMenoresImportStatus#NEVER_STARTED} is never answered here: a walk that ran has
 * started by definition.
 */
public record ContratosMenoresImportSummary(
    int added, int refreshed, ContratosMenoresImportStatus status) {

  public ContratosMenoresImportSummary {
    Objects.requireNonNull(status, "status must not be null");
  }

  /** A walk that read the Órgano's history out: its stored count reached the source's own. */
  public static ContratosMenoresImportSummary complete(int added, int refreshed) {
    return new ContratosMenoresImportSummary(
        added, refreshed, ContratosMenoresImportStatus.COMPLETE);
  }

  /** A walk that stopped short of that, whatever stopped it. What it stored still stands. */
  public static ContratosMenoresImportSummary incomplete(int added, int refreshed) {
    return new ContratosMenoresImportSummary(
        added, refreshed, ContratosMenoresImportStatus.INCOMPLETE);
  }
}
