package gal.conxugal.domain.organo.taxonomia;

import jakarta.inject.Singleton;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Re-parents a term, or moves it to the root when {@code newParentId} is null. Refuses an
 * unknown term, an unknown target parent, a target that is the term itself or one of its
 * descendants, and a name already taken among the siblings the term would land beside.
 *
 * <p>The target parent is looked up <em>before</em> the cycle guard walks the ancestry: walking
 * up from a parent that does not exist would end the walk without finding the cycle it looks
 * for, turning what the caller should see as an unknown term into an accepted move.
 */
@Singleton
public class MoveTermo {

  private final TermoRepository termoRepository;

  public MoveTermo(TermoRepository termoRepository) {
    this.termoRepository = termoRepository;
  }

  public void move(UUID termoId, @Nullable UUID newParentId) {
    Termo termo =
        termoRepository.findById(termoId).orElseThrow(() -> new TermoNotFoundException(termoId));
    if (newParentId != null) {
      if (termoRepository.findById(newParentId).isEmpty()) {
        throw new TermoNotFoundException(newParentId);
      }
      requireNotSelfOrDescendant(termoId, newParentId);
    }
    SiblingNames.requireAvailable(
        termoRepository.findByParentId(newParentId), termo.name(), termoId, newParentId);
    termoRepository.updateParentId(termoId, newParentId);
  }

  private void requireNotSelfOrDescendant(UUID termoId, UUID targetParentId) {
    UUID ancestorId = targetParentId;
    while (ancestorId != null) {
      if (termoId.equals(ancestorId)) {
        throw new TermoCycleException(termoId, targetParentId);
      }
      ancestorId = termoRepository.findById(ancestorId).map(Termo::parentId).orElse(null);
    }
  }
}
