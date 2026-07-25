package gal.conxugal.domain.organo;

import java.util.Objects;

/**
 * Outcome of an {@link ImportOrganos} run: a successful reconciliation with its
 * added/refreshed/deactivated counts, a failure (source unreachable or unusable), or that a run
 * was already in progress.
 */
public record ImportOutcome(Status status, int added, int refreshed, int deactivated) {

  public enum Status {
    SUCCESS,
    FAILURE,
    ALREADY_RUNNING
  }

  public ImportOutcome {
    Objects.requireNonNull(status, "status must not be null");
  }

  public static ImportOutcome success(int added, int refreshed, int deactivated) {
    return new ImportOutcome(Status.SUCCESS, added, refreshed, deactivated);
  }

  public static ImportOutcome failure() {
    return new ImportOutcome(Status.FAILURE, 0, 0, 0);
  }

  public static ImportOutcome alreadyRunning() {
    return new ImportOutcome(Status.ALREADY_RUNNING, 0, 0, 0);
  }
}
