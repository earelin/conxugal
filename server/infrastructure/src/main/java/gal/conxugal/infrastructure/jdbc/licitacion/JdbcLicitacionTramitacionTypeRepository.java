package gal.conxugal.infrastructure.jdbc.licitacion;

import gal.conxugal.domain.licitacion.LicitacionTramitacionType;
import gal.conxugal.domain.licitacion.LicitacionTramitacionTypeId;
import gal.conxugal.domain.licitacion.LicitacionTramitacionTypeRepository;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.transaction.annotation.Transactional;
import java.util.Objects;

/**
 * Stores the published tramitación-type vocabulary, on
 * {@link JdbcLicitacionContractTypeRepository}'s shape and for its reasons.
 *
 * @see JdbcLicitacionContractTypeRepository
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public abstract class JdbcLicitacionTramitacionTypeRepository
    implements LicitacionTramitacionTypeRepository,
        GenericRepository<LicitacionTramitacionType, LicitacionTramitacionTypeId> {

  private static final String UPSERT =
      """
      INSERT INTO licitacion_tramitacion_type (name) VALUES (?::text)
      ON CONFLICT ON CONSTRAINT licitacion_tramitacion_type_name_key
          DO UPDATE SET name = EXCLUDED.name
      RETURNING id
      """;

  private final JdbcOperations jdbcOperations;

  protected JdbcLicitacionTramitacionTypeRepository(JdbcOperations jdbcOperations) {
    this.jdbcOperations = jdbcOperations;
  }

  @Override
  @Transactional
  public LicitacionTramitacionType upsert(LicitacionTramitacionType type) {
    Objects.requireNonNull(type, "type must not be null");
    return new LicitacionTramitacionType(
        new LicitacionTramitacionTypeId(
            Upserts.returningId(
                jdbcOperations, UPSERT, statement -> statement.setString(1, type.name()))),
        type.name());
  }
}
