package gal.conxugal.infrastructure.jdbc.licitacion;

import gal.conxugal.domain.licitacion.LicitacionProcedureType;
import gal.conxugal.domain.licitacion.LicitacionProcedureTypeId;
import gal.conxugal.domain.licitacion.LicitacionProcedureTypeRepository;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.transaction.annotation.Transactional;
import java.util.Objects;

/**
 * Stores the published procedure-type vocabulary, on
 * {@link JdbcLicitacionContractTypeRepository}'s shape and for its reasons.
 *
 * @see JdbcLicitacionContractTypeRepository
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public abstract class JdbcLicitacionProcedureTypeRepository
    implements LicitacionProcedureTypeRepository,
        GenericRepository<LicitacionProcedureType, LicitacionProcedureTypeId> {

  private static final String UPSERT =
      """
      INSERT INTO licitacion_procedure_type (name) VALUES (?::text)
      ON CONFLICT ON CONSTRAINT licitacion_procedure_type_name_key
          DO UPDATE SET name = EXCLUDED.name
      RETURNING id
      """;

  private final JdbcOperations jdbcOperations;

  protected JdbcLicitacionProcedureTypeRepository(JdbcOperations jdbcOperations) {
    this.jdbcOperations = jdbcOperations;
  }

  @Override
  @Transactional
  public LicitacionProcedureType upsert(LicitacionProcedureType type) {
    Objects.requireNonNull(type, "type must not be null");
    return new LicitacionProcedureType(
        new LicitacionProcedureTypeId(
            Upserts.returningId(
                jdbcOperations, UPSERT, statement -> statement.setString(1, type.name()))),
        type.name());
  }
}
