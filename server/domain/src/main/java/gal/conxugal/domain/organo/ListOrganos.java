package gal.conxugal.domain.organo;

import jakarta.inject.Singleton;
import java.util.List;

/**
 * Lists every stored Órgano, each carrying its own placement. Classified and unclassified alike:
 * an Órgano with a null {@code termoId} is the unclassified case, and callers filter for it rather
 * than asking the server for a partitioned catalogue.
 */
@Singleton
public class ListOrganos {

  private final OrganoRepository organoRepository;

  public ListOrganos(OrganoRepository organoRepository) {
    this.organoRepository = organoRepository;
  }

  public List<OrganoDeContratacion> list() {
    return organoRepository.findAllOrderByName();
  }
}
