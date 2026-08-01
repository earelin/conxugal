package gal.conxugal.domain.organo.taxonomia;

import jakarta.inject.Singleton;

/**
 * Removes a term from the taxonomy. Refuses an unknown term and one that still has child terms;
 * otherwise it deletes the term, and the Órganos placed directly in it return to the
 * unclassified set. No Órgano is ever deleted.
 *
 * <p>The placements are cleared by the {@code ON DELETE SET NULL} foreign key rather than by a
 * second write here, so the delete is a single statement that cannot half-succeed. The
 * child-term rule stays in this class: the parent foreign key is deliberately left at
 * {@code NO ACTION}, since cascading there would silently delete a whole subtree, which R16
 * forbids.
 */
@Singleton
public class DeleteTermo {

  private final TermoRepository termoRepository;

  public DeleteTermo(TermoRepository termoRepository) {
    this.termoRepository = termoRepository;
  }

  public void delete(TermoId termoId) {
    if (termoRepository.findById(termoId).isEmpty()) {
      throw new TermoNotFoundException(termoId);
    }
    if (termoRepository.existsByParentId(termoId)) {
      throw new TermoHasChildrenException(termoId);
    }
    termoRepository.deleteById(termoId);
  }
}
