package gal.conxugal.domain.organo;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Insert;
import io.micronaut.data.annotation.Query;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Port for reading and reconciling the Órgano catalogue. Implemented by the
 * {@code infrastructure} module. Reconciliation is always update-in-place: an existing
 * row is matched by {@code sourceKey} and written to via {@link #update} or
 * {@link #updateActive}, never deleted and reinserted. Neither of those touches the
 * taxonomy placement — an import can neither move nor drop it.
 */
public interface OrganoRepository {

  List<OrganoDeContratacion> findAll();

  List<OrganoDeContratacion> findAllBySourceKeyIn(Collection<String> sourceKeys);

  Optional<OrganoDeContratacion> findById(UUID id);

  @Insert
  OrganoDeContratacion insert(OrganoDeContratacion organo);

  /** Refreshes an existing row's name and active state in place. */
  void update(@Id UUID id, String name, boolean active);

  /** Flips an existing row's active state, without touching name. */
  void updateActive(@Id UUID id, boolean active);

  /** Sets (non-null) or clears (null) the Órgano's single taxonomy placement. */
  void updateTermo(@Id UUID id, @Nullable UUID termoId);

  /**
   * Clears every Órgano's placement pointing at the given term. Explicit {@code @Query}
   * because matching and clearing the same column has no derived-method form.
   */
  @Query("UPDATE organo_contratacion SET termo_id = NULL WHERE termo_id = :termoId")
  void clearPlacementsByTermo(UUID termoId);
}
