package gal.conxugal.infrastructure.jdbc.organo;

import gal.conxugal.domain.organo.OrganoDeContratacion;
import gal.conxugal.domain.organo.OrganoRepository;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import java.util.UUID;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface JdbcOrganoRepository
    extends OrganoRepository, GenericRepository<OrganoDeContratacion, UUID> {

  /**
   * {@code setActive}'s name doesn't fit Micronaut Data's {@code updateXxx} derivation
   * convention, so its update statement is spelled out explicitly rather than derived.
   */
  @Override
  @Query("UPDATE organo_contratacion SET active = :active WHERE id = :id")
  void setActive(UUID id, boolean active);
}
