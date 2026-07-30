package gal.conxugal.infrastructure.jdbc.organo;

import gal.conxugal.domain.organo.DuplicateSiblingNameException;
import gal.conxugal.domain.organo.Termo;
import gal.conxugal.domain.organo.TermoNotFoundException;
import gal.conxugal.domain.organo.TermoRepository;
import io.micronaut.data.exceptions.DataAccessException;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The {@link TermoRepository} port, over Micronaut Data's generated {@link JdbcTermoRepository}.
 * Every read passes straight through; the writes that a database guard can refuse translate
 * that refusal into the domain's own exception, so a raced write reaches the caller as the
 * same rejection a checked one would rather than as a 500.
 */
// PreserveStackTrace: every arm below either chains `failure` as the cause or rethrows it
// unchanged, so the trace is preserved on all paths — PMD does not read a `throw switch`.
@SuppressWarnings("PMD.PreserveStackTrace")
@Singleton
class TermoRepositoryAdapter implements TermoRepository {

  private final JdbcTermoRepository termos;

  TermoRepositoryAdapter(JdbcTermoRepository termos) {
    this.termos = termos;
  }

  @Override
  public List<Termo> findAllOrderByName() {
    return termos.findAllOrderByName();
  }

  @Override
  public Optional<Termo> findById(UUID id) {
    return termos.findById(id);
  }

  @Override
  public Termo insert(Termo termo) {
    try {
      return termos.insert(termo);
    } catch (DataAccessException failure) {
      throw switch (TaxonomiaConstraints.refusedBy(failure)) {
        case SIBLING_NAME -> new DuplicateSiblingNameException(termo.name(), failure);
        case PARENT_REFERENCE -> termoNotFound(termo.parentId(), failure);
        case PLACEMENT_REFERENCE, NONE -> failure;
      };
    }
  }

  @Override
  public void updateName(UUID id, String name) {
    try {
      termos.updateName(id, name);
    } catch (DataAccessException failure) {
      throw switch (TaxonomiaConstraints.refusedBy(failure)) {
        case SIBLING_NAME -> new DuplicateSiblingNameException(name, failure);
        case PARENT_REFERENCE, PLACEMENT_REFERENCE, NONE -> failure;
      };
    }
  }

  @Override
  public void updateParentId(UUID id, @Nullable UUID parentId) {
    try {
      termos.updateParentId(id, parentId);
    } catch (DataAccessException failure) {
      throw switch (TaxonomiaConstraints.refusedBy(failure)) {
        // The move is addressed by ids alone, and the aborted transaction rules out reading
        // the name back, so this is the one refusal that cannot name what it collided with.
        case SIBLING_NAME -> new DuplicateSiblingNameException(null, failure);
        case PARENT_REFERENCE -> termoNotFound(parentId, failure);
        case PLACEMENT_REFERENCE, NONE -> failure;
      };
    }
  }

  @Override
  public void deleteById(UUID id) {
    try {
      termos.deleteById(id);
    } catch (DataAccessException failure) {
      throw switch (TaxonomiaConstraints.refusedBy(failure)) {
        // Both keys are declared without an ON DELETE action, so a term still referenced by a
        // child or by a placement is refused here rather than quietly taking rows with it.
        case PARENT_REFERENCE, PLACEMENT_REFERENCE -> new TermoNotFoundException(id, failure);
        case SIBLING_NAME, NONE -> failure;
      };
    }
  }

  @Override
  public boolean existsByParentId(UUID parentId) {
    return termos.existsByParentId(parentId);
  }

  @Override
  public List<Termo> findByParentId(@Nullable UUID parentId) {
    return termos.findByParentId(parentId);
  }

  @Override
  public void lockTaxonomia() {
    termos.acquireTaxonomiaLock();
  }

  // Only a non-null reference can violate a foreign key, so the id is always there in
  // practice. Should a schema change ever make that untrue, the original failure goes up
  // unchanged rather than being reported as a term that was never named.
  private static RuntimeException termoNotFound(
      @Nullable UUID termoId, DataAccessException failure) {
    return termoId == null ? failure : new TermoNotFoundException(termoId, failure);
  }
}
