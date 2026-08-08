package gal.conxugal.domain.contrato;

import gal.conxugal.domain.organo.ContratosMenoresImportStatus;
import java.util.Objects;

/**
 * What one Órgano's walk stored, and the state it left that Órgano in.
 *
 * <p>{@code state} is {@link ContratosMenoresImportStatus#COMPLETE} only when the stored count
 * reached the count the source reports; every other ending — the history floor, a walk that stopped
 * because its run no longer holds the guard — is {@link ContratosMenoresImportStatus#INCOMPLETE},
 * so the Órgano is resumed later rather than treated as loaded.
 * {@link ContratosMenoresImportStatus#NEVER_STARTED} is never answered here: a walk that ran has
 * started by definition.
 */
public record ContratosMenoresImportSummary(
    int added, int refreshed, ContratosMenoresImportStatus state) {

  public ContratosMenoresImportSummary {
    Objects.requireNonNull(state, "state must not be null");
  }
}
