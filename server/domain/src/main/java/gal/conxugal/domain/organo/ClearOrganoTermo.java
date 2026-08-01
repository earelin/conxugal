package gal.conxugal.domain.organo;

import jakarta.inject.Singleton;
import java.util.UUID;

/**
 * Returns an Órgano to the unclassified set. Refuses an unknown Órgano with the same type
 * {@link AssignOrganoToTermo} uses.
 *
 * <p>Clearing an Órgano that is already unclassified is not a failure: the caller's intent is
 * already satisfied, so it writes nothing and returns. An admin clicking twice on a row should
 * not see an error the second time.
 */
@Singleton
public class ClearOrganoTermo {

  private final OrganoRepository organoRepository;

  public ClearOrganoTermo(OrganoRepository organoRepository) {
    this.organoRepository = organoRepository;
  }

  public void clear(UUID organoId) {
    OrganoDeContratacion organo =
        organoRepository
            .findById(organoId)
            .orElseThrow(() -> new OrganoNotFoundException(organoId));
    if (organo.termoId() == null) {
      return;
    }
    organoRepository.updateTermo(organoId, null);
  }
}
