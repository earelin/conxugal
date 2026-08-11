package gal.conxugal.domain.contrato;

import gal.conxugal.domain.importrun.ImportRunId;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What asking for a contratos menores import answers: the identity of the run that was started, or
 * the reason none was.
 *
 * <p>The two refusals are separate values rather than one because they are the caller's to tell
 * apart. An import already running is a matter of timing and asking again later works; an Órgano
 * that is inactive or unmarked will refuse every time until the catalogue or the mark changes, and
 * an administrator told only that "an import is running" would go on waiting for one that is never
 * going to start.
 */
public record ContratosMenoresImportClaim(Status status, @Nullable ImportRunId runId) {

  public enum Status {
    CLAIMED,
    ALREADY_RUNNING,
    NOT_ELIGIBLE
  }

  public ContratosMenoresImportClaim {
    Objects.requireNonNull(status, "status must not be null");
    if (status == Status.CLAIMED && runId == null) {
      throw new IllegalArgumentException("a claimed import carries the identity of its run");
    }
    if (status != Status.CLAIMED && runId != null) {
      throw new IllegalArgumentException("a refused import started no run to identify");
    }
  }

  public static ContratosMenoresImportClaim claimed(ImportRunId runId) {
    return new ContratosMenoresImportClaim(
        Status.CLAIMED, Objects.requireNonNull(runId, "runId must not be null"));
  }

  /** Some import already holds the guard — this one, another Órgano's, or the catalogue's. */
  public static ContratosMenoresImportClaim alreadyRunning() {
    return new ContratosMenoresImportClaim(Status.ALREADY_RUNNING, null);
  }

  /** The named Órgano is not active in the catalogue, not marked for import, or neither. */
  public static ContratosMenoresImportClaim notEligible() {
    return new ContratosMenoresImportClaim(Status.NOT_ELIGIBLE, null);
  }
}
