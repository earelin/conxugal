package gal.conxugal.domain.organo.taxonomia;

import jakarta.inject.Singleton;
import java.util.List;

/**
 * Lists every term, each naming its parent. The tree is not assembled here: the flat list plus
 * each term's {@code parentId} is enough for a caller to build it, and no term carries its
 * children or the Órganos filed under it.
 */
@Singleton
public class ListTermos {

  private final TermoRepository termoRepository;

  public ListTermos(TermoRepository termoRepository) {
    this.termoRepository = termoRepository;
  }

  public List<Termo> list() {
    return termoRepository.findAllOrderByName();
  }
}
