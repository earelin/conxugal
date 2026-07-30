package gal.conxugal.infrastructure.jdbc.organo;

import gal.conxugal.domain.organo.OrganoDeContratacion;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Insert;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Micronaut Data's generated store for {@code organo_contratacion}. It deliberately does not
 * implement {@link gal.conxugal.domain.organo.OrganoRepository}: {@link OrganoRepositoryAdapter}
 * does, so there is exactly one bean satisfying the port and one place that turns a constraint
 * violation into a domain refusal.
 *
 * <p>{@code findAllOrderByName} carries no explicit {@code COLLATE} — {@code name} is declared
 * with the {@code galician} collation, so the derived {@code ORDER BY} inherits it.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
interface JdbcOrganoRepository extends GenericRepository<OrganoDeContratacion, UUID> {

  List<OrganoDeContratacion> findAllOrderByName();

  List<OrganoDeContratacion> findAllBySourceKeyIn(Collection<String> sourceKeys);

  Optional<OrganoDeContratacion> findById(UUID id);

  @Insert
  OrganoDeContratacion insert(OrganoDeContratacion organo);

  void update(@Id UUID id, String name, boolean active);

  void updateActive(@Id UUID id, boolean active);

  void updateTermo(@Id UUID id, @Nullable UUID termoId);

  /** Matching and clearing the same column has no derived form. */
  @Query("UPDATE organo_contratacion SET termo_id = NULL WHERE termo_id = :termoId")
  void clearPlacementsByTermo(UUID termoId);
}
