package gal.conxugal.domain.organo;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Insert;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Port for reading and reconciling the Órgano catalogue. Implemented by the
 * {@code infrastructure} module. Reconciliation is always update-in-place: an existing
 * row is matched by {@code sourceKey} and written to via {@link #update} or
 * {@link #setActive}, never deleted and reinserted.
 */
public interface OrganoRepository {

  List<OrganoDeContratacion> findAll();

  List<OrganoDeContratacion> findAllBySourceKeyIn(Collection<String> sourceKeys);

  @Insert
  OrganoDeContratacion insert(OrganoDeContratacion organo);

  /** Refreshes an existing row's name, acronym and active state in place. */
  void update(@Id UUID id, String name, @Nullable String acronym, boolean active);

  /** Flips an existing row's active state, without touching name or acronym. */
  void setActive(@Id UUID id, boolean active);
}
