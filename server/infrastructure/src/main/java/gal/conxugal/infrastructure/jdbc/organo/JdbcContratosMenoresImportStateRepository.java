package gal.conxugal.infrastructure.jdbc.organo;

import gal.conxugal.domain.organo.ContratosMenoresImportState;
import gal.conxugal.domain.organo.ContratosMenoresImportStateRepository;
import gal.conxugal.domain.organo.OrganoId;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface JdbcContratosMenoresImportStateRepository
    extends ContratosMenoresImportStateRepository,
        GenericRepository<ContratosMenoresImportState, OrganoId> {
}
