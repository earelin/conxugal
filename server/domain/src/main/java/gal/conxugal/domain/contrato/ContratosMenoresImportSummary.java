package gal.conxugal.domain.contrato;

import gal.conxugal.domain.organo.ContratosMenoresImportStatus;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What one Órgano's walk stored, how far its history has been loaded once it finished, and what cut
 * it off if anything did.
 *
 * <p>{@code status} is {@link ContratosMenoresImportStatus#COMPLETE} only when the stored count
 * reached the count the source reports; every other ending — the history floor, a walk cut off —
 * is {@link ContratosMenoresImportStatus#INCOMPLETE}, so the Órgano is resumed later rather than
 * treated as loaded. {@link ContratosMenoresImportStatus#NEVER_STARTED} is never answered here: a
 * walk that ran has started by definition.
 *
 * <p>{@code stoppedBy} is the second, separate fact, and it is a {@link StopReason} rather than a
 * flag because its two values ask opposite things of the caller. Only the walk can tell them apart
 * — asking afterwards would read a catalogue and a guard that may both have moved since. Absent
 * means the walk ended on its own terms.
 */
public record ContratosMenoresImportSummary(
    int added,
    int refreshed,
    ContratosMenoresImportStatus status,
    @Nullable StopReason stoppedBy) {

  public ContratosMenoresImportSummary {
    Objects.requireNonNull(status, "status must not be null");
    if (status == ContratosMenoresImportStatus.NEVER_STARTED) {
      throw new IllegalArgumentException("a walk that ran has started by definition");
    }
    if (stoppedBy != null && status != ContratosMenoresImportStatus.INCOMPLETE) {
      throw new IllegalArgumentException(
          "a walk that was stopped leaves the Órgano incomplete, so a later mark resumes it");
    }
  }

  /** A walk that read the Órgano's history out: its stored count reached the source's own. */
  public static ContratosMenoresImportSummary complete(int added, int refreshed) {
    return new ContratosMenoresImportSummary(
        added, refreshed, ContratosMenoresImportStatus.COMPLETE, null);
  }

  /** A walk that read every window it had and still fell short. What it stored still stands. */
  public static ContratosMenoresImportSummary incomplete(int added, int refreshed) {
    return new ContratosMenoresImportSummary(
        added, refreshed, ContratosMenoresImportStatus.INCOMPLETE, null);
  }

  /**
   * A walk cut off at a batch boundary. What it stored still stands and the cursor is left where it
   * reached, so a later walk resumes from there.
   */
  public static ContratosMenoresImportSummary stopped(
      int added, int refreshed, StopReason stoppedBy) {
    return new ContratosMenoresImportSummary(
        added,
        refreshed,
        ContratosMenoresImportStatus.INCOMPLETE,
        Objects.requireNonNull(stoppedBy, "stoppedBy must not be null"));
  }
}
