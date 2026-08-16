package gal.conxugal.domain.organo;

import jakarta.inject.Singleton;

/**
 * One Órgano, by its identity. Unlike the reads that narrow the catalogue, this one answers for
 * any stored Órgano: which contracts a reader may see is a property of the contracts, so an
 * Órgano nothing is visible for is still an Órgano that exists and is still answered here.
 *
 * <p>An unknown id is refused rather than answered with nothing, because a caller that named one
 * has a wrong identifier and not an empty result.
 */
@Singleton
public class ViewOrgano {

  private final OrganoRepository organoRepository;

  public ViewOrgano(OrganoRepository organoRepository) {
    this.organoRepository = organoRepository;
  }

  public OrganoDeContratacion view(OrganoId id) {
    return organoRepository.findById(id).orElseThrow(() -> new OrganoNotFoundException(id));
  }
}
