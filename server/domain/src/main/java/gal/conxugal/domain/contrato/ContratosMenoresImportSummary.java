package gal.conxugal.domain.contrato;

import gal.conxugal.domain.organo.ContratosMenoresImportStatus;
import java.util.Objects;

/**
 * What one Órgano's walk stored, how far its history has been loaded once it finished, and whether
 * it was cut off before it could decide.
 *
 * <p>{@code status} is {@link ContratosMenoresImportStatus#COMPLETE} only when the stored count
 * reached the count the source reports; every other ending — the history floor, a walk cut off —
 * is {@link ContratosMenoresImportStatus#INCOMPLETE}, so the Órgano is resumed later rather than
 * treated as loaded. {@link ContratosMenoresImportStatus#NEVER_STARTED} is never answered here: a
 * walk that ran has started by definition.
 *
 * <p>{@code stopped} is the second, separate fact: the walk was told to stop rather than reaching
 * an ending of its own. Its caller needs it to tell an Órgano deliberately cut off from one that
 * read every window it had and still fell short, and the walk is the only thing that can answer
 * it — asking the catalogue again afterwards would read a mark that may have moved since. The two
 * travel together because a stopped walk is always incomplete, which is what makes a later mark
 * resume it.
 */
public record ContratosMenoresImportSummary(
    int added, int refreshed, ContratosMenoresImportStatus status, boolean stopped) {

  public ContratosMenoresImportSummary {
    Objects.requireNonNull(status, "status must not be null");
    if (stopped && status != ContratosMenoresImportStatus.INCOMPLETE) {
      throw new IllegalArgumentException(
          "a walk that was stopped leaves the Órgano incomplete, so a later mark resumes it");
    }
  }

  /** A walk that read the Órgano's history out: its stored count reached the source's own. */
  public static ContratosMenoresImportSummary complete(int added, int refreshed) {
    return new ContratosMenoresImportSummary(
        added, refreshed, ContratosMenoresImportStatus.COMPLETE, false);
  }

  /** A walk that read every window it had and still fell short. What it stored still stands. */
  public static ContratosMenoresImportSummary incomplete(int added, int refreshed) {
    return new ContratosMenoresImportSummary(
        added, refreshed, ContratosMenoresImportStatus.INCOMPLETE, false);
  }

  /**
   * A walk cut off at a batch boundary — the Órgano stopped being eligible, or the run stopped
   * holding the guard. What it stored still stands and the cursor is left where it reached.
   */
  public static ContratosMenoresImportSummary stoppedShort(int added, int refreshed) {
    return new ContratosMenoresImportSummary(
        added, refreshed, ContratosMenoresImportStatus.INCOMPLETE, true);
  }
}
