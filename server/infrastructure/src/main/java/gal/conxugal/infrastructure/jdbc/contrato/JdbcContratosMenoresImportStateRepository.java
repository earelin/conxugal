package gal.conxugal.infrastructure.jdbc.contrato;

import gal.conxugal.domain.contrato.ContratosMenoresImportState;
import gal.conxugal.domain.contrato.ContratosMenoresImportStateRepository;
import gal.conxugal.domain.organo.OrganoId;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface JdbcContratosMenoresImportStateRepository
    extends ContratosMenoresImportStateRepository,
        GenericRepository<ContratosMenoresImportState, OrganoId> {
}
