package gal.conxugal.infrastructure.jdbc.organo;

import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.OrganoRepository;
import gal.conxugal.domain.organo.TermoNotFoundException;
import io.micronaut.data.exceptions.DataAccessException;
import jakarta.inject.Singleton;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The {@link OrganoRepository} port, over Micronaut Data's generated
 * {@link JdbcOrganoRepository}. Only the placement write can be refused by a database guard:
 * an assign naming a term that a concurrent delete has already removed.
 */
// PreserveStackTrace: every arm below either chains `failure` as the cause or rethrows it
// unchanged, so the trace is preserved on all paths — PMD does not read a `throw switch`.
@SuppressWarnings("PMD.PreserveStackTrace")
@Singleton
class OrganoRepositoryAdapter implements OrganoRepository {

  private final JdbcOrganoRepository organos;

  OrganoRepositoryAdapter(JdbcOrganoRepository organos) {
    this.organos = organos;
  }

  @Override
  public List<OrganoDeContratacion> findAllOrderByName() {
    return organos.findAllOrderByName();
  }

  @Override
  public List<OrganoDeContratacion> findAllBySourceKeyIn(Collection<String> sourceKeys) {
    return organos.findAllBySourceKeyIn(sourceKeys);
  }

  @Override
  public Optional<OrganoDeContratacion> findById(UUID id) {
    return organos.findById(id);
  }

  @Override
  public OrganoDeContratacion insert(OrganoDeContratacion organo) {
    return organos.insert(organo);
  }

  @Override
  public void update(UUID id, String name, boolean active) {
    organos.update(id, name, active);
  }

  @Override
  public void updateActive(UUID id, boolean active) {
    organos.updateActive(id, active);
  }

  @Override
  public void updateTermo(UUID id, @Nullable UUID termoId) {
    try {
      organos.updateTermo(id, termoId);
    } catch (DataAccessException failure) {
      throw switch (TaxonomiaConstraints.refusedBy(failure)) {
        case PLACEMENT_REFERENCE -> termoNotFound(termoId, failure);
        case SIBLING_NAME, PARENT_REFERENCE, NONE -> failure;
      };
    }
  }

  @Override
  public void clearPlacementsByTermo(UUID termoId) {
    organos.clearPlacementsByTermo(termoId);
  }

  // Clearing a placement writes null and cannot violate the key, so the id is always there in
  // practice. Should that ever stop holding, the original failure goes up unchanged rather
  // than being reported as a term that was never named.
  private static RuntimeException termoNotFound(
      @Nullable UUID termoId, DataAccessException failure) {
    return termoId == null ? failure : new TermoNotFoundException(termoId, failure);
  }
}
