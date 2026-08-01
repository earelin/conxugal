package gal.conxugal.domain.organo.taxonomia;

import jakarta.inject.Singleton;

/**
 * Changes a term's name, leaving its place in the tree untouched. Refuses an unknown term and a
 * name already taken by one of its siblings. The name is stored stripped; its blankness and
 * length are rejected at the edge.
 */
@Singleton
public class RenameTermo {

  private final TermoRepository termoRepository;

  public RenameTermo(TermoRepository termoRepository) {
    this.termoRepository = termoRepository;
  }

  public Termo rename(TermoId termoId, String name) {
    Termo termo =
        termoRepository.findById(termoId).orElseThrow(() -> new TermoNotFoundException(termoId));
    String storedName = name.strip();
    SiblingNames.requireAvailable(
        termoRepository.findByParentId(termo.parentId()), storedName, termoId, termo.parentId());
    termoRepository.updateName(termoId, storedName);
    return new Termo(termoId, storedName, termo.parentId());
  }
}
