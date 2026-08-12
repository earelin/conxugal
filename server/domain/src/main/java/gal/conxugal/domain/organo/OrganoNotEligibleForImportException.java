package gal.conxugal.domain.organo;

/**
 * Thrown when an import is asked for one named Órgano that is not active in the catalogue, not
 * marked by an administrator, or neither.
 *
 * <p>Distinct from the guard already being held, and the distinction is the caller's to act on: an
 * import already running is a matter of timing and asking again later works, while this refusal
 * repeats until the catalogue or the mark changes. An administrator told only that an import is
 * running would go on waiting for one that is never going to start.
 *
 * <p>Distinct too from {@link OrganoNotFoundException}: this Órgano exists, and which half of
 * eligibility it fails is deliberately not reported, because the answer is the same either way.
 */
public class OrganoNotEligibleForImportException extends RuntimeException {

  private final OrganoId organoId;

  public OrganoNotEligibleForImportException(OrganoId organoId) {
    super("Órgano %s is not active and marked for import".formatted(organoId));
    this.organoId = organoId;
  }

  public OrganoId getOrganoId() {
    return organoId;
  }
}
