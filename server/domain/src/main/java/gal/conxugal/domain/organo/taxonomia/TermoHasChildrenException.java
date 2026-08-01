package gal.conxugal.domain.organo.taxonomia;

/**
 * Thrown by {@link DeleteTermo} when the term still has child terms. Deleting it would take a
 * whole subtree with it, so the admin moves or removes the children first.
 */
public class TermoHasChildrenException extends RuntimeException {

  private final TermoId termoId;

  public TermoHasChildrenException(TermoId termoId) {
    super("Cannot delete term %s while it has child terms".formatted(termoId));
    this.termoId = termoId;
  }

  public TermoId getTermoId() {
    return termoId;
  }
}
