package gal.conxugal.domain.organo;

import jakarta.inject.Singleton;

/**
 * Opts an Órgano into having its contratos menores imported. Refuses an unknown Órgano with the
 * same type the classification use cases raise.
 *
 * <p>Marking an already-marked Órgano is not a failure: the caller's intent is already satisfied,
 * so it writes nothing and returns. It is a separate use case from {@link UnmarkOrganoForImport}
 * rather than one flag-setter because the two gain different rules once there is an importer —
 * marking requests an import and can have that import refused, while unmarking stops one.
 */
@Singleton
public class MarkOrganoForImport {

  private final OrganoRepository organoRepository;

  public MarkOrganoForImport(OrganoRepository organoRepository) {
    this.organoRepository = organoRepository;
  }

  public void mark(OrganoId organoId) {
    OrganoDeContratacion organo =
        organoRepository
            .findById(organoId)
            .orElseThrow(() -> new OrganoNotFoundException(organoId));
    if (organo.importable()) {
      return;
    }
    organoRepository.updateImportable(organoId, true);
  }
}
