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

  @Override
  @Query("UPDATE organo_contratacion SET termo_id = NULL WHERE termo_id = :termoId")
  void clearPlacementsByTermo(UUID termoId);
}
