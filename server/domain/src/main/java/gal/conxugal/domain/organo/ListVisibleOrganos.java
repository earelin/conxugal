package gal.conxugal.domain.organo;

import jakarta.inject.Singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lists the Órganos a reader may be offered: those holding at least one visible contract, of any
 * family. The predicate is nowhere stored — no column, no count, no flag — so an Órgano enters the
 * set the moment its first visible contract is stored and leaves it when its last one goes, with
 * no administrator action either way.
 *
 * <p>Every {@link OrganosWithVisibleContracts} the container finds is asked, and the answers are
 * unioned: this use case knows what a contract family is only as a bean, never as a table. The
 * catalogue is then filtered in place, so the repository's Galician-collated name order survives
 * the narrowing untouched.
 *
 * <p>{@link ListOrganos} still answers the whole catalogue, and the two are deliberately separate:
 * the administration read serves it, and a shared filter would narrow that one too.
 */
@Singleton
public class ListVisibleOrganos {

  private static final Logger LOG = LoggerFactory.getLogger(ListVisibleOrganos.class);

  private final OrganoRepository organoRepository;
  private final List<OrganosWithVisibleContracts> contractFamilies;

  public ListVisibleOrganos(
      OrganoRepository organoRepository, List<OrganosWithVisibleContracts> contractFamilies) {
    this.organoRepository = organoRepository;
    this.contractFamilies = List.copyOf(contractFamilies);
    if (this.contractFamilies.isEmpty()) {
      // Not a failure the container raises: it injects an empty list rather than refusing, so
      // without this the read would answer an empty set forever and read as a catalogue nobody
      // has imported yet.
      LOG.warn("No contract family implements {} — the visible set can only ever be empty",
          OrganosWithVisibleContracts.class.getSimpleName());
    }
  }

  public List<OrganoDeContratacion> list() {
    List<OrganoDeContratacion> catalogue = organoRepository.findAllOrderByName();
    if (catalogue.isEmpty()) {
      return catalogue;
    }
    Set<OrganoId> visible = visibleAmong(catalogue.stream().map(ListVisibleOrganos::idOf).toList());
    return catalogue.stream()
        .filter(organo -> visible.contains(organo.id()))
        .toList();
  }

  /** A stored Órgano always carries one, and asking a family about a null would be nonsense. */
  private static OrganoId idOf(OrganoDeContratacion organo) {
    return Objects.requireNonNull(organo.id(), "a stored Órgano must carry an id");
  }

  private Set<OrganoId> visibleAmong(List<OrganoId> candidates) {
    Set<OrganoId> visible = new HashSet<>();
    for (OrganosWithVisibleContracts family : contractFamilies) {
      visible.addAll(family.among(candidates));
    }
    return visible;
  }
}
