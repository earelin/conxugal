package gal.conxugal.domain.organo.taxonomia;

import jakarta.inject.Singleton;
import java.util.HashSet;
import java.util.Set;
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

  public void move(TermoId termoId, @Nullable TermoId newParentId) {
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

  /**
   * Walks up from the target to the root, refusing the move if it passes through the term being
   * moved. Revisiting an id means the stored ancestry already loops without involving this term
   * — two concurrent moves can jointly produce that, since the rules are not serialised — so the
   * walk refuses rather than circling it forever.
   */
  private void requireNotSelfOrDescendant(TermoId termoId, TermoId targetParentId) {
    Set<TermoId> visited = new HashSet<>();
    TermoId ancestorId = targetParentId;
    while (ancestorId != null) {
      if (termoId.equals(ancestorId) || !visited.add(ancestorId)) {
        throw new TermoCycleException(termoId, targetParentId);
      }
      ancestorId = termoRepository.findById(ancestorId).map(Termo::parentId).orElse(null);
    }
  }
}
