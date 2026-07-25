package gal.conxugal.domain.organo;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Insert;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Port for reading and reconciling the Órgano catalogue. Implemented by the
 * {@code infrastructure} module. Reconciliation is always update-in-place: an existing
 * row is matched by {@code sourceKey} and written to via {@link #update} or
 * {@link #updateActive}, never deleted and reinserted.
 */
public interface OrganoRepository {

  List<OrganoDeContratacion> findAll();

  List<OrganoDeContratacion> findAllBySourceKeyIn(Collection<String> sourceKeys);

  @Insert
  OrganoDeContratacion insert(OrganoDeContratacion organo);

  /** Refreshes an existing row's name and active state in place. */
  void update(@Id UUID id, String name, boolean active);

  /** Flips an existing row's active state, without touching name. */
  void updateActive(@Id UUID id, boolean active);
}
