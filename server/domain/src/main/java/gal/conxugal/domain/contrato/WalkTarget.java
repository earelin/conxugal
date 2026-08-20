package gal.conxugal.domain.contrato;

import gal.conxugal.domain.importrun.ImportRunId;
import gal.conxugal.domain.organo.OrganoId;
import java.util.Objects;

/**
 * What one walk is about: the Órgano it is loading, the key the source knows that Órgano by, and
 * the run it reports its progress against. None of the three moves while the walk runs, so they
 * travel together rather than as three more parameters on every step of it.
 *
 * <p>Refused rather than carried when any of the three is absent. An Órgano that was never stored
 * has no id, and a walk built on one would fetch a page before failing on it — deep inside the
 * batch store, with the source already asked.
 */
public record WalkTarget(ImportRunId runId, OrganoId organoId, String sourceKey) {

  public WalkTarget {
    Objects.requireNonNull(runId, "runId must not be null");
    Objects.requireNonNull(organoId, "organoId must not be null");
    Objects.requireNonNull(sourceKey, "sourceKey must not be null");
  }
}
