package gal.conxugal.domain.organo;

import gal.conxugal.domain.organo.taxonomia.TermoNotFoundException;
import gal.conxugal.domain.organo.taxonomia.TermoRepository;
import jakarta.inject.Singleton;
import java.util.UUID;

/**
 * Files an Órgano under a term, replacing whatever term it was in. Refuses an unknown Órgano and
 * an unknown term, each with its own type. Both checks precede the write, so a refusal leaves the
 * placement as it was.
 *
 * <p>The placement is a single column on the Órgano row, so setting it is what makes an Órgano
 * belong to exactly one term: there is no path by which it accumulates a second.
 */
@Singleton
public class AssignOrganoToTermo {

  private final OrganoRepository organoRepository;
  private final TermoRepository termoRepository;

  public AssignOrganoToTermo(OrganoRepository organoRepository, TermoRepository termoRepository) {
    this.organoRepository = organoRepository;
    this.termoRepository = termoRepository;
  }

  public void assign(UUID organoId, UUID termoId) {
    if (organoRepository.findById(organoId).isEmpty()) {
      throw new OrganoNotFoundException(organoId);
    }
    if (termoRepository.findById(termoId).isEmpty()) {
      throw new TermoNotFoundException(termoId);
    }
    organoRepository.updateTermo(organoId, termoId);
  }
}
