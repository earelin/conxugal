package gal.conxugal.domain.organo.taxonomia;

import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * Adds a term to the taxonomy, at the root when {@code parentId} is null or under an existing
 * term otherwise. Refuses an unknown parent and a name already taken by one of the siblings it
 * would join. The name is stored stripped; its blankness and length are rejected at the edge.
 */
@Singleton
public class CreateTermo {

  private final TermoRepository termoRepository;

  public CreateTermo(TermoRepository termoRepository) {
    this.termoRepository = termoRepository;
  }

  public Termo create(String name, @Nullable TermoId parentId) {
    if (parentId != null && termoRepository.findById(parentId).isEmpty()) {
      throw new TermoNotFoundException(parentId);
    }
    String storedName = name.strip();
    SiblingNames.requireAvailable(
        termoRepository.findByParentId(parentId), storedName, null, parentId);
    return termoRepository.insert(new Termo(storedName, parentId));
  }
}
