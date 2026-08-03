package gal.conxugal.domain.organo.taxonomia;

/**
 * Thrown by {@link MoveTermo} when the target parent is the term itself or one of its own
 * descendants, which would detach that branch from the tree into a cycle.
 */
public class TermoCycleException extends RuntimeException {

  private final TermoId termoId;
  private final TermoId targetParentId;

  public TermoCycleException(TermoId termoId, TermoId targetParentId) {
    super("Cannot move term %s under itself or one of its descendants: %s"
        .formatted(termoId, targetParentId));
    this.termoId = termoId;
    this.targetParentId = targetParentId;
  }

  public TermoId getTermoId() {
    return termoId;
  }

  public TermoId getTargetParentId() {
    return targetParentId;
  }
}
