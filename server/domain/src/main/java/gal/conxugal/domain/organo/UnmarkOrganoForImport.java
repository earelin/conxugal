package gal.conxugal.domain.organo;

import jakarta.inject.Singleton;

/**
 * Opts an Órgano back out of having its contratos menores imported. Refuses an unknown Órgano with
 * the same type {@link MarkOrganoForImport} uses.
 *
 * <p>Unmarking an Órgano that is not marked writes nothing and returns, for the same reason
 * marking an already-marked one does. Contratos menores already stored for the Órgano are kept:
 * clearing the mark stops it being updated, it does not discard what was loaded.
 */
@Singleton
public class UnmarkOrganoForImport {

  private final OrganoRepository organoRepository;

  public UnmarkOrganoForImport(OrganoRepository organoRepository) {
    this.organoRepository = organoRepository;
  }

  public void unmark(OrganoId organoId) {
    OrganoDeContratacion organo =
        organoRepository
            .findById(organoId)
            .orElseThrow(() -> new OrganoNotFoundException(organoId));
    if (!organo.importable()) {
      return;
    }
    organoRepository.updateImportable(organoId, false);
  }
}
