package gal.conxugal.infrastructure.jdbc.organo;

import gal.conxugal.domain.organo.Termo;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Insert;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Micronaut Data's generated store for {@code termo}. It deliberately does not implement
 * {@link gal.conxugal.domain.organo.TermoRepository}: {@link TermoRepositoryAdapter} does,
 * so there is exactly one bean satisfying the port and one place that turns a constraint
 * violation into a domain refusal.
 *
 * <p>{@code findAllOrderByName} carries no explicit {@code COLLATE} — {@code termo.name} is
 * declared with the {@code galician} collation, so the derived {@code ORDER BY} inherits it.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
interface JdbcTermoRepository extends GenericRepository<Termo, UUID> {

  /**
   * The advisory-lock key: arbitrary, but fixed forever and owned solely by the taxonomy —
   * advisory locks share one namespace per database, so anything else picking this number
   * would silently serialise against the tree. It spells {@code taxonomi} in ASCII, which is
   * what makes a {@code pg_locks} row traceable back here.
   */
  long TAXONOMIA_LOCK_KEY = 0x7461786F6E6F6D69L;

  List<Termo> findAllOrderByName();

  Optional<Termo> findById(UUID id);

  @Insert
  Termo insert(Termo termo);

  void updateName(@Id UUID id, String name);

  void updateParentId(@Id UUID id, @Nullable UUID parentId);

  void deleteById(UUID id);

  boolean existsByParentId(UUID parentId);

  /**
   * A derived query would bind {@code parent_id = :parentId}, which matches nothing when the
   * argument is null, so the roots would come back empty rather than as the roots.
   */
  @Query("SELECT * FROM termo WHERE parent_id IS NOT DISTINCT FROM :parentId")
  List<Termo> findByParentId(@Nullable UUID parentId);

  /**
   * {@code pg_advisory_xact_lock} returns {@code void}, which has no column type to map onto
   * a return value; taking it as a {@code FROM} item makes this an ordinary single-row
   * {@code SELECT} whose result {@link TermoRepositoryAdapter} discards.
   */
  @Query("SELECT 1 FROM pg_advisory_xact_lock(" + TAXONOMIA_LOCK_KEY + ")")
  long acquireTaxonomiaLock();
}
