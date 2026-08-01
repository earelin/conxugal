package gal.conxugal.domain.organo;

import gal.conxugal.domain.organo.taxonomia.TermoId;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Insert;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Port for reading and reconciling the Órgano catalogue. Implemented by the
 * {@code infrastructure} module. Reconciliation is always update-in-place: an existing
 * row is matched by {@code sourceKey} and written to via {@link #update} or
 * {@link #updateActive}, never deleted and reinserted. Neither of those touches the
 * taxonomy placement — an import can neither move nor drop it.
 */
public interface OrganoRepository {

  /**
   * Every Órgano, ordered by name. The order is part of this port's own contract, not the
   * adapter's private choice: the catalogue read endpoint serves this list verbatim.
   */
  List<OrganoDeContratacion> findAllOrderByName();

  List<OrganoDeContratacion> findAllBySourceKeyIn(Collection<String> sourceKeys);

  Optional<OrganoDeContratacion> findById(OrganoId id);

  @Insert
  OrganoDeContratacion insert(OrganoDeContratacion organo);

  /** Refreshes an existing row's name and active state in place. */
  void update(@Id OrganoId id, String name, boolean active);

  /** Flips an existing row's active state, without touching name. */
  void updateActive(@Id OrganoId id, boolean active);

  /** Sets (non-null) or clears (null) the Órgano's single taxonomy placement. */
  void updateTermo(@Id OrganoId id, @Nullable TermoId termoId);
}
