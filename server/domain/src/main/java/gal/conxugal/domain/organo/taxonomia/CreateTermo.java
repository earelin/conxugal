package gal.conxugal.domain.organo.taxonomia;

import gal.conxugal.commons.text.Whitespace;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * Adds a term to the taxonomy, at the root when {@code parentId} is null or under an existing
 * term otherwise. Refuses an unknown parent and a name already taken by one of the siblings it
 * would join. The name is stored stripped, and a name with nothing left once stripped is
 * refused here rather than stored — the edge rejects it first, but the stripping and the rule
 * it has to satisfy belong on the same side. Length stays a concern of the edge.
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
    if (Whitespace.isBlank(name)) {
      throw new IllegalArgumentException("name must not be blank");
    }
    String storedName = Whitespace.strip(name);
    SiblingNames.requireAvailable(
        termoRepository.findByParentId(parentId), storedName, null, parentId);
    return termoRepository.insert(new Termo(storedName, parentId));
  }
}
