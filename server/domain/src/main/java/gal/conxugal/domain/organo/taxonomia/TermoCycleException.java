package gal.conxugal.domain.organo.taxonomia;

import java.util.UUID;

/**
 * Thrown by {@link MoveTermo} when the target parent is the term itself or one of its own
 * descendants, which would detach that branch from the tree into a cycle.
 */
public class TermoCycleException extends RuntimeException {

  private final UUID termoId;
  private final UUID targetParentId;

  public TermoCycleException(UUID termoId, UUID targetParentId) {
    super("Cannot move term %s under itself or one of its descendants: %s"
        .formatted(termoId, targetParentId));
    this.termoId = termoId;
    this.targetParentId = targetParentId;
  }

  public UUID getTermoId() {
    return termoId;
  }

  public UUID getTargetParentId() {
    return targetParentId;
  }
}
