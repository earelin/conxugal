package gal.conxugal.domain.organo.taxonomia;

import gal.conxugal.domain.organo.OrganoRepository;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import java.util.UUID;

/**
 * Removes a term from the taxonomy. Refuses an unknown term and one that still has child terms;
 * otherwise it returns the Órganos placed directly in it to the unclassified set and deletes the
 * term. No Órgano is ever deleted.
 *
 * <p>The clearing and the delete share one transaction: without it a failure between them would
 * leave Órganos pointing at a term that is gone, which the foreign key then refuses.
 */
@Singleton
public class DeleteTermo {

  private final TermoRepository termoRepository;
  private final OrganoRepository organoRepository;

  public DeleteTermo(TermoRepository termoRepository, OrganoRepository organoRepository) {
    this.termoRepository = termoRepository;
    this.organoRepository = organoRepository;
  }

  @Transactional
  public void delete(UUID termoId) {
    if (termoRepository.findById(termoId).isEmpty()) {
      throw new TermoNotFoundException(termoId);
    }
    if (termoRepository.existsByParentId(termoId)) {
      throw new TermoHasChildrenException(termoId);
    }
    organoRepository.clearPlacementsByTermo(termoId);
    termoRepository.deleteById(termoId);
  }
}
