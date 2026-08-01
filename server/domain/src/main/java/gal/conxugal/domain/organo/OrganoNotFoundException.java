package gal.conxugal.domain.organo;

import java.util.UUID;

/**
 * Thrown when a classification operation names an Órgano id that doesn't exist. Distinct from
 * the unknown-term type so an unknown Órgano and an unknown term map to their own problem types
 * rather than sharing one.
 */
public class OrganoNotFoundException extends RuntimeException {

  private final UUID organoId;

  public OrganoNotFoundException(UUID organoId) {
    super("No Órgano exists with id: " + organoId);
    this.organoId = organoId;
  }

  public UUID getOrganoId() {
    return organoId;
  }
}
