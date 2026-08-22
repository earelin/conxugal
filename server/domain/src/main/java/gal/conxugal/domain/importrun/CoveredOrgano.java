package gal.conxugal.domain.importrun;

import gal.conxugal.domain.organo.OrganoId;
import java.util.Objects;

/**
 * One Órgano a claim sets out to cover, for one family. The pair is what a claim enumerates, so a
 * run asked for both families of one Órgano is still a single claim — rather than a second one
 * against a guard the first would already be holding.
 */
public record CoveredOrgano(OrganoId organoId, ContractFamily family) {

  public CoveredOrgano {
    Objects.requireNonNull(organoId, "organoId must not be null");
    Objects.requireNonNull(family, "family must not be null");
  }
}
