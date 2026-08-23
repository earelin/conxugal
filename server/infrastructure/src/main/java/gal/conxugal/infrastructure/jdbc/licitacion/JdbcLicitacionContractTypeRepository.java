package gal.conxugal.infrastructure.jdbc.licitacion;

import gal.conxugal.domain.licitacion.LicitacionContractType;
import gal.conxugal.domain.licitacion.LicitacionContractTypeId;
import gal.conxugal.domain.licitacion.LicitacionContractTypeRepository;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.transaction.annotation.Transactional;
import java.util.Objects;

/**
 * Stores the published contract-type vocabulary. One statement, matching on the published name,
 * which is this vocabulary's only key: the record publishes no code for a type.
 *
 * <p>The conflict branch writes the name back over itself. There is nothing else on the row to
 * refresh, and it is a {@code DO UPDATE} rather than a {@code DO NOTHING} because the port answers
 * the stored type even when the source has published it a thousand times before — and
 * {@code DO NOTHING} returns no row to answer with.
 *
 * <p>Like the state's, this runs in the caller's transaction, so a name nobody has published before
 * creates its row beside the procedure that names it.
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public abstract class JdbcLicitacionContractTypeRepository
    implements LicitacionContractTypeRepository,
        GenericRepository<LicitacionContractType, LicitacionContractTypeId> {

  private static final String UPSERT =
      """
      INSERT INTO licitacion_contract_type (name) VALUES (?::text)
      ON CONFLICT ON CONSTRAINT licitacion_contract_type_name_key
          DO UPDATE SET name = EXCLUDED.name
      RETURNING id
      """;

  private final JdbcOperations jdbcOperations;

  protected JdbcLicitacionContractTypeRepository(JdbcOperations jdbcOperations) {
    this.jdbcOperations = jdbcOperations;
  }

  @Override
  @Transactional
  public LicitacionContractType upsert(LicitacionContractType type) {
    Objects.requireNonNull(type, "type must not be null");
    return new LicitacionContractType(
        new LicitacionContractTypeId(
            Upserts.returningId(
                jdbcOperations, UPSERT, statement -> statement.setString(1, type.name()))),
        type.name());
  }
}
