package gal.conxugal.domain.organo;

/**
 * Thrown when a classification operation names an Órgano id that doesn't exist. Distinct from
 * the unknown-term type so an unknown Órgano and an unknown term map to their own problem types
 * rather than sharing one.
 */
public class OrganoNotFoundException extends RuntimeException {

  private final OrganoId organoId;

  public OrganoNotFoundException(OrganoId organoId) {
    super("No Órgano exists with id: %s".formatted(organoId));
    this.organoId = organoId;
  }

  public OrganoId getOrganoId() {
    return organoId;
  }
}
